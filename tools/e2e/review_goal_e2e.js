// ふりかえりの「目標」のタブ（実験の区分を外して置き換えた）。タブは 週 / 目標 の2つ（ぽてねのノートは外した。2026-10-12）で、実験の語がどこにもないこと。「いまのところ」と受診のすすめは「目標」の中。審査員用のデモ表示。
// 前提: アプリが 18095 番（メモリのプロファイル。審査用アカウント demo@example.com / demo-pass-123）。
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const BASE = process.env.YURUNE_BASE || 'http://localhost:18095';
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const DEBUG_PORT = Number(process.env.CHROME_DEBUG_PORT || 9415);
const OUT = process.env.SHOT_DIR || require('os').tmpdir();
const WIDTH = 430;
const HEIGHT = 900;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const check = (name, ok, detail) => results.push(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`);
const getJson = (url) => new Promise((resolve, reject) => {
  http.get(url, (res) => { let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve(JSON.parse(d))); }).on('error', reject);
});

(async () => {
  const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${DEBUG_PORT}`,
    `--window-size=${WIDTH},${HEIGHT}`, '--user-data-dir=' + process.env.TEMP + '\\yurune-w15-' + Date.now(), 'about:blank'], { stdio: 'ignore' });
  let targets = [];
  for (let i = 0; i < 40; i++) { try { targets = await getJson(`http://localhost:${DEBUG_PORT}/json`); if (targets.length) break; } catch (e) { /* wait */ } await sleep(250); }
  const ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0; const pending = new Map(); const loadWaiters = []; const exceptions = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
    if (msg.method === 'Page.loadEventFired') loadWaiters.splice(0).forEach((w) => w());
    if (msg.method === 'Runtime.exceptionThrown') exceptions.push(JSON.stringify(msg.params.exceptionDetails.text + ' ' + ((msg.params.exceptionDetails.exception || {}).description || '')));
    if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') exceptions.push(msg.params.entry.text);
  };
  const send = (method, params = {}) => new Promise((resolve) => { const i = ++id; pending.set(i, resolve); ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async (expr) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true }); return r.result.exceptionDetails ? 'EXC' : r.result.result.value; };
  const go = async (p) => { const l = new Promise((r) => loadWaiters.push(r)); await send('Page.navigate', { url: BASE + p }); await Promise.race([l, sleep(8000)]); await sleep(700); };
  const shot = async (name) => { const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(path.join(OUT, name + '.png'), Buffer.from(r.result.data, 'base64')); };
  const theme = (scheme) => send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: scheme }] });
  const visibleText = () => ev(`document.body.innerText`);

  await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: WIDTH, height: HEIGHT, deviceScaleFactor: 2, mobile: true });

  // ログイン
  await go('/login');
  await ev(`document.querySelector('[name=email]').value='demo@example.com'; document.querySelector('[name=password]').value='demo-pass-123'`);
  const l = new Promise((r) => loadWaiters.push(r));
  await ev(`document.querySelector('#action button').click()`);
  await Promise.race([l, sleep(8000)]); await sleep(700);

  // 1. ふりかえり（標準は「週」）。タブは3つで、「実験」がない
  await go('/review');
  const tabs = await ev(`Array.from(document.querySelectorAll('[data-seg=review] button')).map(b => b.textContent.trim()).join('|')`);
  check('review has exactly the tabs 週 / 目標 (no note tab)', tabs === '週|目標', tabs);
  check('the word 実験 is nowhere on the review screen', !/実験/.test(await visibleText()));
  check('the week panel is shown first', (await ev(`document.querySelector('[data-seg-panel="review:goal"]').hidden`)) === true);
  await shot('w15_review_week');

  // 2. 「目標」のタブを押す（画面の中で切り替わる）
  await ev(`document.querySelector('[data-seg=review] button[data-panel=goal]').click()`); await sleep(500);
  check('pressing 目標 shows the goal panel and hides the week panel',
    (await ev(`document.querySelector('[data-seg-panel="review:goal"]').hidden`)) === false
    && (await ev(`document.querySelector('[data-seg-panel="review:week"]').hidden`)) === true);
  const goalText = await ev(`document.querySelector('[data-seg-panel="review:goal"]').innerText`);
  check('the goal panel shows the goal name, its description and the change entry',
    /寝つき/.test(goalText) && /反応がなくなるまでの時間/.test(goalText) && /目標を変える/.test(goalText), goalText.replace(/\s+/g, ' '));
  check('the speech bubble says the current goal', /いまの目標は「寝つき」だよ。/.test(await visibleText()));
  check('there is no action button on the review screen', (await ev(`document.querySelector('#action button') === null || document.querySelector('#action').hidden === true`)) === true);
  await shot('w15_review_goal_tab');

  // 3. 直接開く（?panel=goal）。明るい・暗い
  await theme('light'); await go('/review?panel=goal');
  check('?panel=goal opens the goal panel', (await ev(`document.querySelector('[data-seg-panel="review:goal"]').hidden`)) === false);
  await shot('w15_review_goal_light');
  await theme('dark'); await go('/review?panel=goal');
  await shot('w15_review_goal_dark');
  await theme('light');

  // 4. 「いまのところ」は「目標」の中。ノートも、A さん・B さんの切り替えも、サンプルも出ない。審査員用のデモと明記される
  await go('/review?panel=goal');
  check('the goal panel holds the "いまのところ" block', /いまのところ/.test(await ev(`document.querySelector('[data-seg-panel="review:goal"]').innerText`)));
  check('no note, no A / B switch and no sample anywhere on the review screen',
    (await ev(`document.querySelector('[data-seg=user]') === null`)) === true && !/ノート|サンプル|Aさん|Bさん/.test(await visibleText()), '');
  check('the review account is marked as the demo for reviewers', /審査員用のデモアカウントです。/.test(await visibleText()));
  await go('/review?panel=note');
  check('the old ?panel=note link still opens the review screen (week)', (await ev(`document.querySelector('[data-seg-panel="review:week"]').hidden`)) === false);
  await shot('w15_review_goal_summary');

  // 5. 「目標を変える」→ 既存の選択画面
  await go('/review?panel=goal');
  const href = await ev(`document.querySelector('[data-seg-panel="review:goal"] a.row').getAttribute('href')`);
  check('the change entry goes to the existing goal choice page', /\/settings\/goal$/.test(href), href);
  await go('/settings/goal');
  check('the goal choice page opens', /夜のめやす|寝つき/.test(await visibleText()));
  await shot('w15_settings_goal');

  // 6. ホームに「今週の実験」がない
  await go('/home');
  check('the word 実験 is nowhere on the home screen', !/実験/.test(await visibleText()));
  await shot('w15_home');

  check('no script errors on the screens', exceptions.length === 0, exceptions.join(' / ').slice(0, 300));
  console.log(results.join('\n'));
  chrome.kill();
  process.exit(results.some((r) => r.startsWith('FAIL')) ? 1 : 0);
})().catch((e) => { console.log('FAILED', e); process.exit(1); });
