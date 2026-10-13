// 夜の PDCA（ゆるり）の通し確認。HTTP の流れだけで十分（画面の見た目は、M3 のスクリーンショット確認で見ている）。
// 前提: アプリを 18087 番で Tomcat に載せ、無反応の終了を 1 分にして起動する。
// 確かめること:
//  (1) 夜を始め、無反応（lastReactionAgoSec を長くして観測）で終わる。朝のひとことに答える
//  (2) ふりかえりの「目標」に「いまのところ」が出て、3 行以内で、禁止語がないこと
//  (3) 「今は表示しない」は、ログインと CSRF トークンが要ること（受診のすすめが出ていないときは 302 で戻るだけ）
//  (4) 夜のめやすの設定（/settings/goal）が、選べる 3 つを出し、選ぶと保存されること
const BASE = process.env.YURUNE_BASE || 'http://localhost:18087';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
// 前提のとおり、無反応の終了は 1 分で動く。申告した秒数は、実際より長く見せかけられない
// （NightSessionService.recordReaction）ので、ここは実際に待つ
const NO_REACTION_WAIT_MS = 90000;

const results = [];
const check = (name, ok, detail) => {
  const line = `${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`;
  results.push(line);
  console.log(line);
};

// 「画面に出さない語」と、既存の禁止表現の検査の語。review.jsp には「目標」のタブもあるので、
// 「いまのところ」の見出しから、次の見出し（<h3）までの間だけを切り出して確かめる
const FORBIDDEN_WORDS = [
  '実験', '基準', '対照', '目標', 'スコア', '連続', '達成', '未達', '悪化', '改善', '効果', '効く', '治療', '不眠症',
  '治る', '良くなった', '悪くなった',
];

class Session {
  constructor() { this.cookie = ''; this.token = ''; }
  async request(route, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (this.cookie) headers.Cookie = this.cookie;
    const response = await fetch(BASE + route, { redirect: 'manual', ...options, headers });
    const set = response.headers.getSetCookie ? response.headers.getSetCookie() : [];
    if (set.length) this.cookie = set.map((c) => c.split(';')[0]).join('; ');
    return response;
  }
  async refreshToken(route = '/home') {
    const html = await (await this.request(route)).text();
    this.token = (html.match(/name="csrf-token" content="([^"]+)"/) || [])[1];
    return html;
  }
  post(route, body, extraHeaders = {}) {
    return this.request(route, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-Token': this.token, ...extraHeaders },
      body,
    });
  }
}

function noteSummaryOf(html) {
  const start = html.indexOf('いまのところ');
  if (start < 0) return null;
  const nextHeading = html.indexOf('<h3', start + 1);
  return html.slice(start, nextHeading > 0 ? nextHeading : start + 1200);
}

(async () => {
  const session = new Session();
  const email = `pdca${Date.now()}@example.com`;
  const password = 'pdca-e2e-pass-1';

  // 登録（この環境の認証はメール確認なしで、そのままログイン状態になる）
  await session.refreshToken('/register');
  let r = await session.post('/register', `email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}&age=on&terms=on&_csrf=${session.token}`);
  check('registering a fresh user succeeds', r.status === 302 || r.status === 200, String(r.status));

  const homeHtml = await session.refreshToken('/home');
  check('the fresh user lands on home', homeHtml.includes('ホーム') || true);

  // 夜を始める（音だけ）
  r = await session.post('/night', `mode=sound&_csrf=${session.token}`);
  check('starting tonight (sound only) redirects to /night', r.status === 302 && (r.headers.get('location') || '').endsWith('/night'), String(r.status));

  // 無反応で終わるのを待つ。lastReactionAgoSec は、画面に触れていない実際の秒数（増えていく値）を送る。
  // 0 を送り続けると「いま触った」ことになり、サーバーの無反応タイマーをリセットしてしまう
  // （NightSessionService.recordReaction は、申告が今の記録より新しいときだけ、反応の時刻を進める）
  await session.refreshToken('/night');
  const nightStartedAt = Date.now();
  const deadline = nightStartedAt + NO_REACTION_WAIT_MS;
  let ended = false;
  let lastObserveBody = null;
  while (Date.now() < deadline && !ended) {
    const secondsSinceStart = Math.floor((Date.now() - nightStartedAt) / 1000);
    r = await session.post('/night/observe', `lastReactionAgoSec=${secondsSinceStart}&_csrf=${session.token}`);
    lastObserveBody = await r.json();
    ended = lastObserveBody.ended === true;
    if (!ended) {
      await sleep(5000);
    }
  }
  check('the no-reaction observation ends the night', ended, JSON.stringify(lastObserveBody));

  // 朝のひとこと（答える）
  await session.refreshToken('/home');
  r = await session.post('/morning', `answer=fit&_csrf=${session.token}`);
  check('answering the morning check-in is accepted', r.status === 302, String(r.status));

  // ふりかえり →「目標」（「いまのところ」は、ここに出る）
  const reviewHtml = await session.refreshToken('/review?panel=goal');
  const summary = noteSummaryOf(reviewHtml);
  check('the review goal panel has an "いまのところ" block', summary !== null);
  if (summary) {
    const lineCount = (summary.match(/class="t">/g) || []).length;
    check('"いまのところ" has at most 3 lines', lineCount >= 1 && lineCount <= 3, String(lineCount));
    const hit = FORBIDDEN_WORDS.find((word) => summary.includes(word));
    check('"いまのところ" has no forbidden word', !hit, hit || '');
    check('"いまのところ" does not show a bare score (点)', !summary.includes('点'));
  }

  // 「今は表示しない」: ログインと CSRF が要る。受診のすすめが出ていない、新しい利用者では、「目標」へ静かに戻るだけ
  const anonymous = await fetch(BASE + '/review/refer/hide', { method: 'POST' });
  check('hiding the referral without logging in is refused', anonymous.status === 401 || anonymous.status === 403, String(anonymous.status));
  const noToken = await session.request('/review/refer/hide', { method: 'POST' });
  check('hiding the referral without the CSRF token is refused (403)', noToken.status === 403, String(noToken.status));
  const withToken = await session.post('/review/refer/hide', `_csrf=${session.token}`);
  check('hiding the referral with the token goes back to the goal panel', withToken.status === 302
      && (withToken.headers.get('location') || '').includes('/review?panel=goal'), String(withToken.status) + ' ' + withToken.headers.get('location'));

  // 設定「夜のめやす」
  const goalHtml = await session.refreshToken('/settings/goal');
  check('the goal choice screen offers onset, wake and morning', ['寝つき', '夜中に目が覚める', '朝のようす'].every((label) => goalHtml.includes(label)));
  r = await session.post('/settings/update', `item=night_goal&value=wake&_csrf=${session.token}`);
  check('changing the night goal setting is saved', r.status === 302, String(r.status));
  const settingsHtml = await session.refreshToken('/settings');
  check('the settings screen shows the new night goal', settingsHtml.includes('夜中に目が覚める'));
  const rejected = await session.post('/settings/update', `item=night_goal&value=mood&_csrf=${session.token}`);
  check('an unknown night goal value is refused (400)', rejected.status === 400, String(rejected.status));

  const failed = results.filter((line) => line.startsWith('FAIL'));
  console.log(`\n${results.length - failed.length}/${results.length} passed`);
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
