import http from 'k6/http';
import exec from 'k6/execution';
import {check} from 'k6';
import {Rate, Trend} from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const PROFILE = __ENV.AUTHENTICATED_LEARNING_PROFILE;
const RUN_ID = __ENV.AUTHENTICATED_LEARNING_RUN_ID;
const PASSWORD = __ENV.AUTHENTICATED_LEARNING_PASSWORD;
const COURSE_SLUG = 'authenticated-learning-performance';
const IDENTITY_COUNT = 40;
const DURATION_SECONDS = 30;
const LESSON_DURATION_SECONDS = 1800;
const LESSON_IDS = [1, 2, 3, 4].map(number =>
    `31000000-0000-0000-0000-${String(number).padStart(12, '0')}`,
);
const PROFILE_CONFIG = {
    read: {rate: 20, minimumIterations: 590, p95Ms: 750, p99Ms: 1500},
    write: {rate: 10, minimumIterations: 295, p95Ms: 1000, p99Ms: 2000},
};

if (!BASE_URL || !BASE_URL.startsWith('http://')) {
    throw new Error('BASE_URL must identify the isolated HTTP application container');
}
if (!Object.hasOwn(PROFILE_CONFIG, PROFILE)) {
    throw new Error('AUTHENTICATED_LEARNING_PROFILE must be read or write');
}
if (!RUN_ID || RUN_ID.length > 48 || !/^[a-z0-9-]+$/.test(RUN_ID)) {
    throw new Error('AUTHENTICATED_LEARNING_RUN_ID must be at most 48 lowercase letters, digits, or hyphens');
}
if (!PASSWORD || PASSWORD.length < 8 || PASSWORD.length > 72) {
    throw new Error('AUTHENTICATED_LEARNING_PASSWORD must satisfy the registration contract');
}

const config = PROFILE_CONFIG[PROFILE];
const workloadDuration = new Trend('authenticated_learning_request_duration', true);
const workloadFailures = new Rate('authenticated_learning_request_failures');

export const options = {
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        authenticated_learning: {
            executor: 'constant-arrival-rate',
            rate: config.rate,
            timeUnit: '1s',
            duration: `${DURATION_SECONDS}s`,
            preAllocatedVUs: PROFILE === 'read' ? 20 : 10,
            maxVUs: IDENTITY_COUNT,
            gracefulStop: '5s',
            tags: {workload: `authenticated-learning-${PROFILE}`},
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        authenticated_learning_request_failures: ['rate==0'],
        authenticated_learning_request_duration: [
            `p(95)<${config.p95Ms}`,
            `p(99)<${config.p99Ms}`,
        ],
        dropped_iterations: ['count==0'],
        iterations: [`count>=${config.minimumIterations}`],
    },
};

function parseJson(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

function bearerHeaders(token, includeContentType = false) {
    const headers = {Authorization: `Bearer ${token}`};
    if (includeContentType) {
        headers['Content-Type'] = 'application/json';
    }
    return headers;
}

export function setup() {
    const tokens = [];

    for (let index = 1; index <= IDENTITY_COUNT; index += 1) {
        const identity = String(index).padStart(2, '0');
        const email = `learning-${RUN_ID}-${identity}@example.invalid`;
        const registration = http.post(
            `${BASE_URL}/api/v1/auth/register`,
            JSON.stringify({
                email,
                password: PASSWORD,
                displayName: `Learning ${PROFILE} ${identity}`,
            }),
            {
                headers: {'Content-Type': 'application/json'},
                tags: {phase: 'setup', endpoint: 'auth-register'},
                responseCallback: http.expectedStatuses(201),
            },
        );
        const registrationBody = parseJson(registration);
        const token = registrationBody?.accessToken;
        const registered = check(registration, {
            'setup registration is HTTP 201': response => response.status === 201,
            'setup registration issued a JWT': () =>
                typeof token === 'string'
                && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(token),
            'setup registration assigned STUDENT': () =>
                registrationBody?.user?.roles?.includes('STUDENT') === true,
        });
        if (!registered) {
            throw new Error(`registration setup failed for identity ${identity} with HTTP ${registration.status}`);
        }

        const enrollment = http.post(
            `${BASE_URL}/api/v1/courses/${COURSE_SLUG}/enrollments`,
            null,
            {
                headers: bearerHeaders(token),
                tags: {phase: 'setup', endpoint: 'course-enrollment'},
                responseCallback: http.expectedStatuses(200),
            },
        );
        const enrollmentBody = parseJson(enrollment);
        const enrolled = check(enrollment, {
            'setup enrollment is HTTP 200': response => response.status === 200,
            'setup enrollment is active': () => enrollmentBody?.status === 'ACTIVE',
            'setup enrollment targets performance course': () =>
                enrollmentBody?.course?.slug === COURSE_SLUG,
        });
        if (!enrolled) {
            throw new Error(`enrollment setup failed for identity ${identity} with HTTP ${enrollment.status}`);
        }
        tokens.push(token);
    }

    return {tokens};
}

function recordWorkload(response, assertions) {
    workloadDuration.add(response.timings.duration, {profile: PROFILE});
    const valid = check(response, assertions);
    workloadFailures.add(!valid, {profile: PROFILE});
}

function authenticatedRead(token, lessonId) {
    const progressRead = exec.scenario.iterationInTest % 10 < 7;
    const path = progressRead
        ? `/api/v1/me/courses/${COURSE_SLUG}/lessons/${lessonId}/progress`
        : `/api/v1/me/courses/${COURSE_SLUG}/lessons/${lessonId}`;
    const response = http.get(`${BASE_URL}${path}`, {
        headers: bearerHeaders(token),
        tags: {phase: 'workload', endpoint: progressRead ? 'progress-read' : 'lesson-player'},
        responseCallback: http.expectedStatuses(200),
    });
    const body = parseJson(response);

    if (progressRead) {
        recordWorkload(response, {
            'progress read is HTTP 200': current => current.status === 200,
            'progress read returns requested lesson': () => body?.lessonId === lessonId,
            'progress read starts incomplete': () =>
                body?.positionSeconds === 0 && body?.completed === false && body?.updatedAt === null,
        });
        return;
    }

    recordWorkload(response, {
        'lesson player is HTTP 200': current => current.status === 200,
        'lesson player returns protected course': () =>
            body?.courseSlug === COURSE_SLUG && body?.lessonId === lessonId,
        'lesson player returns expected duration': () =>
            body?.durationSeconds === LESSON_DURATION_SECONDS && body?.preview === false,
    });
}

function authenticatedWrite(token, lessonId) {
    const positionSeconds = 1 + (exec.scenario.iterationInTest % (LESSON_DURATION_SECONDS - 1));
    const response = http.put(
        `${BASE_URL}/api/v1/me/courses/${COURSE_SLUG}/lessons/${lessonId}/progress`,
        JSON.stringify({positionSeconds, completed: false}),
        {
            headers: bearerHeaders(token, true),
            tags: {phase: 'workload', endpoint: 'progress-write'},
            responseCallback: http.expectedStatuses(200),
        },
    );
    const body = parseJson(response);
    recordWorkload(response, {
        'progress write is HTTP 200': current => current.status === 200,
        'progress write returns requested lesson': () => body?.lessonId === lessonId,
        'progress write persists requested state': () =>
            body?.positionSeconds === positionSeconds
            && body?.completed === false
            && typeof body?.updatedAt === 'string',
    });
}

export default function (data) {
    const tokenIndex = (exec.vu.idInTest - 1) % data.tokens.length;
    const lessonIndex = (exec.vu.idInTest - 1) % LESSON_IDS.length;
    const token = data.tokens[tokenIndex];
    const lessonId = LESSON_IDS[lessonIndex];

    if (PROFILE === 'read') {
        authenticatedRead(token, lessonId);
    } else {
        authenticatedWrite(token, lessonId);
    }
}

function values(data, metricName) {
    return data.metrics[metricName]?.values ?? {};
}

export function handleSummary(data) {
    const summary = {
        format: 'ai-learning-authenticated-learning-v1',
        generatedAtUtc: new Date().toISOString(),
        workload: {
            profile: PROFILE,
            identities: IDENTITY_COUNT,
            ratePerSecond: config.rate,
            durationSeconds: DURATION_SECONDS,
            courseSlug: COURSE_SLUG,
            lessons: LESSON_IDS.length,
        },
        results: {
            checksRate: values(data, 'checks').rate,
            iterations: values(data, 'iterations').count,
            droppedIterations: values(data, 'dropped_iterations').count ?? 0,
            requestFailureRate: values(data, 'authenticated_learning_request_failures').rate,
            requestDurationP95Ms: values(data, 'authenticated_learning_request_duration')['p(95)'],
            requestDurationP99Ms: values(data, 'authenticated_learning_request_duration')['p(99)'],
        },
    };

    return {
        stdout: `${JSON.stringify(summary)}\n`,
        [`/results/authenticated-learning-${PROFILE}-summary.json`]: `${JSON.stringify(summary, null, 2)}\n`,
    };
}
