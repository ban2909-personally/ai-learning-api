import http from 'k6/http';
import ws from 'k6/ws';
import exec from 'k6/execution';
import {check, sleep} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const WS_URL = __ENV.WS_URL;
const FRONTEND_ORIGIN = __ENV.FRONTEND_ORIGIN;
const RUN_ID = __ENV.NOTIFICATION_WEBSOCKET_RUN_ID;
const PASSWORD = __ENV.NOTIFICATION_WEBSOCKET_PASSWORD;
const COURSE_SLUG = 'notification-websocket-performance';
const LESSON_ID = '33000000-0000-0000-0000-000000000001';
const IDENTITY_COUNT = 40;
const SESSIONS_PER_IDENTITY = 2;
const EXPECTED_SESSIONS = IDENTITY_COUNT * SESSIONS_PER_IDENTITY;
const COMPLETION_WORKERS = 8;
const CONNECTION_WINDOW_SECONDS = 10;
const SOCKET_TIMEOUT_MILLISECONDS = 30000;
const DUPLICATE_OBSERVATION_MILLISECONDS = 2000;
const LESSON_DURATION_SECONDS = 1800;

if (!BASE_URL || !BASE_URL.startsWith('http://')) {
    throw new Error('BASE_URL must identify the isolated HTTP application container');
}
if (!WS_URL || !WS_URL.startsWith('ws://')) {
    throw new Error('WS_URL must identify the isolated WebSocket application container');
}
if (!FRONTEND_ORIGIN || !FRONTEND_ORIGIN.startsWith('https://')) {
    throw new Error('FRONTEND_ORIGIN must be the isolated exact HTTPS origin');
}
if (!RUN_ID || RUN_ID.length > 48 || !/^[a-z0-9-]+$/.test(RUN_ID)) {
    throw new Error('NOTIFICATION_WEBSOCKET_RUN_ID must be at most 48 lowercase letters, digits, or hyphens');
}
if (!PASSWORD || PASSWORD.length < 8 || PASSWORD.length > 72) {
    throw new Error('NOTIFICATION_WEBSOCKET_PASSWORD must satisfy the registration contract');
}

const upgradeSuccess = new Rate('notification_websocket_upgrade_success');
const stompConnected = new Counter('notification_websocket_stomp_connected');
const subscriptionsReady = new Counter('notification_websocket_subscriptions_ready');
const messagesReceived = new Counter('notification_websocket_messages_received');
const messageValidity = new Rate('notification_websocket_message_validity');
const deliveryLatency = new Trend('notification_websocket_delivery_latency', true);
const unexpectedMessages = new Counter('notification_websocket_unexpected_messages');
const prematureDisconnects = new Counter('notification_websocket_premature_disconnects');
const socketTimeouts = new Counter('notification_websocket_timeouts');
const socketErrors = new Counter('notification_websocket_errors');
const protocolErrors = new Counter('notification_websocket_protocol_errors');
const completionRequests = new Counter('notification_websocket_completion_requests');
const completionFailures = new Rate('notification_websocket_completion_failures');

export const options = {
    setupTimeout: '2m',
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        notification_subscribers: {
            executor: 'per-vu-iterations',
            exec: 'subscribe',
            vus: EXPECTED_SESSIONS,
            iterations: 1,
            maxDuration: '40s',
            gracefulStop: '2s',
            tags: {workload: 'notification-websocket-subscriber'},
        },
        lesson_completions: {
            executor: 'shared-iterations',
            exec: 'completeLesson',
            startTime: `${CONNECTION_WINDOW_SECONDS}s`,
            vus: COMPLETION_WORKERS,
            iterations: IDENTITY_COUNT,
            maxDuration: '15s',
            gracefulStop: '2s',
            tags: {workload: 'notification-websocket-completion'},
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        dropped_iterations: ['count==0'],
        notification_websocket_upgrade_success: ['rate==1'],
        notification_websocket_stomp_connected: [`count==${EXPECTED_SESSIONS}`],
        notification_websocket_subscriptions_ready: [`count==${EXPECTED_SESSIONS}`],
        notification_websocket_messages_received: [`count==${EXPECTED_SESSIONS}`],
        notification_websocket_message_validity: ['rate==1'],
        notification_websocket_delivery_latency: ['p(95)<2000', 'p(99)<5000'],
        notification_websocket_unexpected_messages: ['count==0'],
        notification_websocket_premature_disconnects: ['count==0'],
        notification_websocket_timeouts: ['count==0'],
        notification_websocket_errors: ['count==0'],
        notification_websocket_protocol_errors: ['count==0'],
        notification_websocket_completion_requests: [`count==${IDENTITY_COUNT}`],
        notification_websocket_completion_failures: ['rate==0'],
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

function stompFrame(command, headers = {}, body = '') {
    const lines = [command];
    for (const [name, value] of Object.entries(headers)) {
        lines.push(`${name}:${value}`);
    }
    return `${lines.join('\n')}\n\n${body}\u0000`;
}

function parseStompFrames(data) {
    return String(data)
        .replaceAll('\r\n', '\n')
        .split('\u0000')
        .map(frame => frame.trim())
        .filter(frame => frame.length > 0)
        .map(frame => {
            const separator = frame.indexOf('\n\n');
            const headerBlock = separator >= 0 ? frame.substring(0, separator) : frame;
            const body = separator >= 0 ? frame.substring(separator + 2) : '';
            const lines = headerBlock.split('\n');
            const command = lines.shift();
            const headers = {};
            for (const line of lines) {
                const colon = line.indexOf(':');
                if (colon > 0) {
                    headers[line.substring(0, colon)] = line.substring(colon + 1);
                }
            }
            return {command, headers, body};
        });
}

function isUuid(value) {
    return typeof value === 'string'
        && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function validateNotification(body) {
    const createdAtMilliseconds = Date.parse(body?.createdAt);
    return isUuid(body?.id)
        && body?.type === 'LESSON_COMPLETED'
        && typeof body?.title === 'string'
        && body.title.length > 0
        && typeof body?.body === 'string'
        && body.body.length > 0
        && body?.targetPath === '/my-learning'
        && Number.isFinite(createdAtMilliseconds)
        && body?.readAt === null;
}

export function setup() {
    const tokens = [];

    for (let index = 1; index <= IDENTITY_COUNT; index += 1) {
        const identity = String(index).padStart(2, '0');
        const email = `notification-ws-${RUN_ID}-${identity}@example.invalid`;
        const registration = http.post(
            `${BASE_URL}/api/v1/auth/register`,
            JSON.stringify({
                email,
                password: PASSWORD,
                displayName: `Notification Student ${identity}`,
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
            'setup enrollment targets WebSocket course': () =>
                enrollmentBody?.course?.slug === COURSE_SLUG,
        });
        if (!enrolled) {
            throw new Error(`enrollment setup failed for identity ${identity} with HTTP ${enrollment.status}`);
        }
        tokens.push(token);
    }

    return {tokens};
}

export function subscribe(data) {
    const slot = exec.scenario.iterationInTest;
    if (slot >= EXPECTED_SESSIONS) {
        throw new Error(`subscriber slot ${slot} exceeds expected session capacity`);
    }
    const identityIndex = Math.floor(slot / SESSIONS_PER_IDENTITY);
    const receiptId = `subscription-${slot}`;
    let connected = false;
    let subscribed = false;
    let messages = 0;
    let closingAfterDelivery = false;

    const confirmSubscription = () => {
        if (!subscribed) {
            subscribed = true;
            subscriptionsReady.add(1);
        }
    };

    const response = ws.connect(
        `${WS_URL}/ws/notifications`,
        {
            headers: {
                Origin: FRONTEND_ORIGIN,
                'Sec-WebSocket-Protocol': 'v12.stomp',
            },
            tags: {phase: 'workload', endpoint: 'notification-websocket'},
        },
        socket => {
            socket.on('open', () => {
                socket.send(stompFrame('CONNECT', {
                    'accept-version': '1.2',
                    'heart-beat': '0,0',
                    host: 'notification-performance',
                    Authorization: `Bearer ${data.tokens[identityIndex]}`,
                }));
            });

            socket.on('message', raw => {
                for (const frame of parseStompFrames(raw)) {
                    if (frame.command === 'CONNECTED') {
                        if (connected) {
                            protocolErrors.add(1);
                            continue;
                        }
                        connected = true;
                        stompConnected.add(1);
                        socket.send(stompFrame('SUBSCRIBE', {
                            id: `notifications-${slot}`,
                            destination: '/user/queue/notifications',
                            ack: 'auto',
                            receipt: receiptId,
                        }));
                        continue;
                    }
                    if (frame.command === 'RECEIPT') {
                        if (frame.headers['receipt-id'] !== receiptId) {
                            protocolErrors.add(1);
                            continue;
                        }
                        confirmSubscription();
                        continue;
                    }
                    if (frame.command === 'ERROR') {
                        protocolErrors.add(1);
                        continue;
                    }
                    if (frame.command !== 'MESSAGE') {
                        protocolErrors.add(1);
                        continue;
                    }

                    // A routed MESSAGE conclusively proves the user-destination
                    // subscription was accepted even when the simple broker does
                    // not emit a SUBSCRIBE receipt.
                    confirmSubscription();
                    messages += 1;
                    messagesReceived.add(1);
                    let body = null;
                    try {
                        body = JSON.parse(frame.body);
                    } catch (_) {
                        body = null;
                    }
                    const valid = messages === 1 && validateNotification(body);
                    messageValidity.add(valid);
                    if (!valid) {
                        unexpectedMessages.add(1);
                    } else {
                        deliveryLatency.add(Date.now() - Date.parse(body.createdAt));
                    }

                    if (!closingAfterDelivery) {
                        closingAfterDelivery = true;
                        socket.setTimeout(() => {
                            socket.send(stompFrame('DISCONNECT'));
                            socket.close();
                        }, DUPLICATE_OBSERVATION_MILLISECONDS);
                    }
                }
            });

            socket.on('error', () => {
                socketErrors.add(1);
            });

            socket.on('close', () => {
                if (!connected || !subscribed || messages !== 1) {
                    prematureDisconnects.add(1);
                }
            });

            socket.setTimeout(() => {
                socketTimeouts.add(1);
                socket.close();
            }, SOCKET_TIMEOUT_MILLISECONDS);
        },
    );

    const upgraded = check(response, {
        'WebSocket upgrade is HTTP 101': current => current?.status === 101,
    });
    upgradeSuccess.add(upgraded);
}

export function completeLesson(data) {
    const identityIndex = exec.scenario.iterationInTest;
    if (identityIndex >= IDENTITY_COUNT) {
        throw new Error(`completion index ${identityIndex} exceeds identity capacity`);
    }
    const response = http.put(
        `${BASE_URL}/api/v1/me/courses/${COURSE_SLUG}/lessons/${LESSON_ID}/progress`,
        JSON.stringify({positionSeconds: LESSON_DURATION_SECONDS, completed: true}),
        {
            headers: bearerHeaders(data.tokens[identityIndex], true),
            tags: {phase: 'workload', endpoint: 'lesson-completion'},
            responseCallback: http.expectedStatuses(200),
        },
    );
    const body = parseJson(response);
    completionRequests.add(1);
    const valid = check(response, {
        'completion write is HTTP 200': current => current.status === 200,
        'completion returns WebSocket lesson': () => body?.lessonId === LESSON_ID,
        'completion persists terminal state': () =>
            body?.positionSeconds === LESSON_DURATION_SECONDS
            && body?.completed === true
            && typeof body?.updatedAt === 'string',
    });
    completionFailures.add(!valid);
    sleep(1);
}

function values(data, metricName) {
    return data.metrics[metricName]?.values ?? {};
}

export function handleSummary(data) {
    const summary = {
        format: 'ai-learning-notification-websocket-v1',
        generatedAtUtc: new Date().toISOString(),
        workload: {
            identities: IDENTITY_COUNT,
            sessionsPerIdentity: SESSIONS_PER_IDENTITY,
            expectedSessions: EXPECTED_SESSIONS,
            completionWorkers: COMPLETION_WORKERS,
            expectedCompletions: IDENTITY_COUNT,
            connectionWindowSeconds: CONNECTION_WINDOW_SECONDS,
            courseSlug: COURSE_SLUG,
        },
        results: {
            checksRate: values(data, 'checks').rate,
            droppedIterations: values(data, 'dropped_iterations').count ?? 0,
            httpFailureRate: values(data, 'http_req_failed').rate,
            upgradeSuccessRate: values(data, 'notification_websocket_upgrade_success').rate,
            stompConnected: values(data, 'notification_websocket_stomp_connected').count,
            subscriptionsReady: values(data, 'notification_websocket_subscriptions_ready').count,
            completionRequests: values(data, 'notification_websocket_completion_requests').count,
            completionFailureRate: values(data, 'notification_websocket_completion_failures').rate,
            messagesReceived: values(data, 'notification_websocket_messages_received').count,
            messageValidityRate: values(data, 'notification_websocket_message_validity').rate,
            deliveryLatencyP95Ms: values(data, 'notification_websocket_delivery_latency')['p(95)'],
            deliveryLatencyP99Ms: values(data, 'notification_websocket_delivery_latency')['p(99)'],
            unexpectedMessages: values(data, 'notification_websocket_unexpected_messages').count ?? 0,
            prematureDisconnects: values(data, 'notification_websocket_premature_disconnects').count ?? 0,
            socketTimeouts: values(data, 'notification_websocket_timeouts').count ?? 0,
            socketErrors: values(data, 'notification_websocket_errors').count ?? 0,
            protocolErrors: values(data, 'notification_websocket_protocol_errors').count ?? 0,
        },
    };

    return {
        stdout: `${JSON.stringify(summary)}\n`,
        '/results/notification-websocket-client-summary.json': `${JSON.stringify(summary, null, 2)}\n`,
    };
}
