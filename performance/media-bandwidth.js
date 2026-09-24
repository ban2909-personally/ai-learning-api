import crypto from 'k6/crypto';
import http from 'k6/http';
import exec from 'k6/execution';
import {check} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const RUN_ID = __ENV.MEDIA_BANDWIDTH_RUN_ID;
const PASSWORD = __ENV.MEDIA_BANDWIDTH_PASSWORD;
const MEDIA_ETAG = __ENV.MEDIA_ETAG;
const RANGE_SHA256 = __ENV.MEDIA_RANGE_SHA256;
const COURSE_SLUG = 'media-bandwidth-performance';
const LESSON_ID = '34000000-0000-0000-0000-000000000001';
const IDENTITY_COUNT = 40;
const OBJECT_SIZE_BYTES = 32 * 1024 * 1024;
const RANGE_SIZE_BYTES = 1024 * 1024;
const RANGE_COUNT = OBJECT_SIZE_BYTES / RANGE_SIZE_BYTES;
const RATE_PER_SECOND = 8;
const DURATION_SECONDS = 30;
const MINIMUM_ITERATIONS = 235;

if (!BASE_URL || !BASE_URL.startsWith('http://')) {
    throw new Error('BASE_URL must identify the isolated HTTP application container');
}
if (!RUN_ID || RUN_ID.length > 48 || !/^[a-z0-9-]+$/.test(RUN_ID)) {
    throw new Error('MEDIA_BANDWIDTH_RUN_ID must be at most 48 lowercase letters, digits, or hyphens');
}
if (!PASSWORD || PASSWORD.length < 8 || PASSWORD.length > 72) {
    throw new Error('MEDIA_BANDWIDTH_PASSWORD must satisfy the registration contract');
}
if (!MEDIA_ETAG || !/^[0-9a-f]{32}(?:-[0-9]+)?$/.test(MEDIA_ETAG)) {
    throw new Error('MEDIA_ETAG must be the validated MinIO object ETag');
}
if (!RANGE_SHA256 || !/^[0-9a-f]{64}$/.test(RANGE_SHA256)) {
    throw new Error('MEDIA_RANGE_SHA256 must be a lowercase SHA-256 digest');
}

const workloadDuration = new Trend('media_bandwidth_request_duration', true);
const workloadFailures = new Rate('media_bandwidth_request_failures');
const completedResponses = new Counter('media_bandwidth_completed_responses');
const transferredBytes = new Counter('media_bandwidth_transferred_bytes');

export const options = {
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        media_bandwidth: {
            executor: 'constant-arrival-rate',
            rate: RATE_PER_SECOND,
            timeUnit: '1s',
            duration: `${DURATION_SECONDS}s`,
            preAllocatedVUs: 16,
            maxVUs: IDENTITY_COUNT,
            gracefulStop: '10s',
            tags: {workload: 'media-bandwidth'},
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        media_bandwidth_request_failures: ['rate==0'],
        media_bandwidth_request_duration: ['p(95)<2000', 'p(99)<4000'],
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

function bearerHeaders(token) {
    return {Authorization: `Bearer ${token}`};
}

export function setup() {
    const mediaTokens = [];

    for (let index = 1; index <= IDENTITY_COUNT; index += 1) {
        const identity = String(index).padStart(2, '0');
        const email = `media-${RUN_ID}-${identity}@example.invalid`;
        const registration = http.post(
            `${BASE_URL}/api/v1/auth/register`,
            JSON.stringify({
                email,
                password: PASSWORD,
                displayName: `Media Performance ${identity}`,
            }),
            {
                headers: {'Content-Type': 'application/json'},
                tags: {phase: 'setup', endpoint: 'auth-register'},
                responseCallback: http.expectedStatuses(201),
            },
        );
        const registrationBody = parseJson(registration);
        const token = registrationBody?.accessToken;
        const mediaCookie = registration.cookies?.media_access?.[0]?.value;
        const registered = check(registration, {
            'setup registration is HTTP 201': response => response.status === 201,
            'setup registration issued a JWT': () =>
                typeof token === 'string'
                && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(token),
            'setup registration issued matching media cookie': () => mediaCookie === token,
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
            'setup enrollment targets media performance course': () =>
                enrollmentBody?.course?.slug === COURSE_SLUG,
        });
        if (!enrolled) {
            throw new Error(`enrollment setup failed for identity ${identity} with HTTP ${enrollment.status}`);
        }
        mediaTokens.push(token);
    }

    return {mediaTokens};
}

export default function (data) {
    const identityIndex = (exec.vu.idInTest - 1) % data.mediaTokens.length;
    const rangeIndex = exec.scenario.iterationInTest % RANGE_COUNT;
    const start = rangeIndex * RANGE_SIZE_BYTES;
    const end = start + RANGE_SIZE_BYTES - 1;
    const token = data.mediaTokens[identityIndex];
    const jar = http.cookieJar();
    jar.set(BASE_URL, 'media_access', token, {path: '/api/v1/media'});

    const response = http.get(
        `${BASE_URL}/api/v1/media/courses/${COURSE_SLUG}/lessons/${LESSON_ID}`,
        {
            headers: {Range: `bytes=${start}-${end}`},
            responseType: 'binary',
            tags: {phase: 'workload', endpoint: 'lesson-media-range'},
            responseCallback: http.expectedStatuses(206),
        },
    );
    const bodyLength = response.body?.byteLength ?? 0;
    const bodySha256 = bodyLength === RANGE_SIZE_BYTES
        ? crypto.sha256(response.body, 'hex')
        : '';
    const valid = check(response, {
        'media range is HTTP 206': current => current.status === 206,
        'media range advertises byte ranges': current => current.headers['Accept-Ranges'] === 'bytes',
        'media range has expected content type': current => current.headers['Content-Type'] === 'video/mp4',
        'media range has expected ETag': current => current.headers.Etag === `"${MEDIA_ETAG}"`,
        'media range has expected content length': current =>
            current.headers['Content-Length'] === String(RANGE_SIZE_BYTES),
        'media range has expected content range': current =>
            current.headers['Content-Range'] === `bytes ${start}-${end}/${OBJECT_SIZE_BYTES}`,
        'media range has exact body length': () => bodyLength === RANGE_SIZE_BYTES,
        'media range has exact body digest': () => bodySha256 === RANGE_SHA256,
    });

    workloadDuration.add(response.timings.duration);
    workloadFailures.add(!valid);
    if (valid) {
        completedResponses.add(1);
        transferredBytes.add(bodyLength);
    }
}

function values(data, metricName) {
    return data.metrics[metricName]?.values ?? {};
}

export function handleSummary(data) {
    const summary = {
        format: 'ai-learning-media-bandwidth-v1',
        generatedAtUtc: new Date().toISOString(),
        workload: {
            identities: IDENTITY_COUNT,
            ratePerSecond: RATE_PER_SECOND,
            durationSeconds: DURATION_SECONDS,
            minimumIterations: MINIMUM_ITERATIONS,
            courseSlug: COURSE_SLUG,
            lessonId: LESSON_ID,
            objectSizeBytes: OBJECT_SIZE_BYTES,
            rangeSizeBytes: RANGE_SIZE_BYTES,
        },
        results: {
            checksRate: values(data, 'checks').rate,
            iterations: values(data, 'iterations').count,
            completedResponses: values(data, 'media_bandwidth_completed_responses').count,
            transferredBytes: values(data, 'media_bandwidth_transferred_bytes').count,
            droppedIterations: values(data, 'dropped_iterations').count ?? 0,
            requestFailureRate: values(data, 'media_bandwidth_request_failures').rate,
            requestDurationP95Ms: values(data, 'media_bandwidth_request_duration')['p(95)'],
            requestDurationP99Ms: values(data, 'media_bandwidth_request_duration')['p(99)'],
        },
    };

    return {
        stdout: `${JSON.stringify(summary)}\n`,
        '/results/media-bandwidth-client-summary.json': `${JSON.stringify(summary, null, 2)}\n`,
    };
}
