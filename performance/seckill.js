import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const activityId = Number(__ENV.ACTIVITY_ID || 1);
const users = Number(__ENV.USERS || 100);
const expectedStock = Number(__ENV.STOCK || 50);
const password = __ENV.USER_PASSWORD || '123456';

const accepted = new Counter('seckill_accepted');
const soldOut = new Counter('seckill_sold_out');
const repeated = new Counter('seckill_repeated');
const unexpected = new Counter('seckill_unexpected');
const seckillDuration = new Trend('seckill_duration', true);

export const options = {
  scenarios: {
    seckill: {
      executor: 'shared-iterations',
      vus: users,
      iterations: users,
      maxDuration: '2m',
    },
  },
  thresholds: {
    seckill_accepted: [`count<=${expectedStock}`],
    seckill_duration: ['p(95)<1000'],
    seckill_unexpected: ['count==0'],
  },
};

export function setup() {
  const runId = Date.now().toString(36);
  const tokens = [];

  // 账号准备不计入秒杀接口延迟；不同用户避免只测到“单用户限流”。
  for (let index = 0; index < users; index += 1) {
    const username = `k6_${runId}_${index + 1}`;
    const registerResponse = http.post(
      `${baseUrl}/api/user/register`,
      JSON.stringify({ username, password, nickname: username }),
      { headers: { 'Content-Type': 'application/json' }, tags: { operation: 'setup-register' } },
    );
    if (registerResponse.status >= 400) {
      throw new Error(`register failed for ${username}: ${registerResponse.body}`);
    }

    const loginResponse = http.post(
      `${baseUrl}/api/user/login`,
      JSON.stringify({ username, password }),
      { headers: { 'Content-Type': 'application/json' }, tags: { operation: 'setup-login' } },
    );
    const body = loginResponse.json();
    const token = typeof body === 'string' ? body : body.data;
    if (loginResponse.status >= 400 || !token) {
      throw new Error(`login failed for ${username}: ${loginResponse.body}`);
    }
    tokens.push(token);
  }

  return { tokens };
}

export default function (data) {
  accepted.add(0);
  soldOut.add(0);
  repeated.add(0);
  unexpected.add(0);

  const tokenIndex = exec.scenario.iterationInTest;
  const response = http.post(`${baseUrl}/api/seckill/${activityId}`, null, {
    headers: { Authorization: `Bearer ${data.tokens[tokenIndex]}` },
    tags: { operation: 'seckill' },
  });
  seckillDuration.add(response.timings.duration);

  const text = response.body || '';
  if (response.status === 200 && text.trim() === 'true') {
    accepted.add(1);
  } else if (text.includes('库存不足')) {
    soldOut.add(1);
  } else if (text.includes('重复') || text.includes('参与过')) {
    repeated.add(1);
  } else {
    unexpected.add(1);
  }

  check(response, {
    'response is recognized': () => response.status === 200
      || text.includes('库存不足')
      || text.includes('重复')
      || text.includes('参与过'),
  });
}
