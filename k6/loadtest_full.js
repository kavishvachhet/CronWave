import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// ─────────────────────────────────────────────
//  CONFIGURATION
// ─────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Load pre-seeded tokens for authenticated endpoints
const tokensData = new SharedArray('tokens', function () {
    const f = open('./tokens.csv');
    const lines = f.split('\n');
    lines.shift(); // remove 'token' header
    return lines.filter(line => line.trim().length > 0);
});

// ─────────────────────────────────────────────
//  CUSTOM METRICS
// ─────────────────────────────────────────────

// Per-endpoint error rates
const healthCheckErrors  = new Rate('errors_health_check');
const getJobsErrors      = new Rate('errors_get_jobs');
const createJobErrors    = new Rate('errors_create_job');
const deleteJobErrors    = new Rate('errors_delete_job');
const patchStatusErrors  = new Rate('errors_patch_status');
const loginErrors        = new Rate('errors_login');
const registerErrors     = new Rate('errors_register');
const overallErrors      = new Rate('errors_overall');

// Per-endpoint response times
const healthCheckDuration = new Trend('duration_health_check', true);
const getJobsDuration     = new Trend('duration_get_jobs', true);
const createJobDuration   = new Trend('duration_create_job', true);
const deleteJobDuration   = new Trend('duration_delete_job', true);
const patchStatusDuration = new Trend('duration_patch_status', true);
const loginDuration       = new Trend('duration_login', true);

// Counters
const successfulRequests = new Counter('successful_requests');
const failedRequests     = new Counter('failed_requests');

// ─────────────────────────────────────────────
//  LOAD TEST SCENARIOS
//  
//  We run 3 scenarios to find your max limit:
//
//  Scenario 1: "ramp_30s" — Ramp from 0 → 2000 VUs in 30s
//              Find max concurrent users in 30s window
//
//  Scenario 2: "ramp_60s" — Ramp from 0 → 5000 VUs in 60s
//              Find max concurrent users in 60s window
//
//  Scenario 3: "sustained" — Hold 3000 VUs for 30s
//              Find sustained throughput limit
// ─────────────────────────────────────────────

export const options = {
    scenarios: {
        // Phase 1: Quick 30s burst — how many can it handle instantly?
        ramp_30s: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 1000 },   // ramp to 1000
                { duration: '15s', target: 2000 },   // push to 2000
            ],
            gracefulRampDown: '5s',
            exec: 'mixedWorkload',
            startTime: '0s',
        },
        // Phase 2: 60s sustained ramp — find the breaking point
        ramp_60s: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 1000 },   // warm up
                { duration: '15s', target: 3000 },   // push it
                { duration: '15s', target: 5000 },   // max pressure
                { duration: '15s', target: 0 },       // cool down
            ],
            gracefulRampDown: '5s',
            exec: 'mixedWorkload',
            startTime: '40s',   // starts after Phase 1 finishes
        },
        // Phase 3: Sustained peak — hold max load
        sustained_peak: {
            executor: 'constant-vus',
            vus: 3000,
            duration: '30s',
            exec: 'mixedWorkload',
            startTime: '105s',  // starts after Phase 2 finishes
        },
    },

    thresholds: {
        // Overall
        http_req_duration:       ['p(50)<200', 'p(95)<1000', 'p(99)<3000'],
        http_req_failed:         ['rate<0.05'],  // < 5% failure rate
        errors_overall:          ['rate<0.05'],

        // Per-endpoint SLAs
        duration_health_check:   ['p(95)<100'],
        duration_get_jobs:       ['p(95)<500'],
        duration_create_job:     ['p(95)<500'],
        duration_login:          ['p(95)<1000'],

        errors_health_check:     ['rate<0.01'],
        errors_get_jobs:         ['rate<0.05'],
        errors_create_job:       ['rate<0.10'],
    },
};

// ─────────────────────────────────────────────
//  HELPER: Get random auth headers
// ─────────────────────────────────────────────

function authHeaders() {
    const token = tokensData[Math.floor(Math.random() * tokensData.length)];
    return {
        headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
        },
    };
}

// ─────────────────────────────────────────────
//  MIXED WORKLOAD — Realistic traffic pattern
//  
//  Distribution (mirrors real-world usage):
//    40% — GET /jobs          (most frequent: users checking their jobs)
//    20% — GET /check         (health checks / monitoring)
//    15% — POST /jobs         (creating new jobs)
//    10% — PATCH /jobs/status (pause/resume)
//     5% — DELETE /jobs       (occasional cleanup)
//     5% — POST /auth/login   (re-authentication)
//     5% — GET /jobs/{id}/logs(checking execution history)
// ─────────────────────────────────────────────

export function mixedWorkload() {
    const roll = Math.random() * 100;

    if (roll < 40) {
        testGetJobs();
    } else if (roll < 60) {
        testHealthCheck();
    } else if (roll < 75) {
        testCreateJob();
    } else if (roll < 85) {
        testPatchJobStatus();
    } else if (roll < 90) {
        testDeleteJob();
    } else if (roll < 95) {
        testLogin();
    } else {
        testGetLogs();
    }

    sleep(randomIntBetween(0, 1) * 0.5); // 0-500ms think time
}

// ─────────────────────────────────────────────
//  INDIVIDUAL ENDPOINT TESTS
// ─────────────────────────────────────────────

function testHealthCheck() {
    group('GET /check', function () {
        const res = http.get(`${BASE_URL}/check`);
        const ok = check(res, {
            'health: status 200': (r) => r.status === 200,
            'health: body is Working': (r) => r.body === 'Working',
        });
        healthCheckErrors.add(!ok);
        healthCheckDuration.add(res.timings.duration);
        overallErrors.add(!ok);
        ok ? successfulRequests.add(1) : failedRequests.add(1);
    });
}

function testGetJobs() {
    group('GET /jobs', function () {
        const page = randomIntBetween(0, 2);
        const res = http.get(`${BASE_URL}/jobs?page=${page}&size=20`, authHeaders());
        const ok = check(res, {
            'getJobs: status 200': (r) => r.status === 200,
            'getJobs: has content': (r) => {
                try { return JSON.parse(r.body).content !== undefined; } 
                catch { return false; }
            },
        });
        getJobsErrors.add(!ok);
        getJobsDuration.add(res.timings.duration);
        overallErrors.add(!ok);
        ok ? successfulRequests.add(1) : failedRequests.add(1);
    });
}

function testCreateJob() {
    group('POST /jobs', function () {
        const vuId = __VU;
        const iterId = __ITER;
        const payload = JSON.stringify({
            name: `k6-loadtest-job-${vuId}-${iterId}`,
            url: 'https://httpbin.org/get',
            method: 'GET',
            cronExpression: '0 */30 * * *',
        });

        const res = http.post(`${BASE_URL}/jobs`, payload, authHeaders());
        const ok = check(res, {
            'createJob: status 200': (r) => r.status === 200,
        });
        createJobErrors.add(!ok);
        createJobDuration.add(res.timings.duration);
        overallErrors.add(!ok);
        ok ? successfulRequests.add(1) : failedRequests.add(1);
    });
}

function testPatchJobStatus() {
    group('PATCH /jobs/{id}/status', function () {
        // First, get a job to patch
        const getRes = http.get(`${BASE_URL}/jobs?page=0&size=5`, authHeaders());
        let jobId = null;

        try {
            const data = JSON.parse(getRes.body);
            if (data.content && data.content.length > 0) {
                jobId = data.content[0].id;
            }
        } catch (e) { /* ignore */ }

        if (!jobId) {
            patchStatusErrors.add(false); // not a server error
            return;
        }

        const payload = JSON.stringify({ status: 'PAUSED' });
        const res = http.patch(`${BASE_URL}/jobs/${jobId}/status`, payload, authHeaders());
        const ok = check(res, {
            'patchStatus: status 200': (r) => r.status === 200,
        });
        patchStatusErrors.add(!ok);
        patchStatusDuration.add(res.timings.duration);
        overallErrors.add(!ok);
        ok ? successfulRequests.add(1) : failedRequests.add(1);

        // Resume it back
        if (ok) {
            const resumePayload = JSON.stringify({ status: 'ACTIVE' });
            http.patch(`${BASE_URL}/jobs/${jobId}/status`, resumePayload, authHeaders());
        }
    });
}

function testDeleteJob() {
    group('DELETE /jobs/{id}', function () {
        // Create a temp job then delete it
        const createPayload = JSON.stringify({
            name: `k6-delete-test-${__VU}-${__ITER}`,
            url: 'https://httpbin.org/get',
            method: 'GET',
            cronExpression: '0 */30 * * *',
        });

        const createRes = http.post(`${BASE_URL}/jobs`, createPayload, authHeaders());
        if (createRes.status !== 200) {
            deleteJobErrors.add(true);
            overallErrors.add(true);
            failedRequests.add(1);
            return;
        }

        // Get the job ID to delete
        const getRes = http.get(`${BASE_URL}/jobs?page=0&size=5`, authHeaders());
        let jobId = null;
        try {
            const data = JSON.parse(getRes.body);
            if (data.content && data.content.length > 0) {
                // Find the job we just created
                const ourJob = data.content.find(j => j.name === `k6-delete-test-${__VU}-${__ITER}`);
                jobId = ourJob ? ourJob.id : data.content[0].id;
            }
        } catch (e) { /* ignore */ }

        if (!jobId) {
            deleteJobErrors.add(false);
            return;
        }

        const res = http.del(`${BASE_URL}/jobs/${jobId}`, null, authHeaders());
        const ok = check(res, {
            'deleteJob: status 200': (r) => r.status === 200,
        });
        deleteJobErrors.add(!ok);
        deleteJobDuration.add(res.timings.duration);
        overallErrors.add(!ok);
        ok ? successfulRequests.add(1) : failedRequests.add(1);
    });
}

function testLogin() {
    group('POST /auth/login', function () {
        const idx = randomIntBetween(1, 6000);
        const payload = JSON.stringify({
            email: `test_user_${idx}@example.com`,
            password: 'dummy_hashed_password', // won't work with BCrypt but tests server response
        });

        const res = http.post(`${BASE_URL}/auth/login`, payload, {
            headers: { 'Content-Type': 'application/json' },
        });

        // Login may return 401/403 since password is dummy — we just care about server handling load
        const ok = check(res, {
            'login: server responded': (r) => r.status !== 0 && r.status < 500,
        });
        loginErrors.add(!ok);
        loginDuration.add(res.timings.duration);
        overallErrors.add(!ok);
        ok ? successfulRequests.add(1) : failedRequests.add(1);
    });
}

function testGetLogs() {
    group('GET /jobs/{id}/logs', function () {
        // Get a job first
        const getRes = http.get(`${BASE_URL}/jobs?page=0&size=5`, authHeaders());
        let jobId = null;

        try {
            const data = JSON.parse(getRes.body);
            if (data.content && data.content.length > 0) {
                jobId = data.content[0].id;
            }
        } catch (e) { /* ignore */ }

        if (!jobId) {
            return; // no jobs to get logs for
        }

        const res = http.get(`${BASE_URL}/jobs/${jobId}/logs`, authHeaders());
        const ok = check(res, {
            'getLogs: status 200': (r) => r.status === 200,
        });
        overallErrors.add(!ok);
        ok ? successfulRequests.add(1) : failedRequests.add(1);
    });
}

// ─────────────────────────────────────────────
//  SUMMARY HANDLER
// ─────────────────────────────────────────────

export function handleSummary(data) {
    const now = new Date().toISOString().replace(/[:.]/g, '-');
    
    // Build a clean text summary
    let summary = '';
    summary += '\n╔══════════════════════════════════════════════════════════════╗\n';
    summary += '║         CronWave — LOAD TEST RESULTS SUMMARY                ║\n';
    summary += '╚══════════════════════════════════════════════════════════════╝\n\n';

    // Overall stats
    const metrics = data.metrics;
    
    if (metrics.http_reqs) {
        summary += `  📊 Total Requests:     ${metrics.http_reqs.values.count}\n`;
        summary += `  ⏱️  Total Duration:     ${(metrics.http_reqs.values.count > 0 ? 'See below' : 'N/A')}\n`;
    }
    if (metrics.http_req_duration) {
        const d = metrics.http_req_duration.values;
        summary += `\n  ── Response Times ──\n`;
        summary += `  Average:    ${d.avg?.toFixed(2)}ms\n`;
        summary += `  Median:     ${d.med?.toFixed(2)}ms\n`;
        summary += `  p(90):      ${d['p(90)']?.toFixed(2)}ms\n`;
        summary += `  p(95):      ${d['p(95)']?.toFixed(2)}ms\n`;
        summary += `  p(99):      ${d['p(99)']?.toFixed(2)}ms\n`;
        summary += `  Max:        ${d.max?.toFixed(2)}ms\n`;
    }
    if (metrics.http_req_failed) {
        const failRate = (metrics.http_req_failed.values.rate * 100).toFixed(2);
        summary += `\n  ── Failure Rate ──\n`;
        summary += `  HTTP Failures: ${failRate}%\n`;
    }
    if (metrics.http_reqs) {
        const throughput = metrics.http_reqs.values.rate?.toFixed(2);
        summary += `  Throughput:    ${throughput} req/s\n`;
    }

    // Per-endpoint breakdown
    summary += `\n  ── Per-Endpoint p95 Latency ──\n`;
    const endpointMetrics = [
        ['Health Check', 'duration_health_check'],
        ['GET /jobs', 'duration_get_jobs'],
        ['POST /jobs', 'duration_create_job'],
        ['DELETE /jobs', 'duration_delete_job'],
        ['PATCH /status', 'duration_patch_status'],
        ['POST /login', 'duration_login'],
    ];

    for (const [name, key] of endpointMetrics) {
        if (metrics[key]) {
            const v = metrics[key].values;
            summary += `  ${name.padEnd(16)} avg: ${v.avg?.toFixed(0)}ms  p95: ${v['p(95)']?.toFixed(0)}ms  max: ${v.max?.toFixed(0)}ms\n`;
        }
    }

    // Per-endpoint error rates
    summary += `\n  ── Per-Endpoint Error Rates ──\n`;
    const errorMetrics = [
        ['Health Check', 'errors_health_check'],
        ['GET /jobs', 'errors_get_jobs'],
        ['POST /jobs', 'errors_create_job'],
        ['DELETE /jobs', 'errors_delete_job'],
        ['PATCH /status', 'errors_patch_status'],
        ['POST /login', 'errors_login'],
    ];

    for (const [name, key] of errorMetrics) {
        if (metrics[key]) {
            const rate = (metrics[key].values.rate * 100).toFixed(2);
            summary += `  ${name.padEnd(16)} ${rate}%\n`;
        }
    }

    // Threshold pass/fail
    summary += `\n  ── Threshold Results ──\n`;
    if (data.thresholds) {
        for (const [key, val] of Object.entries(data.thresholds)) {
            const icon = val.ok ? '✅' : '❌';
            summary += `  ${icon} ${key}\n`;
        }
    }

    // VU stats
    if (metrics.vus_max) {
        summary += `\n  ── Virtual Users ──\n`;
        summary += `  Max VUs:  ${metrics.vus_max.values.value}\n`;
    }

    if (metrics.successful_requests) {
        summary += `\n  ── Request Counts ──\n`;
        summary += `  Successful: ${metrics.successful_requests.values.count}\n`;
        summary += `  Failed:     ${metrics.failed_requests?.values?.count || 0}\n`;
    }

    summary += '\n══════════════════════════════════════════════════════════════\n';

    console.log(summary);

    // Also save to JSON for detailed analysis
    return {
        'stdout': summary,
        [`./loadtest_results_${now}.json`]: JSON.stringify(data, null, 2),
    };
}
