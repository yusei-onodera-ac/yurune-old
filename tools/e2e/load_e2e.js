// 負荷: 高頻度の観測と画面の要求を送って、(1) LLM の呼び出しが上限の中に収まる、(2) 5xx が出ない、(3) 遅くならない、を確かめる。
// アプリは、擬似の Gemini（fake_google.js）につないで動かしてあり、夜の制御が LLM に聞いた回数を、擬似側が数える。
const BASE = 'http://localhost:18080';
const FAKE = 'http://127.0.0.1:18090';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const check = (name, ok, detail) => { const line = `${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`; results.push(line); console.log(line); };
const percentile = (values, p) => { const s = [...values].sort((a, b) => a - b); return s[Math.min(s.length - 1, Math.floor(s.length * p))]; };

class Session {
  constructor() { this.cookie = ''; this.token = ''; }
  async request(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (this.cookie) headers.Cookie = this.cookie;
    const response = await fetch(BASE + path, { redirect: 'manual', ...options, headers });
    const set = response.headers.getSetCookie();
    if (set.length) this.cookie = set.map((c) => c.split(';')[0]).join('; ');
    return response;
  }
  async refreshToken(path = '/login') {
    const html = await (await this.request(path)).text();
    this.token = (html.match(/name="csrf-token" content="([^"]+)"/) || [])[1];
  }
  post(path, body, extra = {}) {
    return this.request(path, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-Token': this.token, ...extra }, body });
  }
}

async function flood(count, concurrency, work) {
  const latencies = []; const statuses = {}; let next = 0;
  async function worker() {
    while (next < count) {
      next++;
      const started = Date.now();
      const status = await work();
      latencies.push(Date.now() - started);
      statuses[status] = (statuses[status] || 0) + 1;
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker));
  return { latencies, statuses };
}

(async () => {
  await fetch(FAKE + '/reset', { method: 'POST' });
  await fetch(FAKE + '/control?llm=up&armor=up&tts=up', { method: 'POST' });

  // 1 画面の要求（ログイン不要の稼働確認）に、たくさん送る
  const health = await flood(3000, 40, async () => (await fetch(BASE + '/healthz')).status);
  check('3000 health requests at concurrency 40: no error', Object.keys(health.statuses).join() === '200', JSON.stringify(health.statuses));
  check('health latency p95 stays small', percentile(health.latencies, 0.95) < 500, `p50=${percentile(health.latencies, 0.5)}ms p95=${percentile(health.latencies, 0.95)}ms`);

  // 2 ログインして、夜（音だけ）を始める
  const session = new Session();
  await session.refreshToken('/login');
  const login = await session.post('/login', 'email=demo%40example.com&password=demo-pass-123');
  check('login for the load test works', login.status === 302 && /\/home/.test(login.headers.get('location') || ''), String(login.status));
  await session.refreshToken('/prepare');
  const end = await session.post('/night/end', 'reason=user');
  const start = await session.post('/night', 'mode=sound');
  check('a night starts', start.status === 302, String(start.status));

  // 3 反応のない時間が 60 秒をこえるまで待つ（サーバーの時計で数える）。待つ間に、偽りの申告を送って、LLM を呼ばせようとする
  console.log('waiting 65 s for the server-side silence to pass 60 s ...');
  const spoof = await flood(200, 10, async () => (await session.post('/night/observe', 'lastReactionAgoSec=86000')).status);
  const early = await (await fetch(FAKE + '/state')).json();
  check('a flood of observations before 60 s of silence never reaches the model', early.agentCalls === 0, `agentCalls=${early.agentCalls}`);
  await sleep(65000);

  // 4 60 秒をこえてから、観測を、高頻度で送る
  const before = await (await fetch(FAKE + '/state')).json();
  const started = Date.now();
  const burst = await flood(1500, 30, async () => (await session.post('/night/observe', 'lastReactionAgoSec=86000')).status);
  const elapsedMs = Date.now() - started;
  const after = await (await fetch(FAKE + '/state')).json();
  const calls = after.agentCalls - before.agentCalls;
  check('1500 observations at concurrency 30: no 5xx', Object.keys(burst.statuses).every((s) => Number(s) < 500), JSON.stringify(burst.statuses));
  check('the model was called at most once per 20 s no matter how many observations came', calls <= Math.ceil(elapsedMs / 20000) + 1 && calls <= 2, `${calls} calls in ${elapsedMs} ms`);
  check('observe latency p95 stays small', percentile(burst.latencies, 0.95) < 1500, `p50=${percentile(burst.latencies, 0.5)}ms p95=${percentile(burst.latencies, 0.95)}ms`);

  // 5 不正な申告は、断る
  const bad = [];
  for (const value of ['-5', '999999999', 'abc', '']) bad.push((await session.post('/night/observe', 'lastReactionAgoSec=' + value)).status);
  check('invalid reported seconds are refused (400)', bad.every((s) => s === 400), JSON.stringify(bad));

  // 6 会話: 続けて送っても、日次と間隔の上限の中に収まる
  await session.post('/night/mode', 'mode=talk');
  await fetch(FAKE + '/reset', { method: 'POST' });
  const talkStarted = Date.now();
  const talk = await flood(300, 20, async () => (await session.request('/night/talk', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': session.token }, body: JSON.stringify({ text: 'おはなししよう', history: [] }) })).status);
  const talkState = await (await fetch(FAKE + '/state')).json();
  check('300 talk requests at concurrency 20: no 5xx', Object.keys(talk.statuses).every((s) => Number(s) < 500), JSON.stringify(talk.statuses));
  const talkElapsedMs = Date.now() - talkStarted;
  check('the model was called at most once per 2 s for talk, however many requests came', talkState.llmCalls <= Math.ceil(talkElapsedMs / 2000) + 1, `${talkState.llmCalls} calls in ${talkElapsedMs} ms`);

  // 7 後始末
  await session.post('/night/end', 'reason=user');
  console.log(results.filter((r) => r.startsWith('FAIL')).length === 0 ? 'ALL PASS' : 'SOME FAILED');
  process.exit(results.some((r) => r.startsWith('FAIL')) ? 1 : 0);
})().catch((e) => { console.log('SCRIPT ERROR', e); process.exit(1); });
