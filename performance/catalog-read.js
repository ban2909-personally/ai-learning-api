import http from 'k6/http';
import exec from 'k6/execution';
import {check} from 'k6';

const BASE_URL = __ENV.BASE_URL;
const EXPECTED_COURSES = 5000;
const EXPECTED_CATEGORY_COURSES = 1250;
const RATE_PER_SECOND = 25;
const DURATION_SECONDS = 30;

if (!BASE_URL || !BASE_URL.startsWith('http://')) {
    throw new Error('BASE_URL must identify the isolated HTTP application container');
}

export const options = {
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        catalog_read: {
            executor: 'constant-arrival-rate',
            rate: RATE_PER_SECOND,
            timeUnit: '1s',
            duration: `${DURATION_SECONDS}s`,
            preAllocatedVUs: 20,
            maxVUs: 50,
            gracefulStop: '5s',
            tags: {workload: 'catalog-read'},
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<750', 'p(99)<1500'],
        dropped_iterations: ['count==0'],
        iterations: ['count>=740'],
    },
};

function parseJson(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

function verifyPage(response, expectedTotal) {
    const body = parseJson(response);
    return check(response, {
        'catalog page is HTTP 200': current => current.status === 200,
        'catalog page has expected total': () => body?.totalElements === expectedTotal,
        'catalog page has twelve items': () => Array.isArray(body?.items) && body.items.length === 12,
        'catalog page items have stable identifiers': () =>
            body?.items?.every(item => typeof item.id === 'string' && typeof item.slug === 'string') === true,
    });
}

function popularPage() {
    const response = http.get(`${BASE_URL}/api/v1/courses?size=12`, {
        tags: {endpoint: 'catalog-popular'},
        responseCallback: http.expectedStatuses(200),
    });
    verifyPage(response, EXPECTED_COURSES);
}

function categoryPage() {
    const response = http.get(`${BASE_URL}/api/v1/courses?category=backend&size=12`, {
        tags: {endpoint: 'catalog-category'},
        responseCallback: http.expectedStatuses(200),
    });
    verifyPage(response, EXPECTED_CATEGORY_COURSES);
}

function courseDetail() {
    const response = http.get(`${BASE_URL}/api/v1/courses/performance-course-02500`, {
        tags: {endpoint: 'catalog-detail'},
        responseCallback: http.expectedStatuses(200),
    });
    const body = parseJson(response);
    check(response, {
        'course detail is HTTP 200': current => current.status === 200,
        'course detail has expected slug': () => body?.slug === 'performance-course-02500',
        'course detail has expected instructor': () =>
            body?.instructorName === 'Performance Baseline Instructor',
    });
}

export default function () {
    const slot = exec.scenario.iterationInTest % 20;
    if (slot < 12) {
        popularPage();
    } else if (slot < 17) {
        categoryPage();
    } else {
        courseDetail();
    }
}

function values(data, metricName) {
    return data.metrics[metricName]?.values ?? {};
}

export function handleSummary(data) {
    const summary = {
        format: 'ai-learning-catalog-performance-v1',
        generatedAtUtc: new Date().toISOString(),
        workload: {
            datasetPublishedCourses: EXPECTED_COURSES,
            ratePerSecond: RATE_PER_SECOND,
            durationSeconds: DURATION_SECONDS,
            paths: ['catalog-popular', 'catalog-category', 'catalog-detail'],
        },
        results: {
            checksRate: values(data, 'checks').rate,
            iterations: values(data, 'iterations').count,
            droppedIterations: values(data, 'dropped_iterations').count ?? 0,
            requestFailureRate: values(data, 'http_req_failed').rate,
            requestDurationP95Ms: values(data, 'http_req_duration')['p(95)'],
            requestDurationP99Ms: values(data, 'http_req_duration')['p(99)'],
        },
    };

    return {
        stdout: `${JSON.stringify(summary)}\n`,
        '/results/catalog-performance-summary.json': `${JSON.stringify(summary, null, 2)}\n`,
    };
}
