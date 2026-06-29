import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ─────────────────────────────────────────────
//  STEP LOAD TEST — Find Maximum Capacity
//  
//  Strategy: Step up VUs in fixed increments
//  and hold each level for 15 seconds.
//  This clearly shows WHERE performance degrades.
//  
//  Steps: 100 → 500 → 1000 → 2000 → 3000 → 5000
//  Total duration: ~90 seconds
// ─────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const tokensData = new SharedArray('tokens', function () {
    const f = open('./tokens.csv');
    const lines = f.split('\n');
    lines.shift();
    return lines.filter(line => line.trim().length > 0);
});

export const errorRate = new Rate('errors');
export const getJobsDuration = new Trend('get_jobs_p95', true);

export const options = {
    stages: [
        // Step 1: Baseline — 100 VUs for 15s
        { duration: '5s',  target: 100 },
        { duration: '15s', target: 100 },

        // Step 2: Moderate — 500 VUs for 15s
        { duration: '5s',  target: 500 },
        { duration: '15s', target: 500 },

        // Step 3: Heavy — 1000 VUs for 15s
        { duration: '5s',  target: 1000 },
        { duration: '15s', target: 1000 },

        // Step 4: Stress — 2000 VUs for 15s
        { duration: '5s',  target: 2000 },
        { duration: '15s', target: 2000 },

        // Step 5: Peak — 3000 VUs for 15s
        { duration: '5s',  target: 3000 },
        { duration: '15s', target: 3000 },

        // Step 6: Max Push — 5000 VUs for 15s
        { duration: '5s',  target: 5000 },
        { duration: '15s', target: 5000 },

        // Cooldown
        { duration: '10s', target: 0 },
    ],

    thresholds: {
        http_req_duration: ['p(95)<1000'],
        http_req_failed:   ['rate<0.05'],
        errors:            ['rate<0.05'],
    },
};

export default function () {
    const token = tokensData[Math.floor(Math.random() * tokensData.length)];

    const res = http.get(`${BASE_URL}/jobs?page=0&size=20`, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });

    const ok = check(res, {
        'status is 200':    (r) => r.status === 200,
        'duration < 500ms': (r) => r.timings.duration < 500,
        'duration < 1s':    (r) => r.timings.duration < 1000,
        'duration < 3s':    (r) => r.timings.duration < 3000,
    });

    errorRate.add(!ok);
    getJobsDuration.add(res.timings.duration);

    sleep(0.5);
}

export function handleSummary(data) {
    const metrics = data.metrics;
    let s = '';

    s += '\n╔══════════════════════════════════════════════════════════════╗\n';
    s += '║       CronWave — STEP LOAD TEST RESULTS                     ║\n';
    s += '║       Finding Maximum Concurrent User Capacity               ║\n';
    s += '╚══════════════════════════════════════════════════════════════╝\n\n';

    if (metrics.http_reqs) {
        s += `  📊 Total Requests:   ${metrics.http_reqs.values.count}\n`;
        s += `  🔄 Throughput:       ${metrics.http_reqs.values.rate?.toFixed(2)} req/s\n`;
    }

    if (metrics.http_req_duration) {
        const d = metrics.http_req_duration.values;
        s += `\n  ── Response Times ──\n`;
        s += `  Average:  ${d.avg?.toFixed(2)}ms\n`;
        s += `  Median:   ${d.med?.toFixed(2)}ms\n`;
        s += `  p(90):    ${d['p(90)']?.toFixed(2)}ms\n`;
        s += `  p(95):    ${d['p(95)']?.toFixed(2)}ms\n`;
        s += `  p(99):    ${d['p(99)']?.toFixed(2)}ms\n`;
        s += `  Max:      ${d.max?.toFixed(2)}ms\n`;
    }

    if (metrics.http_req_failed) {
        s += `\n  ── Failure Rate ──\n`;
        s += `  HTTP Failures: ${(metrics.http_req_failed.values.rate * 100).toFixed(2)}%\n`;
    }

    if (metrics.vus_max) {
        s += `\n  ── Virtual Users ──\n`;
        s += `  Max VUs:  ${metrics.vus_max.values.value}\n`;
    }

    s += `\n  ── Interpretation ──\n`;

    const p95 = metrics.http_req_duration?.values?.['p(95)'] || 0;
    const failRate = metrics.http_req_failed?.values?.rate || 0;
    const throughput = metrics.http_reqs?.values?.rate || 0;

    if (p95 < 100 && failRate < 0.01) {
        s += `  ✅ EXCELLENT — p95: ${p95.toFixed(0)}ms, failures: ${(failRate*100).toFixed(2)}%\n`;
        s += `  💪 Server handled peak load with ease!\n`;
    } else if (p95 < 500 && failRate < 0.05) {
        s += `  ✅ GOOD — p95: ${p95.toFixed(0)}ms, failures: ${(failRate*100).toFixed(2)}%\n`;
        s += `  📈 Server handled load well within acceptable limits.\n`;
    } else if (p95 < 1000 && failRate < 0.10) {
        s += `  ⚠️  DEGRADED — p95: ${p95.toFixed(0)}ms, failures: ${(failRate*100).toFixed(2)}%\n`;
        s += `  📉 Server is under stress. Consider scaling.\n`;
    } else {
        s += `  ❌ OVERLOADED — p95: ${p95.toFixed(0)}ms, failures: ${(failRate*100).toFixed(2)}%\n`;
        s += `  🔥 Server exceeded capacity. Max safe limit is below peak VUs.\n`;
    }

    s += `\n  ── Estimated Max Capacity ──\n`;
    s += `  Throughput:  ~${throughput.toFixed(0)} req/s\n`;

    // Estimate based on throughput and think time
    if (failRate < 0.05 && p95 < 500) {
        s += `  Max VUs (sub-500ms p95): ✅ 5000+ VUs\n`;
    } else if (failRate < 0.05 && p95 < 1000) {
        s += `  Max VUs (sub-1s p95):    ⚠️  ~3000-5000 VUs\n`;
    } else if (failRate < 0.10) {
        s += `  Max VUs (acceptable):    ⚠️  ~1000-3000 VUs\n`;
    } else {
        s += `  Max VUs (safe):          ❌ <1000 VUs\n`;
    }

    s += '\n══════════════════════════════════════════════════════════════\n';

    console.log(s);
    return { 'stdout': s };
}
