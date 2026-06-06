import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    // Stage configurations for load/concurrency test
    stages: [
        { duration: '5s', target: 10 },  // ramp-up to 10 VUs
        { duration: '10s', target: 10 }, // run 10 concurrent VUs
        { duration: '5s', target: 0 },   // ramp-down to 0 VUs
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% of requests must complete under 500ms
        http_req_failed: ['rate<0.01'],    // fail rate must be less than 1%
    },
};

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const ACCOUNT_ID = 'k6-load-test-account';

// Setup phase: get JWT token from Gateway
export function setup() {
    const res = http.get(`${GATEWAY_URL}/auth/token?client=k6-tester`);
    
    check(res, {
        'Auth token generated': (r) => r.status === 200,
    });

    const token = res.json().token;
    return { token: token };
}

// Virtual User execution flow
export default function (data) {
    const token = data.token;
    const eventId = `evt-k6-${randomString(10)}`;
    const isCredit = Math.random() > 0.4;
    const type = isCredit ? 'CREDIT' : 'DEBIT';
    const amount = (Math.random() * 100 + 1).toFixed(2);

    const payload = JSON.stringify({
        eventId: eventId,
        accountId: ACCOUNT_ID,
        type: type,
        amount: parseFloat(amount),
        currency: 'USD',
        eventTimestamp: new Date().toISOString(),
        metadata: {
            source: 'k6-concurrency-agent',
            iteration: __ITER
        }
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
    };

    // 1. Submit event to gateway
    const postRes = http.post(`${GATEWAY_URL}/events`, payload, params);

    check(postRes, {
        'Event accepted (201)': (r) => r.status === 201,
    });

    // 2. Submit same event (Idempotency check)
    const dupRes = http.post(`${GATEWAY_URL}/events`, payload, params);
    
    check(dupRes, {
        'Idempotency duplicate handled (209)': (r) => r.status === 209,
    });

    sleep(0.1); // Sleep 100ms between loops
}

// Teardown phase: print summaries
export function teardown(data) {
    console.log('Teardown complete. Event Ledger successfully load tested.');
}
