import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// We want to test exactly 10,000 registrations.
export const options = {
    scenarios: {
        register_10k: {
            executor: 'shared-iterations',
            vus: 100, // 100 concurrent pipelines
            iterations: 10000, // Exactly 10k registrations
            maxDuration: '3m', // Give it up to 3 minutes
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const registerDuration = new Trend('register_duration', true);
const registerFailures = new Rate('register_failures');
const status200 = new Counter('status_200');

export default function () {
    // Generate a unique email for every single iteration
    const uniqueEmail = `test_user_${__VU}_${__ITER}_${Date.now()}@example.com`;

    const payload = JSON.stringify({
        name: `Test User ${__ITER}`,
        email: uniqueEmail,
        password: 'Password123!',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
        timeout: '30s', // High timeout because BCrypt takes CPU time and requests queue up
    };

    const res = http.post(`${BASE_URL}/auth/register`, payload, params);

    const ok = check(res, {
        'status is 200 OK': (r) => r.status === 200,
        'returns JWT token': (r) => {
            try { return JSON.parse(r.body).token && JSON.parse(r.body).token.length > 50; }
            catch { return false; }
        },
    });

    if (ok) {
        status200.add(1);
    } else {
        registerFailures.add(1);
    }
    
    registerDuration.add(res.timings.duration);
}

export function handleSummary(data) {
    const m = data.metrics;
    let s = '\n╔══════════════════════════════════════════════════════════════════╗\n';
    s += '║   REGISTRATION TEST (10,000 USERS)                               ║\n';
    s += '╚══════════════════════════════════════════════════════════════════╝\n\n';

    s += `Total Registrations:  ${m.iterations.values.count}\n`;
    s += `Success (200 OK):     ${m.status_200 ? m.status_200.values.count : 0}\n`;
    s += `Failed:               ${m.register_failures ? m.register_failures.values.passes : 0}\n\n`;
    
    s += `Throughput (Reg/Sec): ${m.iterations.values.rate.toFixed(2)} users per second\n\n`;

    if (m.register_duration) {
        s += `Average time per reg: ${m.register_duration.values.avg.toFixed(2)} ms\n`;
        s += `p(95) time:           ${m.register_duration.values['p(95)'].toFixed(2)} ms\n`;
        s += `Max wait time:        ${m.register_duration.values.max.toFixed(2)} ms\n`;
    }

    console.log(s);
    return {
        'stdout': s
    };
}
