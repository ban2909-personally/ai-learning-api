import http from 'k6/http';
import exec from 'k6/execution';
import {check} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const RUN_ID = __ENV.AI_MENTOR_RUN_ID;
const PASSWORD = __ENV.AI_MENTOR_PASSWORD;
const COURSE_SLUG = 'ai-mentor-performance';
const LESSON_ID = '32000000-0000-0000-0000-000000000001';
const IDENTITY_COUNT = 40;
const RATE_PER_SECOND = 8;
const DURATION_SECONDS = 30;
const MINIMUM_ITERATIONS = 235;

if (!BASE_URL || !BASE_URL.startsWith('http://')) {
    throw new Error('BASE_URL must identify the isolated HTTP application container');
}
if (!RUN_ID || RUN_ID.length > 48 || !/^[a-z0-9-]+$/.test(RUN_ID)) {
    throw new Error('AI_MENTOR_RUN_ID must be at most 48 lowercase letters, digits, or hyphens');
}
if (!PASSWORD || PASSWORD.length < 8 || PASSWORD.length > 72) {
    throw new Error('AI_MENTOR_PASSWORD must satisfy the registration contract');
}

const turnDuration = new Trend('ai_mentor_turn_duration', true);
const turnFailures = new Rate('ai_mentor_turn_failures');
const turnCompletions = new Counter('ai_mentor_turn_completions');

export const options = {
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        mentor_turns: {
            executor: 'constant-arrival-rate',
            rate: RATE_PER_SECOND,
            timeUnit: '1s',
            duration: `${DURATION_SECONDS}s`,
            preAllocatedVUs: 16,
            maxVUs: 32,
            gracefulStop: '5s',
            tags: {workload: 'ai-mentor'},
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        ai_mentor_turn_failures: ['rate==0'],
        ai_mentor_turn_completions: [`count>=${MINIMUM_ITERATIONS}`],
        ai_mentor_turn_duration: ['p(95)<2000', 'p(99)<4000'],
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
        headers.Accept = 'text/event-stream';
    }
    return headers;
}

export function setup() {
    const tokens = [];
    for (let index = 1; index <= IDENTITY_COUNT; index += 1) {
        const identity = String(index).padStart(2, '0');
        const registration = http.post(
            `${BASE_URL}/api/v1/auth/register`,
            JSON.stringify({
                email: `mentor-${RUN_ID}-${identity}@example.invalid`,
                password: PASSWORD,
                displayName: `Mentor Performance ${identity}`,
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
            throw new Error(`registration setup failed for identity ${identity}`);
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
            'setup enrollment targets mentor course': () =>
                enrollmentBody?.course?.slug === COURSE_SLUG,
        });
        if (!enrolled) {
            throw new Error(`enrollment setup failed for identity ${identity}`);
        }
        tokens.push(token);
    }
    return {tokens};
}

function eventCount(body, eventName) {
    return (body.match(new RegExp(`event:${eventName}(?:\\r?\\n)`, 'g')) || []).length;
}

export default function (data) {
    const token = data.tokens[exec.scenario.iterationInTest % data.tokens.length];
    const questionNumber = exec.scenario.iterationInTest + 1;
    const response = http.post(
        `${BASE_URL}/api/v1/me/courses/${COURSE_SLUG}/lessons/${LESSON_ID}/mentor/messages`,
        JSON.stringify({question: `Explain dependency inversion with example ${questionNumber}.`}),
        {
            headers: bearerHeaders(token, true),
            tags: {phase: 'workload', endpoint: 'mentor-ask'},
            responseCallback: http.expectedStatuses(200),
            timeout: '5s',
        },
    );
    const body = response.body || '';
    const valid = check(response, {
        'mentor response is HTTP 200': current => current.status === 200,
        'mentor response is SSE': current =>
            String(current.headers['Content-Type'] || '').includes('text/event-stream'),
        'mentor response has one accepted message': () => eventCount(body, 'message') === 1,
        'mentor response has answer deltas': () => eventCount(body, 'delta') >= 1,
        'mentor response has one completion': () => eventCount(body, 'complete') === 1,
        'mentor response has no error': () => eventCount(body, 'error') === 0,
        'mentor response contains bounded answer': () => body.includes('Use a small boundary example.'),
        'mentor response omits provider metadata': () =>
            !body.includes('mentor-performance-stub')
            && !body.includes('inputTokens')
            && !body.includes('outputTokens'),
    });
    turnDuration.add(response.timings.duration);
    turnFailures.add(!valid);
    if (valid) {
        turnCompletions.add(1);
    }
}

function values(data, metricName) {
    return data.metrics[metricName]?.values ?? {};
}

export function handleSummary(data) {
    const summary = {
        format: 'ai-learning-ai-mentor-performance-v1',
        generatedAtUtc: new Date().toISOString(),
        workload: {
            identities: IDENTITY_COUNT,
            ratePerSecond: RATE_PER_SECOND,
            durationSeconds: DURATION_SECONDS,
            minimumIterations: MINIMUM_ITERATIONS,
            courseSlug: COURSE_SLUG,
            lessonId: LESSON_ID,
        },
        results: {
            checksRate: values(data, 'checks').rate,
            iterations: values(data, 'iterations').count,
            completedTurns: values(data, 'ai_mentor_turn_completions').count,
            droppedIterations: values(data, 'dropped_iterations').count ?? 0,
            requestFailureRate: values(data, 'ai_mentor_turn_failures').rate,
            requestDurationP95Ms: values(data, 'ai_mentor_turn_duration')['p(95)'],
            requestDurationP99Ms: values(data, 'ai_mentor_turn_duration')['p(99)'],
        },
    };
    return {
        '/results/ai-mentor-client-summary.json': `${JSON.stringify(summary, null, 2)}\n`,
        stdout: `${JSON.stringify(summary)}\n`,
    };
}
