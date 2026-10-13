// 設定の保存、設定の下の3ページ、ふりかえり、PWA（manifest と Service Worker）を、統合した状態で、実際のブラウザで通して確かめる。
// 審査用アカウント（demo@example.com / demo-pass-123）で入る。画面の撮影は、SHOTS に出す。
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');
const BASE = process.env.YURUNE_BASE || 'http://localhost:18080';
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = Number(process.env.CHROME_DEBUG_PORT || 9361);
const SHOTS = process.env.SHOTS_DIR || require('os').tmpdir();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const check = (name, ok, detail) => results.push(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`);
const getJson = (url) => new Promise((resolve, reject) => {
  http.get(url, (res) => { let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve(JSON.parse(d))); }).on('error', reject);
});

(async () => {
  const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${PORT}`,
    '--window-size=430,900', '--user-data-dir=' + process.env.TEMP + '\\yurune-cdp-features-' + Date.now(), 'about:blank'], { stdio: 'ignore' });
  let targets;
  for (let i = 0; i < 40; i++) { try { targets = await getJson(`http://localhost:${PORT}/json`); if (targets.length) break; } catch (e) {} await sleep(250); }
  const ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0; const pending = new Map(); const loadWaiters = []; const violations = []; const scriptErrors = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
    if (msg.method === 'Page.loadEventFired') loadWaiters.splice(0).forEach((w) => w());
    if (msg.method === 'Log.entryAdded') { const e = msg.params.entry; if (/Content Security Policy|Refused to/i.test(e.text)) violations.push(e.text.slice(0, 160)); }
    if (msg.method === 'Runtime.exceptionThrown') scriptErrors.push(JSON.stringify(msg.params.exceptionDetails.exception && msg.params.exceptionDetails.exception.description).slice(0, 200));
  };
  const send = (method, params = {}) => new Promise((resolve) => { const i = ++id; pending.set(i, resolve); ws.send(JSON.stringify({ id: i, method, params })); });
  await send('Page.enable'); await send('Log.enable'); await send('Runtime.enable');
  const ev = async (expr) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
    return r.result.exceptionDetails ? 'EXC ' + JSON.stringify(r.result.exceptionDetails.exception && r.result.exceptionDetails.exception.description).slice(0, 200) : r.result.result.value; };
  const go = async (p) => { const l = new Promise((r) => loadWaiters.push(r)); await send('Page.navigate', { url: BASE + p }); await Promise.race([l, sleep(8000)]); await sleep(600); };
  const shot = async (name) => { const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(path.join(SHOTS, name), Buffer.from(r.result.data, 'base64')); };
  const text = () => ev('document.body.textContent');
  const switchState = (label) => ev(`(() => { const b = document.querySelector('button[role=switch][aria-label="${label}"]'); return b ? b.getAttribute('aria-checked') : 'missing'; })()`);

  // 審査用アカウントで入る
  await go('/login');
  await ev(`document.querySelector('[name=email]').value = 'demo@example.com'; document.querySelector('[name=password]').value = 'demo-pass-123'`);
  await ev(`document.querySelector('#action button').click()`);
  await sleep(1500);

  // 1 どの画面も開け、スクリプトの例外も CSP 違反もない
  const pages = ['/home', '/log', '/prepare', '/review', '/settings', '/settings/volume', '/settings/voice', '/settings/memory', '/settings/credits', '/settings/guide', '/settings/erase', '/terms', '/privacy'];
  for (const p of pages) {
    const status = await ev(`fetch(${JSON.stringify(BASE + p)}, { credentials: 'same-origin' }).then((r) => r.status)`);
    check(`${p} opens`, status === 200, String(status));
  }
  for (const p of ['/settings', '/review', '/settings/memory', '/settings/credits', '/settings/guide', '/prepare']) {
    await go(p);
    await shot('feat_' + p.replace(/\//g, '_') + '.png');
  }
  check('no script error and no CSP violation on those screens', scriptErrors.length === 0 && violations.length === 0, JSON.stringify({ scriptErrors, violations }));

  // 2 設定が保存され、読み直しても残る（夜の会話のスイッチ）
  await go('/settings');
  const before = await switchState('夜の会話');
  await ev(`document.querySelector('button[role=switch][aria-label="夜の会話"]').click()`);
  await sleep(1200);
  await go('/settings');
  const flipped = await switchState('夜の会話');
  check('the night-talk switch is saved and stays after reloading', before !== 'missing' && flipped !== before, `${before} -> ${flipped}`);
  await ev(`document.querySelector('button[role=switch][aria-label="夜の会話"]').click()`);
  await sleep(1200);
  await go('/settings');
  check('and it can be put back', (await switchState('夜の会話')) === before);

  // 2b 画面をつけたままにする、の設定も、同じしくみで保存される
  const keepBefore = await switchState('夜のあいだ、画面をつけたままにする');
  await ev(`document.querySelector('button[role=switch][aria-label="夜のあいだ、画面をつけたままにする"]').click()`);
  await sleep(1200);
  await go('/settings');
  const keepFlipped = await switchState('夜のあいだ、画面をつけたままにする');
  check('the keep-screen-on switch is saved and stays after reloading', keepBefore !== 'missing' && keepFlipped !== keepBefore, `${keepBefore} -> ${keepFlipped}`);
  await ev(`document.querySelector('button[role=switch][aria-label="夜のあいだ、画面をつけたままにする"]').click()`);
  await sleep(1200);

  // 3 設定の下の3ページは、仕様の固定文言と、本人のデータを出す
  await go('/settings/guide');
  check('guide shows the fixed wording N-02', (await text()).includes('眠っても大丈夫です。反応がなくなったら、静かに終わります。'));
  await go('/settings/memory');
  check('memory shows the fixed footer', (await text()).includes('会話の全文は保存しません。'));
  await go('/settings/credits');
  check('credits tells the truth about the sound sources', /外部の音源/.test(await text()));

  // 4 PWA
  const manifest = await ev(`fetch('/manifest.webmanifest').then(async (r) => ({ s: r.status, t: r.headers.get('content-type'), n: (await r.json()).name }))`);
  check('manifest is served as application/manifest+json', manifest.s === 200 && /manifest\+json/.test(manifest.t) && manifest.n === 'ゆるね', JSON.stringify(manifest));
  await go('/home');
  await sleep(1500);
  const sw = await ev(`navigator.serviceWorker.getRegistration().then((r) => (r && r.active ? r.active.state : 'none'))`);
  check('the service worker is registered and active', sw === 'activated', String(sw));
  const cached = await ev(`caches.keys().then(async (ks) => { const out = []; for (const k of ks) { const c = await caches.open(k); out.push(...(await c.keys()).map((q) => new URL(q.url).pathname)); } return out; })`);
  check('only the fixed static files are cached (no personal page)', Array.isArray(cached) && cached.every((p) => p === '/offline.html' || p === '/css/yurune.css'), JSON.stringify(cached));
  await go('/offline.html');
  await shot('feat_offline.png');

  console.log(results.join('\n'));
  console.log(results.some((r) => r.startsWith('FAIL')) ? 'SOME FAILED' : 'ALL PASS');
  chrome.kill();
  process.exit(results.some((r) => r.startsWith('FAIL')) ? 1 : 0);
})().catch((e) => { console.log('SCRIPT ERROR', e); process.exit(1); });
