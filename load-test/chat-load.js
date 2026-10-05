// 챗봇 질의 API 부하 테스트.
//
// 확인하려는 것: AI 서버가 질문을 동시에 처리하는가, 한 번에 하나씩 처리하는가.
//
// polight-ai 의 /internal/rag/query 는 async def 안에서 동기 OpenAI 호출을 한다
// (app/api/routes/rag.py 의 query_policy -> answer_question, await 없음). uvicorn 워커도
// 1개라, 그렇다면 이벤트 루프가 질문 하나마다 통째로 멈춘다.
//
// 직렬이면 VU 를 올려도 처리량(http_reqs/s)이 평평하고 지연만 선형으로 는다.
// 병렬인데 그냥 느린 것이면 처리량이 VU 에 비례해 늘고 지연은 평평하다.
// 이 두 그래프의 대비가 판정 근거다 -- 지연 하나만 보면 둘을 구분할 수 없다.
//
// 실행:
//   BASE_URL=... TOKEN=... VUS=1  DURATION=3m k6 run load-test/chat-load.js   # 기준값(S)
//   BASE_URL=... TOKEN=... VUS=5  DURATION=3m k6 run load-test/chat-load.js
//   BASE_URL=... TOKEN=... VUS=10 DURATION=3m k6 run load-test/chat-load.js

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;
const TRIP_ID = __ENV.TRIP_ID || '';
const VUS = Number(__ENV.VUS || 1);
const DURATION = __ENV.DURATION || '3m';

// 처음 몇 번은 버린다. 대화 이력이 비어 있어 프롬프트가 짧고(서버가 최근 6턴을 실어 보낸다),
// JIT·커넥션 워밍업도 섞여 있어 정상 상태의 값이 아니다.
const WARMUP_ITERATIONS = 2;

// 백엔드가 AI 응답을 60초까지 기다린다(ai.server.rag-read-timeout). 연결 계열 예외는 1회
// 재시도하므로 최대 120초까지 늘어날 수 있다. k6 기본 타임아웃(60초)을 그대로 두면 백엔드가
// 아직 기다리는 중인데 k6 가 먼저 끊어, 정작 보려던 "언제 터지는가"를 못 본다.
const REQUEST_TIMEOUT = '130s';

// 약관이 연결되지 않으면 백엔드가 AI 를 부르지 않고 이 문장을 즉시 돌려준다
// (ChatQueryService.NO_TERMS_ANSWER). 수 ms 에 끝나므로 측정에 섞이면 p95 가 통째로 왜곡된다.
const NO_TERMS_ANSWER =
  '가입하신 증권에 연결된 약관을 찾지 못해 약관 근거를 확인할 수 없습니다. 증권을 등록했는지 확인해 주세요.';

// AI 를 실제로 태운 질의의 지연. 이것만이 측정 대상이다.
const aiLatency = new Trend('ai_latency', true);
// AI 를 태워 답을 받은 횟수. 처리량(= 이 값 / 측정 시간)이 직렬 여부를 가르는 핵심 지표라
// 직접 센다. k6 의 http_reqs 에는 대조군이 섞여 있어 빼기를 해야 하고, 대조군이 설정한
// 속도를 못 채우면 그 빼기가 틀어진다.
const aiRequests = new Counter('ai_requests');
// 워밍업을 포함한 전체 지연.
//
// ai_latency 는 앞 몇 건을 버리는데, 포화 구간에서는 성공 자체가 드물어 그 몇 건이 전부일 수
// 있다. 실제로 VU=4 측정에서 성공 6건이 전원 워밍업 구간에 들어가 ai_latency 가 통째로
// 비었다. 버리지 않은 값도 같이 남겨 그런 구간에서도 읽을 것이 있게 한다.
const aiLatencyAll = new Trend('ai_latency_all', true);
// AI 를 안 탄 응답. 0 이 아니면 테스트 대상 여행에 약관이 안 붙은 것이라 측정 자체가 무의미하다.
const noTermsCount = new Counter('ai_skipped_no_terms');
const aiTimeouts = new Counter('ai_timeouts');
const aiServerErrors = new Counter('ai_server_errors');
// 대조군. AI 를 타지 않는 읽기 전용 API 다.
const controlLatency = new Trend('control_latency', true);

const QUESTIONS = [
  '항공편이 지연되면 보상되나요?',
  '해외에서 병원에 가면 얼마까지 보장되나요?',
  '휴대품을 도난당하면 어떻게 청구하나요?',
  '자기부담금이 얼마인가요?',
  '보장되지 않는 경우는 어떤 것이 있나요?',
  '치과 치료도 보장되나요?',
  '여행 중 사고가 나면 필요한 서류가 무엇인가요?',
  '배상책임은 얼마까지 보장되나요?',
];

export const options = {
  scenarios: {
    // 본 측정. AI 를 태우는 질의를 VUS 명이 동시에 던진다.
    chat: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'askQuestion',
    },
    // 대조군. AI 를 타지 않는 읽기 API 를 낮은 빈도로 계속 때린다.
    //
    // 이것까지 같이 느려지면 AI 서버의 이벤트 루프가 막힌 것이다(= 가설 확정).
    // 챗봇만 느려지면 원인이 다른 데 있다. 가설을 가르는 지표라 꼭 같이 돌린다.
    control: {
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '2s',
      duration: DURATION,
      preAllocatedVUs: 2,
      exec: 'readHistory',
    },
  },
  // 임계값으로 실패시키지 않는다. 터지는 지점을 보려는 테스트라 빨간 글씨는 결과이지 오류가 아니다.
  thresholds: {},
};

function authHeaders() {
  return {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json',
  };
}

// 토큰과 여행을 확인한다. 여기서는 질문을 보내지 않으므로 LLM 비용이 들지 않는다.
export function setup() {
  if (!TOKEN) {
    throw new Error('TOKEN 환경변수가 필요합니다. (예: TOKEN=eyJ... k6 run ...)');
  }

  let tripId = TRIP_ID;
  if (!tripId) {
    const res = http.get(`${BASE_URL}/api/v1/trips`, { headers: authHeaders() });
    if (res.status !== 200) {
      throw new Error(`여행 목록 조회 실패 (${res.status}). 토큰이 만료됐을 수 있습니다: ${res.body}`);
    }
    const trips = res.json();
    if (!trips || trips.length === 0) {
      throw new Error('여행이 하나도 없습니다. 증권을 올린 여행을 먼저 만들어 주세요.');
    }
    tripId = trips[0].id;
  }

  // 이력 조회로 토큰·여행 접근이 되는지만 본다.
  const history = http.get(`${BASE_URL}/api/v1/trips/${tripId}/chat/messages`, {
    headers: authHeaders(),
  });
  if (history.status !== 200) {
    throw new Error(`대화 이력 조회 실패 (${history.status}): ${history.body}`);
  }

  console.log(`대상 여행: ${tripId} / 동시 사용자: ${VUS} / 측정 시간: ${DURATION}`);
  return { tripId };
}

export function askQuestion(data) {
  const question = QUESTIONS[(__VU + __ITER) % QUESTIONS.length];

  const res = http.post(
    `${BASE_URL}/api/v1/trips/${data.tripId}/chat/messages`,
    JSON.stringify({ question }),
    { headers: authHeaders(), timeout: REQUEST_TIMEOUT, tags: { name: 'chat_ask' } },
  );

  check(res, { '응답 200': (r) => r.status === 200 });

  if (res.status === 0) {
    // k6 쪽 타임아웃. REQUEST_TIMEOUT 을 넘겼다는 뜻이다.
    aiTimeouts.add(1);
    return;
  }
  if (res.status >= 500) {
    // 백엔드가 AI 응답을 못 받아 502 로 내려보낸 경우가 여기 들어온다.
    aiServerErrors.add(1);
    return;
  }
  if (res.status !== 200) {
    return;
  }

  const answer = (res.json('answer') || res.json('content') || '').toString();
  if (answer.indexOf(NO_TERMS_ANSWER) !== -1) {
    // AI 를 안 탄 응답이라 측정에서 뺀다.
    noTermsCount.add(1);
    return;
  }

  // 처리량은 워밍업 포함 전부 센다. "이 시간에 몇 건을 처리했는가"가 질문이므로
  // 앞 몇 건을 빼면 분모와 분자가 어긋난다.
  aiRequests.add(1);
  aiLatencyAll.add(res.timings.duration);

  if (__ITER >= WARMUP_ITERATIONS) {
    aiLatency.add(res.timings.duration);
  }
}

// 대조군: AI 를 타지 않는 읽기 전용 API.
export function readHistory(data) {
  const res = http.get(`${BASE_URL}/api/v1/trips/${data.tripId}/chat/messages?limit=1`, {
    headers: authHeaders(),
    timeout: '30s',
    tags: { name: 'control_history' },
  });
  check(res, { '대조군 200': (r) => r.status === 200 });
  if (res.status === 200) {
    controlLatency.add(res.timings.duration);
  }
}
