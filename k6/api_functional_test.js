/**
 * CronWave — Comprehensive API Functional Test
 * Tests ALL 7 endpoints + rate limiting
 * 
 * Usage: node api_functional_test.js
 * Requires: npm install axios (already in k6/node_modules)
 */

const axios = require('axios');

const BASE_URL = 'http://localhost:8080';
const TIMESTAMP = Date.now();
const TEST_USER = {
    name: `LoadTest User ${TIMESTAMP}`,
    email: `loadtest_${TIMESTAMP}@example.com`,
    password: 'TestPass@123'
};

let TOKEN = null;
let CREATED_JOB_ID = null;

const results = [];

function log(status, endpoint, details) {
    const icon = status === 'PASS' ? '✅' : '❌';
    const entry = { status, endpoint, details };
    results.push(entry);
    console.log(`  ${icon} ${endpoint} — ${details}`);
}

async function testHealthCheck() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  1. HEALTH CHECK');
    console.log('═══════════════════════════════════════════');
    try {
        const res = await axios.get(`${BASE_URL}/check`);
        if (res.status === 200 && res.data === 'Working') {
            log('PASS', 'GET /check', `Status ${res.status} | Body: "${res.data}"`);
        } else {
            log('FAIL', 'GET /check', `Unexpected response: ${res.status} | ${res.data}`);
        }
    } catch (err) {
        log('FAIL', 'GET /check', `Error: ${err.message}`);
    }
}

async function testRegister() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  2. REGISTER');
    console.log('═══════════════════════════════════════════');
    try {
        const res = await axios.post(`${BASE_URL}/auth/register`, TEST_USER);
        log('PASS', 'POST /auth/register', `Status ${res.status} | Body: "${res.data}"`);
    } catch (err) {
        if (err.response) {
            log('FAIL', 'POST /auth/register', `Status ${err.response.status} | ${JSON.stringify(err.response.data)}`);
        } else {
            log('FAIL', 'POST /auth/register', `Error: ${err.message}`);
        }
    }
}

async function testDuplicateRegister() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  2b. DUPLICATE REGISTER (should fail)');
    console.log('═══════════════════════════════════════════');
    try {
        const res = await axios.post(`${BASE_URL}/auth/register`, TEST_USER);
        log('FAIL', 'POST /auth/register (dup)', `Should have rejected but got ${res.status}`);
    } catch (err) {
        if (err.response && (err.response.status === 400 || err.response.status === 409 || err.response.status === 500)) {
            log('PASS', 'POST /auth/register (dup)', `Correctly rejected: ${err.response.status}`);
        } else {
            log('FAIL', 'POST /auth/register (dup)', `Unexpected: ${err.message}`);
        }
    }
}

async function testLogin() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  3. LOGIN');
    console.log('═══════════════════════════════════════════');
    try {
        const res = await axios.post(`${BASE_URL}/auth/login`, {
            email: TEST_USER.email,
            password: TEST_USER.password
        });
        if (res.status === 200 && res.data.token) {
            TOKEN = res.data.token;
            log('PASS', 'POST /auth/login', `Status ${res.status} | Token received (${TOKEN.substring(0, 30)}...)`);
        } else {
            log('FAIL', 'POST /auth/login', `Status ${res.status} | No token in response`);
        }
    } catch (err) {
        if (err.response) {
            log('FAIL', 'POST /auth/login', `Status ${err.response.status} | ${JSON.stringify(err.response.data)}`);
        } else {
            log('FAIL', 'POST /auth/login', `Error: ${err.message}`);
        }
    }
}

async function testLoginWrongPassword() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  3b. LOGIN WITH WRONG PASSWORD (should fail)');
    console.log('═══════════════════════════════════════════');
    try {
        const res = await axios.post(`${BASE_URL}/auth/login`, {
            email: TEST_USER.email,
            password: 'WrongPass@999'
        });
        log('FAIL', 'POST /auth/login (wrong pwd)', `Should have rejected but got ${res.status}`);
    } catch (err) {
        if (err.response && (err.response.status === 401 || err.response.status === 403 || err.response.status === 500)) {
            log('PASS', 'POST /auth/login (wrong pwd)', `Correctly rejected: ${err.response.status}`);
        } else {
            log('FAIL', 'POST /auth/login (wrong pwd)', `Unexpected: ${err.message}`);
        }
    }
}

async function testCreateJob() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  4. CREATE JOB');
    console.log('═══════════════════════════════════════════');
    if (!TOKEN) {
        log('FAIL', 'POST /jobs', 'Skipped — no token from login');
        return;
    }
    try {
        const res = await axios.post(`${BASE_URL}/jobs`, {
            name: `Test Job ${TIMESTAMP}`,
            url: 'https://httpbin.org/get',
            method: 'GET',
            cronExpression: '0 */5 * * *'
        }, {
            headers: { Authorization: `Bearer ${TOKEN}` }
        });
        if (res.status === 200) {
            // The response might be a string with the job ID or a success message
            CREATED_JOB_ID = res.data;
            log('PASS', 'POST /jobs', `Status ${res.status} | Response: "${res.data}"`);
        } else {
            log('FAIL', 'POST /jobs', `Status ${res.status}`);
        }
    } catch (err) {
        if (err.response) {
            log('FAIL', 'POST /jobs', `Status ${err.response.status} | ${JSON.stringify(err.response.data)}`);
        } else {
            log('FAIL', 'POST /jobs', `Error: ${err.message}`);
        }
    }
}

async function testCreateJobUnauth() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  4b. CREATE JOB WITHOUT AUTH (should fail)');
    console.log('═══════════════════════════════════════════');
    try {
        const res = await axios.post(`${BASE_URL}/jobs`, {
            name: 'Unauthorized Job',
            url: 'https://httpbin.org/get',
            method: 'GET',
            cronExpression: '0 */5 * * *'
        });
        log('FAIL', 'POST /jobs (no auth)', `Should have rejected but got ${res.status}`);
    } catch (err) {
        if (err.response && (err.response.status === 401 || err.response.status === 403)) {
            log('PASS', 'POST /jobs (no auth)', `Correctly rejected: ${err.response.status}`);
        } else {
            log('FAIL', 'POST /jobs (no auth)', `Unexpected: ${err.message}`);
        }
    }
}

async function testGetJobs() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  5. GET JOBS (paginated)');
    console.log('═══════════════════════════════════════════');
    if (!TOKEN) {
        log('FAIL', 'GET /jobs', 'Skipped — no token from login');
        return;
    }
    try {
        const res = await axios.get(`${BASE_URL}/jobs?page=0&size=20`, {
            headers: { Authorization: `Bearer ${TOKEN}` }
        });
        if (res.status === 200) {
            const jobCount = res.data.content ? res.data.content.length : 'N/A';
            const totalElements = res.data.totalElements || 'N/A';
            log('PASS', 'GET /jobs?page=0&size=20', `Status ${res.status} | Jobs: ${jobCount} | Total: ${totalElements}`);

            // Try to find our created job's ID from the response
            if (res.data.content && res.data.content.length > 0) {
                const ourJob = res.data.content.find(j => j.name === `Test Job ${TIMESTAMP}`);
                if (ourJob) {
                    CREATED_JOB_ID = ourJob.id;
                    console.log(`    📌 Found created job ID: ${CREATED_JOB_ID}`);
                }
            }
        } else {
            log('FAIL', 'GET /jobs?page=0&size=20', `Status ${res.status}`);
        }
    } catch (err) {
        if (err.response) {
            log('FAIL', 'GET /jobs?page=0&size=20', `Status ${err.response.status} | ${JSON.stringify(err.response.data)}`);
        } else {
            log('FAIL', 'GET /jobs?page=0&size=20', `Error: ${err.message}`);
        }
    }
}

async function testPatchJobStatus() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  6. PATCH JOB STATUS (pause)');
    console.log('═══════════════════════════════════════════');
    if (!TOKEN || !CREATED_JOB_ID) {
        log('FAIL', 'PATCH /jobs/{id}/status', `Skipped — token: ${!!TOKEN}, jobId: ${CREATED_JOB_ID}`);
        return;
    }
    try {
        const res = await axios.patch(`${BASE_URL}/jobs/${CREATED_JOB_ID}/status`, {
            status: 'PAUSED'
        }, {
            headers: { Authorization: `Bearer ${TOKEN}` }
        });
        log('PASS', 'PATCH /jobs/{id}/status', `Status ${res.status} | Response: "${res.data}"`);
    } catch (err) {
        if (err.response) {
            log('FAIL', 'PATCH /jobs/{id}/status', `Status ${err.response.status} | ${JSON.stringify(err.response.data)}`);
        } else {
            log('FAIL', 'PATCH /jobs/{id}/status', `Error: ${err.message}`);
        }
    }

    // Resume it back
    console.log('\n═══════════════════════════════════════════');
    console.log('  6b. PATCH JOB STATUS (resume)');
    console.log('═══════════════════════════════════════════');
    try {
        const res = await axios.patch(`${BASE_URL}/jobs/${CREATED_JOB_ID}/status`, {
            status: 'ACTIVE'
        }, {
            headers: { Authorization: `Bearer ${TOKEN}` }
        });
        log('PASS', 'PATCH /jobs/{id}/status (resume)', `Status ${res.status} | Response: "${res.data}"`);
    } catch (err) {
        if (err.response) {
            log('FAIL', 'PATCH /jobs/{id}/status (resume)', `Status ${err.response.status} | ${JSON.stringify(err.response.data)}`);
        } else {
            log('FAIL', 'PATCH /jobs/{id}/status (resume)', `Error: ${err.message}`);
        }
    }
}

async function testGetLogs() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  7. GET EXECUTION LOGS');
    console.log('═══════════════════════════════════════════');
    if (!TOKEN || !CREATED_JOB_ID) {
        log('FAIL', 'GET /jobs/{id}/logs', `Skipped — token: ${!!TOKEN}, jobId: ${CREATED_JOB_ID}`);
        return;
    }
    try {
        const res = await axios.get(`${BASE_URL}/jobs/${CREATED_JOB_ID}/logs`, {
            headers: { Authorization: `Bearer ${TOKEN}` }
        });
        if (res.status === 200) {
            const logCount = Array.isArray(res.data) ? res.data.length : 'N/A';
            log('PASS', 'GET /jobs/{id}/logs', `Status ${res.status} | Logs: ${logCount}`);
        } else {
            log('FAIL', 'GET /jobs/{id}/logs', `Status ${res.status}`);
        }
    } catch (err) {
        if (err.response) {
            log('FAIL', 'GET /jobs/{id}/logs', `Status ${err.response.status} | ${JSON.stringify(err.response.data)}`);
        } else {
            log('FAIL', 'GET /jobs/{id}/logs', `Error: ${err.message}`);
        }
    }
}

async function testDeleteJob() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  8. DELETE JOB');
    console.log('═══════════════════════════════════════════');
    if (!TOKEN || !CREATED_JOB_ID) {
        log('FAIL', 'DELETE /jobs/{id}', `Skipped — token: ${!!TOKEN}, jobId: ${CREATED_JOB_ID}`);
        return;
    }
    try {
        const res = await axios.delete(`${BASE_URL}/jobs/${CREATED_JOB_ID}`, {
            headers: { Authorization: `Bearer ${TOKEN}` }
        });
        log('PASS', 'DELETE /jobs/{id}', `Status ${res.status} | Response: "${res.data}"`);
    } catch (err) {
        if (err.response) {
            log('FAIL', 'DELETE /jobs/{id}', `Status ${err.response.status} | ${JSON.stringify(err.response.data)}`);
        } else {
            log('FAIL', 'DELETE /jobs/{id}', `Error: ${err.message}`);
        }
    }

    // Verify deletion — GET should return empty or not contain the job
    console.log('\n═══════════════════════════════════════════');
    console.log('  8b. VERIFY DELETION');
    console.log('═══════════════════════════════════════════');
    try {
        const res = await axios.get(`${BASE_URL}/jobs?page=0&size=100`, {
            headers: { Authorization: `Bearer ${TOKEN}` }
        });
        if (res.status === 200) {
            const content = res.data.content || [];
            const found = content.find(j => j.id === CREATED_JOB_ID);
            if (!found) {
                log('PASS', 'GET /jobs (verify delete)', `Job ${CREATED_JOB_ID} no longer exists ✓`);
            } else {
                log('FAIL', 'GET /jobs (verify delete)', `Job ${CREATED_JOB_ID} still exists!`);
            }
        }
    } catch (err) {
        log('FAIL', 'GET /jobs (verify delete)', `Error: ${err.message}`);
    }
}

async function testRateLimiting() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  9. RATE LIMITING — POST /auth/register');
    console.log('     (limit: 5 req/min)');
    console.log('═══════════════════════════════════════════');

    let hitRateLimit = false;
    for (let i = 1; i <= 8; i++) {
        try {
            const res = await axios.post(`${BASE_URL}/auth/register`, {
                name: `RateTest ${i}`,
                email: `ratetest_${TIMESTAMP}_${i}@example.com`,
                password: 'Pass@123'
            });
            console.log(`    Request ${i}: ✅ ${res.status} | Remaining: ${res.headers['x-rate-limit-remaining'] || 'N/A'}`);
        } catch (err) {
            if (err.response?.status === 429) {
                hitRateLimit = true;
                console.log(`    Request ${i}: 🛑 429 Too Many Requests | RetryAfter: ${err.response.data.retryAfter}s`);
            } else {
                console.log(`    Request ${i}: ⚠️  ${err.response?.status || err.message}`);
            }
        }
    }

    if (hitRateLimit) {
        log('PASS', 'Rate Limit /auth/register', 'Rate limiter triggered correctly at limit 5');
    } else {
        log('FAIL', 'Rate Limit /auth/register', 'Rate limiter did NOT trigger within 8 requests');
    }
}

async function printSummary() {
    console.log('\n\n╔══════════════════════════════════════════════╗');
    console.log('║          API FUNCTIONAL TEST SUMMARY          ║');
    console.log('╚══════════════════════════════════════════════╝\n');

    const passed = results.filter(r => r.status === 'PASS').length;
    const failed = results.filter(r => r.status === 'FAIL').length;
    const total = results.length;

    results.forEach(r => {
        const icon = r.status === 'PASS' ? '✅' : '❌';
        console.log(`  ${icon} ${r.endpoint}`);
    });

    console.log(`\n  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`);
    console.log(`  Total: ${total} | Passed: ${passed} | Failed: ${failed}`);
    console.log(`  Result: ${failed === 0 ? '🎉 ALL TESTS PASSED' : `⚠️  ${failed} TEST(S) FAILED`}`);
    console.log(`  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`);
}

async function run() {
    console.log('╔══════════════════════════════════════════════╗');
    console.log('║    CronWave — API Functional Test Suite      ║');
    console.log('║    Testing ALL 7 endpoints + edge cases      ║');
    console.log('╚══════════════════════════════════════════════╝');
    console.log(`  Server: ${BASE_URL}`);
    console.log(`  Test User: ${TEST_USER.email}`);
    console.log(`  Timestamp: ${new Date().toISOString()}\n`);

    await testHealthCheck();
    await testRegister();
    await testDuplicateRegister();
    await testLogin();
    await testLoginWrongPassword();
    await testCreateJob();
    await testCreateJobUnauth();
    await testGetJobs();
    await testPatchJobStatus();
    await testGetLogs();
    await testDeleteJob();
    await testRateLimiting();

    await printSummary();
}

run().catch(err => {
    console.error('\n💥 Test suite crashed:', err.message);
    process.exit(1);
});
