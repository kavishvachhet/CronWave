import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ═══════════════════════════════════════════════════════════════
//  CronWave — REPORT ENDPOINT CONCURRENCY TEST
//
//  Goal: Find the maximum number of concurrent users the
//        report endpoints can handle before degradation.
//
//  Endpoints tested:
//    • GET /jobs?page=0&size=20   (80% of traffic — main report)
//    • GET /jobs/{id}/logs        (20% of traffic — execution logs)
//
//  Strategy: Step up VUs in clear increments, hold each level
//            for 20 seconds to get stable measurements.
//
//  Steps: 50 → 100 → 200 → 400 → 800 → 1200 → 1600 → 2000
// ═══════════════════════════════════════════════════════════════

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Single authenticated token
const AUTH_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJrYXZpc2h2YWNoaGV0YTUwQGdtYWlsLmNvbSIsImlhdCI6MTc4MjcwNjE0MSwiZXhwIjoxNzgyNzkyNTQxfQ.iASEPQKcpoauQfufsSD1l5wAj8F9d8kMwcMr-FndALA';

// ─── Custom Metrics ──────────────────────────────────────────

const getJobsErrors     = new Rate('err_get_jobs');
const getLogsErrors     = new Rate('err_get_logs');
const overallErrors     = new Rate('err_overall');

const getJobsDuration   = new Trend('dur_get_jobs', true);
const getLogsDuration   = new Trend('dur_get_logs', true);

const successCount      = new Counter('success_count');
const failCount         = new Counter('fail_count');

const status200         = new Counter('status_200');
const status4xx         = new Counter('status_4xx');
const status5xx         = new Counter('status_5xx');
const statusTimeout     = new Counter('status_timeout');

// ─── Load Profile ────────────────────────────────────────────

export const options = {
    stages: [
        // Step 1: 200 VUs (warm-up — we know this is easy)
        { duration: '5s',  target: 200 },
        { duration: '15s', target: 200 },

        // Step 2: 500 VUs
        { duration: '5s',  target: 500 },
        { duration: '15s', target: 500 },

        // Step 3: 1000 VUs
        { duration: '5s',  target: 1000 },
        { duration: '15s', target: 1000 },

        // Step 4: 2000 VUs (previous peak)
        { duration: '5s',  target: 2000 },
        { duration: '15s', target: 2000 },

        // Step 5: 3000 VUs
        { duration: '5s',  target: 3000 },
        { duration: '15s', target: 3000 },

        // Step 6: 4000 VUs
        { duration: '5s',  target: 4000 },
        { duration: '15s', target: 4000 },

        // Step 7: 5000 VUs — MAX PUSH
        { duration: '5s',  target: 5000 },
        { duration: '20s', target: 5000 },

        // Cooldown
        { duration: '10s', target: 0 },
    ],

    thresholds: {
        http_req_duration: [{ threshold: 'p(95)<2000', abortOnFail: false }],
        http_req_failed:   [{ threshold: 'rate<0.10',  abortOnFail: false }],
    },

    discardResponseBodies: false,
};

// ─── Auth Helper ─────────────────────────────────────────────

function getAuthHeaders() {
    return {
        headers: {
            Authorization: `Bearer ${AUTH_TOKEN}`,
        },
    };
}

// ─── Main Test Function ──────────────────────────────────────

export default function () {
    const roll = Math.random() * 100;

    if (roll < 80) {
        testGetJobs();
    } else {
        testGetLogs();
    }

    sleep(0.3 + Math.random() * 0.4); // 300-700ms think time
}

// ─── GET /jobs ───────────────────────────────────────────────

function testGetJobs() {
    const page = Math.floor(Math.random() * 3);
    const res = http.get(
        `${BASE_URL}/jobs?page=${page}&size=20`,
        Object.assign({}, getAuthHeaders(), { timeout: '10s' })
    );

    trackStatus(res);

    const ok = check(res, {
        'GET /jobs: status 200':       (r) => r.status === 200,
        'GET /jobs: has content':      (r) => {
            try { return JSON.parse(r.body).content !== undefined; }
            catch { return false; }
        },
        'GET /jobs: under 500ms':      (r) => r.timings.duration < 500,
        'GET /jobs: under 1s':         (r) => r.timings.duration < 1000,
        'GET /jobs: under 3s':         (r) => r.timings.duration < 3000,
    });

    getJobsErrors.add(!ok);
    getJobsDuration.add(res.timings.duration);
    overallErrors.add(!ok);
    ok ? successCount.add(1) : failCount.add(1);
}

// ─── GET /jobs/{id}/logs ─────────────────────────────────────

function testGetLogs() {
    const listRes = http.get(
        `${BASE_URL}/jobs?page=0&size=5`,
        Object.assign({}, getAuthHeaders(), { timeout: '10s' })
    );

    let jobId = null;
    try {
        const data = JSON.parse(listRes.body);
        if (data.content && data.content.length > 0) {
            const idx = Math.floor(Math.random() * data.content.length);
            jobId = data.content[idx].id;
        }
    } catch (e) { /* ignore */ }

    if (!jobId) {
        getLogsErrors.add(false);
        return;
    }

    const res = http.get(
        `${BASE_URL}/jobs/${jobId}/logs`,
        Object.assign({}, getAuthHeaders(), { timeout: '10s' })
    );

    trackStatus(res);

    const ok = check(res, {
        'GET /logs: status 200':   (r) => r.status === 200,
        'GET /logs: under 500ms':  (r) => r.timings.duration < 500,
        'GET /logs: under 1s':     (r) => r.timings.duration < 1000,
        'GET /logs: under 3s':     (r) => r.timings.duration < 3000,
    });

    getLogsErrors.add(!ok);
    getLogsDuration.add(res.timings.duration);
    overallErrors.add(!ok);
    ok ? successCount.add(1) : failCount.add(1);
}

// ─── Status Tracker ──────────────────────────────────────────

function trackStatus(res) {
    if (res.status === 0) {
        statusTimeout.add(1);
    } else if (res.status >= 200 && res.status < 300) {
        status200.add(1);
    } else if (res.status >= 400 && res.status < 500) {
        status4xx.add(1);
    } else if (res.status >= 500) {
        status5xx.add(1);
    }
}

// ═══════════════════════════════════════════════════════════════
//  SUMMARY HANDLER
// ═══════════════════════════════════════════════════════════════

export function handleSummary(data) {
    const m = data.metrics;
    let s = '';

    s += '\n';
    s += '╔══════════════════════════════════════════════════════════════════╗\n';
    s += '║   CronWave — REPORT ENDPOINT CONCURRENCY TEST RESULTS         ║\n';
    s += '║   Finding Maximum Concurrent Users for Reports                 ║\n';
    s += '╚══════════════════════════════════════════════════════════════════╝\n\n';

    // ── Overall Stats ──
    s += '  ┌─────────────────────────────────────────────────────┐\n';
    s += '  │  OVERALL STATS                                      │\n';
    s += '  └─────────────────────────────────────────────────────┘\n';

    if (m.http_reqs) {
        s += `  Total Requests:    ${m.http_reqs.values.count}\n`;
        s += `  Throughput:        ${m.http_reqs.values.rate?.toFixed(2)} req/s\n`;
    }
    if (m.iterations) {
        s += `  Total Iterations:  ${m.iterations.values.count}\n`;
    }
    if (m.vus_max) {
        s += `  Peak VUs:          ${m.vus_max.values.value}\n`;
    }
    if (m.success_count) {
        s += `  Successful:        ${m.success_count.values.count}\n`;
    }
    if (m.fail_count) {
        s += `  Failed:            ${m.fail_count.values.count}\n`;
    }

    // ── Response Time Breakdown ──
    s += '\n  ┌─────────────────────────────────────────────────────┐\n';
    s += '  │  RESPONSE TIMES                                     │\n';
    s += '  └─────────────────────────────────────────────────────┘\n';

    if (m.http_req_duration) {
        const d = m.http_req_duration.values;
        s += `  Overall:\n`;
        s += `    Average:   ${d.avg?.toFixed(2)}ms\n`;
        s += `    Median:    ${d.med?.toFixed(2)}ms\n`;
        s += `    p(90):     ${d['p(90)']?.toFixed(2)}ms\n`;
        s += `    p(95):     ${d['p(95)']?.toFixed(2)}ms\n`;
        s += `    p(99):     ${d['p(99)']?.toFixed(2)}ms\n`;
        s += `    Max:       ${d.max?.toFixed(2)}ms\n`;
    }

    if (m.dur_get_jobs) {
        const d = m.dur_get_jobs.values;
        s += `\n  GET /jobs (report listing):\n`;
        s += `    Average:   ${d.avg?.toFixed(2)}ms\n`;
        s += `    Median:    ${d.med?.toFixed(2)}ms\n`;
        s += `    p(95):     ${d['p(95)']?.toFixed(2)}ms\n`;
        s += `    p(99):     ${d['p(99)']?.toFixed(2)}ms\n`;
        s += `    Max:       ${d.max?.toFixed(2)}ms\n`;
    }

    if (m.dur_get_logs) {
        const d = m.dur_get_logs.values;
        s += `\n  GET /jobs/{id}/logs (execution logs):\n`;
        s += `    Average:   ${d.avg?.toFixed(2)}ms\n`;
        s += `    Median:    ${d.med?.toFixed(2)}ms\n`;
        s += `    p(95):     ${d['p(95)']?.toFixed(2)}ms\n`;
        s += `    p(99):     ${d['p(99)']?.toFixed(2)}ms\n`;
        s += `    Max:       ${d.max?.toFixed(2)}ms\n`;
    }

    // ── Error Breakdown ──
    s += '\n  ┌─────────────────────────────────────────────────────┐\n';
    s += '  │  ERROR BREAKDOWN                                    │\n';
    s += '  └─────────────────────────────────────────────────────┘\n';

    if (m.http_req_failed) {
        s += `  HTTP Failure Rate:    ${(m.http_req_failed.values.rate * 100).toFixed(2)}%\n`;
    }
    if (m.err_get_jobs) {
        s += `  GET /jobs Errors:     ${(m.err_get_jobs.values.rate * 100).toFixed(2)}%\n`;
    }
    if (m.err_get_logs) {
        s += `  GET /logs Errors:     ${(m.err_get_logs.values.rate * 100).toFixed(2)}%\n`;
    }
    if (m.err_overall) {
        s += `  Overall Error Rate:   ${(m.err_overall.values.rate * 100).toFixed(2)}%\n`;
    }

    // ── Status Code Distribution ──
    s += '\n  ┌─────────────────────────────────────────────────────┐\n';
    s += '  │  HTTP STATUS CODE DISTRIBUTION                      │\n';
    s += '  └─────────────────────────────────────────────────────┘\n';

    const s200 = m.status_200?.values?.count || 0;
    const s4xx = m.status_4xx?.values?.count || 0;
    const s5xx = m.status_5xx?.values?.count || 0;
    const sto  = m.status_timeout?.values?.count || 0;
    const total = s200 + s4xx + s5xx + sto;

    s += `  2xx (Success):    ${s200}  (${total > 0 ? ((s200/total)*100).toFixed(1) : 0}%)\n`;
    s += `  4xx (Client):     ${s4xx}  (${total > 0 ? ((s4xx/total)*100).toFixed(1) : 0}%)\n`;
    s += `  5xx (Server):     ${s5xx}  (${total > 0 ? ((s5xx/total)*100).toFixed(1) : 0}%)\n`;
    s += `  Timeout/Drop:     ${sto}   (${total > 0 ? ((sto/total)*100).toFixed(1) : 0}%)\n`;

    // ── Threshold Results ──
    s += '\n  ┌─────────────────────────────────────────────────────┐\n';
    s += '  │  THRESHOLD RESULTS                                  │\n';
    s += '  └─────────────────────────────────────────────────────┘\n';

    if (data.thresholds) {
        for (const [key, val] of Object.entries(data.thresholds)) {
            const icon = val.ok ? '✅' : '❌';
            s += `  ${icon} ${key}\n`;
        }
    }

    // ── Capacity Assessment ──
    s += '\n  ┌─────────────────────────────────────────────────────┐\n';
    s += '  │  CAPACITY ASSESSMENT                                │\n';
    s += '  └─────────────────────────────────────────────────────┘\n';

    const p95       = m.http_req_duration?.values?.['p(95)'] || 0;
    const p99       = m.http_req_duration?.values?.['p(99)'] || 0;
    const avg       = m.http_req_duration?.values?.avg || 0;
    const failRate  = m.http_req_failed?.values?.rate || 0;
    const throughput = m.http_reqs?.values?.rate || 0;
    const maxVUs    = m.vus_max?.values?.value || 0;
    const s5xxPct   = total > 0 ? (s5xx / total) : 0;

    let safeVUs = maxVUs;
    let grade = '';
    let emoji = '';
    let explanation = '';

    if (failRate < 0.01 && p95 < 200) {
        grade = 'EXCELLENT';
        emoji = '🏆';
        explanation = `Server crushed it! All ${maxVUs} VUs handled with sub-200ms p95.`;
        safeVUs = maxVUs;
    } else if (failRate < 0.01 && p95 < 500) {
        grade = 'GREAT';
        emoji = '✅';
        explanation = `Server handled ${maxVUs} VUs well. p95 under 500ms with near-zero failures.`;
        safeVUs = maxVUs;
    } else if (failRate < 0.05 && p95 < 1000) {
        grade = 'GOOD';
        emoji = '👍';
        explanation = `Server managed the load but showing strain. p95 under 1s.`;
        safeVUs = Math.floor(maxVUs * 0.75);
    } else if (failRate < 0.10 && p95 < 2000) {
        grade = 'DEGRADED';
        emoji = '⚠️';
        explanation = `Server is struggling. Noticeable latency increase and some failures.`;
        safeVUs = Math.floor(maxVUs * 0.50);
    } else if (failRate < 0.25) {
        grade = 'STRESSED';
        emoji = '🔶';
        explanation = `Server under heavy stress. Significant failures detected.`;
        safeVUs = Math.floor(maxVUs * 0.30);
    } else {
        grade = 'OVERLOADED';
        emoji = '🔥';
        explanation = `Server exceeded capacity. Massive failures detected.`;
        safeVUs = Math.floor(maxVUs * 0.15);
    }

    s += `\n  ${emoji}  Grade: ${grade}\n`;
    s += `  ${explanation}\n\n`;
    s += `  Key Numbers:\n`;
    s += `    Peak VUs tested:         ${maxVUs}\n`;
    s += `    Estimated safe capacity: ~${safeVUs} concurrent users\n`;
    s += `    Sustained throughput:    ~${throughput.toFixed(0)} req/s\n`;
    s += `    p95 latency:             ${p95.toFixed(0)}ms\n`;
    s += `    p99 latency:             ${p99.toFixed(0)}ms\n`;
    s += `    Failure rate:            ${(failRate * 100).toFixed(2)}%\n`;
    s += `    Server errors (5xx):     ${s5xx} (${(s5xxPct * 100).toFixed(2)}%)\n`;
    s += `    Timeouts:                ${sto}\n`;

    s += '\n  ┌─────────────────────────────────────────────────────┐\n';
    s += '  │  RECOMMENDATIONS                                    │\n';
    s += '  └─────────────────────────────────────────────────────┘\n';

    if (failRate < 0.01 && p95 < 500) {
        s += '  ✅ Server is healthy. Could likely handle even more load.\n';
        s += '  💡 Consider running with higher VU counts (3000-5000) to find the true ceiling.\n';
    } else if (failRate < 0.05) {
        s += '  ⚠️  Server is near its comfortable limit.\n';
        s += '  💡 For production, target ~70% of peak VUs as your capacity plan.\n';
        s += '  💡 Consider connection pooling, query caching, or read replicas.\n';
    } else if (s5xx > 0) {
        s += '  ❌ Server errors detected — check application logs for root cause.\n';
        s += '  💡 Common causes: DB connection pool exhaustion, thread pool limits, OOM.\n';
    }
    if (sto > 0) {
        s += `  ❌ ${sto} requests timed out — server couldn't respond within 10s.\n`;
        s += '  💡 Check if your connection pool or thread pool is maxed out.\n';
    }
    if (p95 > 1000) {
        s += '  ⚠️  p95 latency exceeds 1 second — users will notice.\n';
        s += '  💡 Add database indexes, enable query caching, or increase thread pool.\n';
    }

    s += '\n══════════════════════════════════════════════════════════════════\n';

    console.log(s);

    const now = new Date().toISOString().replace(/[:.]/g, '-');
    return {
        'stdout': s,
        [`./report_concurrency_results_${now}.json`]: JSON.stringify(data, null, 2),
    };
}
