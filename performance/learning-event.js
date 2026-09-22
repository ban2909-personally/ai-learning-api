import http from 'k6/http';
import exec from 'k6/execution';
import {check} from 'k6';
import {Rate, Trend} from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const RUN_ID = __ENV.LEARNING_EVENT_RUN_ID;
const PASSWORD = __ENV.LEARNING_EVENT_PASSWORD;
const COURSE_SLUG = 'learning-event-performance';
const IDENTITY_COUNT = 40;
const DURATION_SECONDS = 30;
const RATE_PER_SECOND = 8;
const MINIMUM_ITERATIONS = 235;
const LESSON_DURATION_SECONDS = 1800;
const LESSON_IDS = Array.from(
    {length: 8},
    (_, index) => '32000000-0000-0000-0000-' + String(index + 1).padStart(12, '0'),
);
const UNIQUE_COMPLETION_CAPACITY = IDENTITY_COUNT * LESSON_IDS.length;

if (!BASE_URL || !BASE_URL.startsWith('http://')) {
    throw new Error('BASE_URL must identify the isolated HTTP application container');
}
if (!RUN_ID || RUN_ID.length > 48 || !/^[a-z0-9-]+$/.test(RUN_ID)) {
    throw new Error('LEARNING_EVENT_RUN_ID must be at most 48 lowercase letters, digits, or hyphens');
}
if (!PASSWORD || PASSWORD.length < 8 || PASSWORD.length > 72) {
    throw new Error('LEARNING_EVENT_PASSWORD must satisfy the registration contract');
}

const completionDuration = new Trend('learning_event_completion_request_duration', true);
const completionFailures = new Rate('learning_event_completion_request_failures');

export const options = {
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        learning_event_completion: {
            executor: 'constant-arrival-rate',
            rate: RATE_PER_SECOND,
            timeUnit: '1s',
            duration: `${DURATION_SECONDS}s`,
            preAllocatedVUs: 8,
            maxVUs: IDENTITY_COUNT,
            gracefulStop: '5s',
            tags: {workload: 'learning-event-completion'},
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        learning_event_completion_request_failures: ['rate==0'],
        learning_event_completion_request_duration: ['p(95)<1000', 'p(99)<2000'],
        dropped_iterations: ['count==0'],
        iterations: [`count>=${MINIMUM_ITERATIONS}`],
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
        const email = `event-${RUN_ID}-${identity}@example.invalid`;
        const registration = http.post(
            `${BASE_URL}/api/v1/auth/register`,
            JSON.stringify({
                email,
                password: PASSWORD,
                displayName: `Event Student ${identity}`,
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
            'setup enrollment targets event course': () =>
                enrollmentBody?.course?.slug === COURSE_SLUG,
        });
        if (!enrolled) {
            throw new Error(`enrollment setup failed for identity ${identity} with HTTP ${enrollment.status}`);
        }
        tokens.push(token);
    }

    return {tokens};
}

export default function (data) {
    const completionIndex = exec.scenario.iterationInTest;
    if (completionIndex >= UNIQUE_COMPLETION_CAPACITY) {
        throw new Error(`completion index ${completionIndex} exceeds the unique fixture capacity`);
    }

    const tokenIndex = completionIndex % IDENTITY_COUNT;
    const lessonIndex = Math.floor(completionIndex / IDENTITY_COUNT);
    const lessonId = LESSON_IDS[lessonIndex];
    const response = http.put(
        `${BASE_URL}/api/v1/me/courses/${COURSE_SLUG}/lessons/${lessonId}/progress`,
        JSON.stringify({positionSeconds: LESSON_DURATION_SECONDS, completed: true}),
        {
            headers: bearerHeaders(data.tokens[tokenIndex], true),
            tags: {phase: 'workload', endpoint: 'lesson-completion'},
            responseCallback: http.expectedStatuses(200),
        },
    );
    const body = parseJson(response);
    completionDuration.add(response.timings.duration);
    const valid = check(response, {
        'completion write is HTTP 200': current => current.status === 200,
        'completion returns requested lesson': () => body?.lessonId === lessonId,
        'completion persists terminal state': () =>
            body?.positionSeconds === LESSON_DURATION_SECONDS
            && body?.completed === true
            && typeof body?.updatedAt === 'string',
    });
    completionFailures.add(!valid);
}

function values(data, metricName) {
    return data.metrics[metricName]?.values ?? {};
}

export function handleSummary(data) {
    const summary = {
        format: 'ai-learning-event-throughput-v1',
        generatedAtUtc: new Date().toISOString(),
        workload: {
            identities: IDENTITY_COUNT,
            ratePerSecond: RATE_PER_SECOND,
            durationSeconds: DURATION_SECONDS,
            minimumIterations: MINIMUM_ITERATIONS,
            uniqueCompletionCapacity: UNIQUE_COMPLETION_CAPACITY,
            courseSlug: COURSE_SLUG,
            lessons: LESSON_IDS.length,
        },
        results: {
            checksRate: values(data, 'checks').rate,
            iterations: values(data, 'iterations').count,
            droppedIterations: values(data, 'dropped_iterations').count ?? 0,
            requestFailureRate: values(data, 'learning_event_completion_request_failures').rate,
            requestDurationP95Ms: values(data, 'learning_event_completion_request_duration')['p(95)'],
            requestDurationP99Ms: values(data, 'learning_event_completion_request_duration')['p(99)'],
        },
    };

    return {
        stdout: `${JSON.stringify(summary)}\n`,
        '/results/learning-event-http-summary.json': `${JSON.stringify(summary, null, 2)}\n`,
    };
}
