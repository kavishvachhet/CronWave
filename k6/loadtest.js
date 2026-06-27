import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// Load the 6000 tokens efficiently into memory
const tokensData = new SharedArray('tokens', function () {
  const f = open('./tokens.csv');
  const lines = f.split('\n');
  lines.shift(); // remove 'token' header
  return lines.filter(line => line.trim().length > 0);
});

const BASE_URL = 'http://localhost:8080';
export let errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 5000 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
};

export default function () {
  // Pick a random token from the 6000 generated users
  const randomToken = tokensData[Math.floor(Math.random() * tokensData.length)];

  const res = http.get(`${BASE_URL}/jobs?page=0&size=20`, {
    headers: {
      Authorization: `Bearer ${randomToken}`,
    },
  });

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'duration < 500ms': (r) => r.timings.duration < 500,
  });

  errorRate.add(!ok);
  sleep(1);
}