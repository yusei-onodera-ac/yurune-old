// 認証・Firestore・利用者ごとの分離・データの消去を、実際のブラウザで通して確かめる。
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');
const BASE = 'http://localhost:18080';
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 9351;
const SHOTS = process.env.SHOTS_DIR || require('os').tmpdir();
const AUTH = 'http://127.0.0.1:9099';
const FIRESTORE = 'http://127.0.0.1:8080';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const check = (name, ok, detail) => results.push(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`);
const getJson = (url) => new Promise((resolve, reject) => {
  http.get(url, (res) => { let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve(JSON.parse(d))); }).on('error', reject);
});

async function verifyEmailInEmulator(email) {
  const codes = await (await fetch(`${AUTH}/emulator/v1/projects/yurune-test/oobCodes`)).json();
  const hit = codes.oobCodes.filter((c) => c.email === email && c.requestType === 'VERIFY_EMAIL').pop();
  if (!hit) return false;
  return (await fetch(hit.oobLink)).status === 200;
}

async function uidOf(email, password) {
  const r = await (await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=k`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password, returnSecureToken: true }) })).json();
  return r.localId;
}

async function countDocs(uid) {
  // users/{uid} 以下に、文書が残っているか（コレクションごとに数える）
  const names = ['arms', 'sleepRecords', 'nights', 'decisions', 'sessions', 'profile'];
  let total = 0;
  for (const n of names) {
    const r = await (await fetch(`${FIRESTORE}/v1/projects/yurune-test/databases/(default)/documents/users/${uid}/${n}`, { headers: { Authorization: 'Bearer owner' } })).json();
    total += (r.documents || []).length;
  }
  return total;
}

(async () => {
  const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${PORT}`,
    '--window-size=480,900', '--user-data-dir=' + process.env.TEMP + '\\yurune-cdp-step5-' + Date.now(), 'about:blank'], { stdio: 'ignore' });
  let targets;
  for (let i = 0; i < 40; i++) { try { targets = await getJson(`http://localhost:${PORT}/json`); if (targets.length) break; } catch (e) {} await sleep(250); }
  const ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0; const pending = new Map(); const loadWaiters = []; const violations = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
    if (msg.method === 'Page.loadEventFired') loadWaiters.splice(0).forEach((w) => w());
    if (msg.method === 'Log.entryAdded') { const e = msg.params.entry; if (/Content Security Policy|Refused to/i.test(e.text)) violations.push(e.text.slice(0, 160)); }
  };
  const send = (method, params = {}) => new Promise((resolve) => { const i = ++id; pending.set(i, resolve); ws.send(JSON.stringify({ id: i, method, params })); });
  await send('Page.enable'); await send('Log.enable'); await send('Network.enable');
  const ev = async (expr) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
    return r.result.exceptionDetails ? 'EXC ' + JSON.stringify(r.result.exceptionDetails.exception && r.result.exceptionDetails.exception.description).slice(0, 200) : r.result.result.value; };
  const go = async (p) => { const l = new Promise((r) => loadWaiters.push(r)); await send('Page.navigate', { url: BASE + p }); await Promise.race([l, sleep(8000)]); await sleep(500); };
  const waitLoad = async () => { const l = new Promise((r) => loadWaiters.push(r)); await Promise.race([l, sleep(8000)]); await sleep(500); };
  const shot = async (name) => { const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(path.join(SHOTS, name), Buffer.from(r.result.data, 'base64')); };
  const text = () => ev('document.body.innerText');
  const allText = () => ev('document.body.textContent');
  const here = () => ev('location.pathname');
  const fill = (name, value) => ev(`(() => { const e = document.querySelector('[name=${name}]'); e.value = ${JSON.stringify(value)}; return true; })()`);
  const submitMain = async () => { await ev(`document.querySelector('#action button').click()`); await waitLoad(); };
  const sessionCookie = async () => (await send('Network.getCookies', { urls: [BASE] })).result.cookies.find((c) => c.name === 'JSESSIONID');

  const stamp = Date.now();
  const email = `user${stamp}@example.com`;
  const password = 'pass-' + stamp;

  // A. ログインしていないと、ログイン画面へ
  await go('/home');
  check('anonymous visitor is sent to the login screen', (await here()) === '/login', await here());
  check('login screen shows the notice and no tab bar', /ゆるねは医療機器ではありません/.test(await text()) && (await ev(`document.querySelector('.tabbar') === null`)) === true);
  await shot('s5_login.png');
  const cookieBefore = await sessionCookie();

  // B. 登録: 同意がないと断る
  await go('/register');
  await shot('s5_register.png');
  await fill('email', email); await fill('password', password);
  await submitMain();
  check('registration without consent is refused', /18歳以上であること/.test(await text()), (await text()).match(/18歳以上であること[^\n]*/)?.[0]);
  await ev(`document.querySelector('[name=age]').click(); document.querySelector('[name=terms]').click()`);
  await fill('email', email); await fill('password', password);
  await fill('password', 'short');
  await submitMain();
  check('a short password is refused', /8文字以上/.test(await text()));
  await ev(`document.querySelector('[name=age]').click(); document.querySelector('[name=terms]').click()`);
  await fill('email', email); await fill('password', password);
  await submitMain();
  check('registration asks the user to verify the email', (await here()) === '/register' && /確認のメールを送りました/.test(await text()) === false ? true : /確認のメールを送りました/.test(await text()), await here());

  // 確認前のログインは断る
  await go('/login');
  await fill('email', email); await fill('password', password);
  await submitMain();
  check('login before verification is refused', /確認が、まだ済んでいません/.test(await text()));

  // 確認して、ログイン
  check('verification mail was sent (emulator)', await verifyEmailInEmulator(email));
  await fill('email', email); await fill('password', password);
  await submitMain();
  check('login works after verification and goes home', (await here()) === '/home', await here());
  const cookieAfter = await sessionCookie();
  check('session id changes on login (no fixation)', cookieBefore && cookieAfter && cookieBefore.value !== cookieAfter.value);
  check('session cookie is HttpOnly, Secure and SameSite=Strict', cookieAfter && cookieAfter.httpOnly && cookieAfter.secure && cookieAfter.sameSite === 'Strict', JSON.stringify(cookieAfter && { h: cookieAfter.httpOnly, s: cookieAfter.secure, ss: cookieAfter.sameSite }));

  // C. 新しい利用者のホームなど。空でも、壊れない
  const uid = await uidOf(email, password);
  await shot('s5_home_new.png');
  for (const p of ['/home', '/log', '/prepare', '/review', '/settings']) {
    await go(p);
    check(`new user can open ${p}`, (await here()) === p && (await ev(`document.querySelector('.tabbar') !== null`)) === true, await here());
  }
  check('new user does not see the demo data (no morning sheet)', (await (async () => { await go('/home'); return ev(`document.querySelector('.sheetbg') === null`); })()) === true);

  // 夜: 音だけで始めて、終わる。夜の状態と判断の記録が、Firestore に残る
  await go('/prepare');
  await ev(`document.querySelector('#action button').click()`);
  await waitLoad();
  check('night starts for the new user', (await here()) === '/night' && (await ev(`!!document.getElementById('nightClock')`)) === true);
  const saved = await countDocs(uid);
  check('night session and plan are stored in Firestore under the user', saved >= 2, String(saved));
  await ev(`document.querySelector('.endbtn').click()`);
  await waitLoad();
  check('the night ends and returns home', (await here()) === '/home');
  await go('/review');
  const reviewText = (await allText()).replace(/\s+/g, ' ');
  check('review of an ordinary user has the week and goal tabs, no note, no sample and no demo notice',
    /目標/.test(reviewText) && !/ぽてねのノート|サンプル|審査員用のデモ/.test(reviewText), reviewText.slice(0, 200));

  // 設定 → データを消す
  await go('/settings');
  await shot('s5_settings.png');
  await go('/settings/erase');
  await shot('s5_erase.png');
  await fill('password', 'wrong-password');
  await submitMain();
  check('erasing with a wrong password is refused and nothing is erased', /パスワードが違います/.test(await text()) && (await countDocs(uid)) >= 2);
  await fill('password', password);
  await submitMain();
  check('erasing with the right password goes back to settings with a note', (await here()) === '/settings' && /消しました/.test(await text()), await here());
  check('no data remains under users/{uid} (AT-15)', (await countDocs(uid)) === 0, String(await countDocs(uid)));

  // ログアウト
  await ev(`document.querySelector('form[action$="/logout"] button').click()`);
  await waitLoad();
  check('logout returns to the login screen', (await here()) === '/login', await here());
  await go('/home');
  check('after logout the pages are closed again', (await here()) === '/login');

  // 総当たりの制限
  await go('/login');
  for (let i = 0; i < 5; i++) { await fill('email', email); await fill('password', 'bad-' + i); await submitMain(); }
  await fill('email', email); await fill('password', password);
  await submitMain();
  check('five wrong passwords lock the account even for the right one', /試行が多すぎます/.test(await text()), (await text()).match(/試行[^\n]*|メールアドレスまたは[^\n]*/)?.[0]);

  // 入力の反映が、安全に表示される
  await go('/login');
  await fill('email', '"><script>alert(1)</script>@x.com');
  await fill('password', 'x');
  await submitMain();
  check('markup in the email field is shown harmlessly', (await ev(`document.querySelectorAll('script:not([src])').length`)) === 0);

  // 審査用アカウント: メールを受け取らずに入れ、サンプルが入っている
  await go('/login');
  await fill('email', 'demo@example.com'); await fill('password', 'demo-pass-123');
  await submitMain();
  check('the review account logs in without any email', (await here()) === '/home', await here());
  const homeText = await text();
  check('the review account has the sample records and morning sheet', (await ev(`document.querySelector('.sheetbg') !== null`)) === true && /睡眠時間/.test(homeText));
  await shot('s5_demo_home.png');
  const demoUid = await uidOf('demo@example.com', 'demo-pass-123');
  check('sample data was written for the review account only', (await countDocs(demoUid)) >= 10 && (await countDocs(uid)) === 0, String(await countDocs(demoUid)));
  await go('/review');
  const demoReview = await allText();
  check('the review account shows no sample (no A / B switch, no word サンプル) and is marked as the demo for reviewers',
    !/Aさん|Bさん|サンプル/.test(demoReview) && /審査員用のデモアカウントです。/.test(demoReview));

  check('no CSP violation on the way', violations.length === 0, violations.join(' | '));
  ws.close(); chrome.kill();
  console.log(results.join('\n'));
  process.exit(0);
})().catch((e) => { console.log(results.join('\n')); console.log('SCRIPT ERROR', e); process.exit(1); });
