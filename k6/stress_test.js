import http from 'k6/http';
import { check, sleep } from 'k6';

// 1. Configure the stress test stages
export const options = {
    // This ramps up to a massive number of concurrent Virtual Users (VUs)
    stages: [
        { duration: '30s', target: 1000 },  // Ramp up to 1,000 users over 30s
        { duration: '30s', target: 5000 },  // Spike to 5,000 users over the next 30s
        { duration: '30s', target: 5000 },  // Hold at 5,000 users
        { duration: '20s', target: 0 },     // Ramp down to 0
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
        http_req_failed: ['rate<0.01'],   // Error rate must be less than 1%
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    // Using the hardcoded token provided by the user
    return { token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJrYXZpc2h2YWNoaGV0YTUwQGdtYWlsLmNvbSIsInVzZXJJZCI6IjllZTE0Yzk4LWM5ZTMtNGNkMi04ZGVlLWEwNWU0NGU1Mjg2YiIsImlhdCI6MTc4MjczNjUzMCwiZXhwIjoxNzgyODIyOTMwfQ.88agOh9598rswT-6cSZt4vPZwV_GrONdnOjh2YjZz8E" };
}

// 3. Execution Phase: Run by every Virtual User concurrently
export default function (data) {
    const headers = {
        'Authorization': `Bearer ${data.token}`,
        'Content-Type': 'application/json',
    };

    // Action A: Create a Job (Tests Kafka Ingestion)
    const postPayload = JSON.stringify({
        name: `Load Test Job ${__VU}-${__ITER}`,
        url: 'https://httpbin.org/get',
        method: 'GET',
        cronExpression: '0 0 1 * * ?' // Run once a month so they don't flood the executor
    });

    const postRes = http.post(`${BASE_URL}/jobs`, postPayload, { headers });
    
    check(postRes, {
        'POST job is status 200': (r) => r.status === 200,
    });

    // Action B: Fetch Dashboard (Tests Redis Cache)
    const getRes = http.get(`${BASE_URL}/jobs?page=0&size=20`, { headers });
    
    check(getRes, {
        'GET jobs is status 200': (r) => r.status === 200,
    });

    // Small sleep to simulate realistic user pacing
    sleep(1);
}
