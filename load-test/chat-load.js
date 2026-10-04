// 챗봇 질문 API 부하 테스트.
//
// 같은 여행에 VUS 명이 DURATION 동안 쉬지 않고 질문한다. 응답 시간이 사용자 수에 비례해 늘어나는지 보는 것이 목적이다.
// 실행 방법과 측정 순서는 load-test/README.md 에 있다.
//
//   k6 run \
//     -e BASE_URL=http://<EC2 주소> -e TOKEN=<액세스 토큰> -e TRIP_ID=<여행 id> \
//     -e VUS=5 -e DURATION=20s \
//     load-test/chat-load.js
//
// TOKEN 은 이 파일에 적지 않는다. 커밋되면 그 토큰으로 남의 계정처럼 API 를 부를 수 있다.

import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
// handleSummary 를 정의하면 k6 기본 요약이 사라진다. 기본 요약도 함께 보려고 k6 공식 라이브러리를 불러 쓴다.
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = requireEnv('BASE_URL').replace(/\/$/, '');
const TOKEN = requireEnv('TOKEN');
const TRIP_ID = requireEnv('TRIP_ID');

const VUS = Number(__ENV.VUS || 1);
const DURATION = __ENV.DURATION || '20s';

// 수정 전후로 같은 문장을 쓴다. 답변 길이가 달라지면 응답 시간도 달라져 비교가 흐려진다.
const QUESTION = __ENV.QUESTION || '여행 중 휴대품을 도난당하면 보상받을 수 있나요?';

// 백엔드의 rag-read-timeout 은 60초지만, 읽기 타임아웃도 연결 실패(ResourceAccessException)로 분류되어 한 번 더
// 시도한다(FastApiRagQueryClient#query). 그래서 백엔드가 502 를 돌려주기까지 최대 약 120초가 걸린다. k6 기본값(60초)으로
// 두면 백엔드가 아직 기다리는 요청을 k6 가 먼저 끊어, 실패 원인이 "백엔드가 포기함"인지 "k6 가 끊음"인지 구분되지 않는다.
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '150s';

// 약관이 없는 여행이면 백엔드가 AI 를 부르지 않고 이 문장으로 즉답한다(ChatQueryService.NO_TERMS_ANSWER).
// 그 응답은 수십 ms 라 섞이면 평균이 왜곡된다. 따로 세어 둔다.
const NO_TERMS_ANSWER_PREFIX = '가입하신 증권에 연결된 약관을 찾지 못해';

const noTermsAnswers = new Counter('no_terms_answers');

export const options = {
  scenarios: {
    chat: {
      // 정해진 인원이 응답을 받자마자 다음 질문을 보낸다. "동시에 N명이 기다리는 상태"를 유지하는 방식이다.
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      // 마지막 요청이 끝날 때까지 기다린다. 기본 30초면 오래 걸린 요청이 집계에서 빠져 결과가 실제보다 좋아 보인다.
      gracefulStop: REQUEST_TIMEOUT,
    },
  },
  // 합격선이 아니라 표시용이다. 넘어도 측정은 끝까지 하고, 종료 코드만 실패로 남는다.
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<60000'],
    no_terms_answers: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'max'],
};

export function setup() {
  // 질문을 보내지 않고 이력 조회로만 확인한다. 질문은 OpenAI 비용이 들고 채팅 이력도 남는다.
  const res = http.get(`${BASE_URL}/api/v1/trips/${TRIP_ID}/chat/messages?limit=1`, {
    headers: authHeaders(),
  });

  if (res.status === 401) {
    fail('401: 토큰이 없거나 만료됐습니다. 다시 로그인해 받으세요.');
  }
  if (res.status === 404) {
    fail('404: 여행이 없거나 이 토큰 사용자의 여행이 아닙니다. TRIP_ID 를 확인하세요.');
  }
  if (res.status !== 200) {
    fail(`사전 확인 실패: status=${res.status}, body=${res.body}`);
  }

  console.log(`대상 ${BASE_URL}, 여행 ${TRIP_ID}, ${VUS}명 × ${DURATION}`);
}

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/trips/${TRIP_ID}/chat/messages`,
    JSON.stringify({ question: QUESTION }),
    {
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      timeout: REQUEST_TIMEOUT,
      tags: { name: 'chat_ask' },
    },
  );

  const answer = res.status === 200 ? res.json('answer') : null;
  const withoutTerms = typeof answer === 'string' && answer.startsWith(NO_TERMS_ANSWER_PREFIX);
  if (withoutTerms) {
    noTermsAnswers.add(1);
  }

  check(res, {
    'status 200': (r) => r.status === 200,
    'AI 가 답함 (약관 없음 즉답 아님)': () => res.status === 200 && !withoutTerms,
  });

  // 질문 사이에 쉬지 않는다(sleep 없음). 측정하려는 것은 "N명이 동시에 기다릴 때"이고, 쉬는 시간을 넣으면 실제 동시 요청
  // 수가 N보다 줄어든다.
}

export function handleSummary(data) {
  const duration = data.metrics.http_req_duration.values;
  const failed = data.metrics.http_req_failed.values.rate;
  const rps = data.metrics.http_reqs.values.rate;
  const seconds = (ms) => `${(ms / 1000).toFixed(1)}s`;

  // README 결과 표에 그대로 붙여 넣을 한 줄.
  const row = `| ${VUS} | ${seconds(duration.avg)} | ${seconds(duration['p(95)'])} | ${(failed * 100).toFixed(1)}% | ${rps.toFixed(2)} |`;

  return {
    stdout:
      textSummary(data) +
      '\n\nREADME 결과 표에 붙여 넣을 줄 (vus | 평균 | p95 | 실패율 | 처리량):\n' +
      row +
      '\n',
  };
}

function authHeaders() {
  return { Authorization: `Bearer ${TOKEN}` };
}

function requireEnv(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`환경 변수 ${name} 가 필요합니다. 예: -e ${name}=...`);
  }
  return value;
}
