const axios = require('axios');

const BASE_URL = 'http://localhost:8080';

async function testLoginRateLimit() {
    console.log('=== Testing POST /auth/login (limit: 10/min) ===\n');
    
    for (let i = 1; i <= 13; i++) {
        try {
            const res = await axios.post(`${BASE_URL}/auth/login`, {
                email: 'dummy@gmail.com',
                password: 'dummy@123'
            });
            console.log(`Request ${i}: ✅ ${res.status} | Remaining: ${res.headers['x-rate-limit-remaining']}`);
        } catch (err) {
            if (err.response?.status === 429) {
                console.log(`Request ${i}: ❌ 429 Too Many Requests | RetryAfter: ${err.response.data.retryAfter}s`);
            } else {
                console.log(`Request ${i}: ❌ ${err.response?.status} | ${err.message}`);
            }
        }
    }
}

testLoginRateLimit();
