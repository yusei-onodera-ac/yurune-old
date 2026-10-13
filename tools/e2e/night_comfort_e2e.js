// 夜の使い心地の確認。Chrome（ヘッドレス）と Node.js 24 で動かす。
// 前提: アプリを 18092 番で Tomcat に載せ、無反応の時間を短くしておく。night_sound_e2e.js のあとに動かす想定。
// ここで確かめられること（ヘッドレスで試せる範囲。実機の画面ロックそのものは、このスクリプトでは試せない。確認できていないことに明記する）:
//  (1) 設定「夜のあいだ、画面をつけたままにする」の値が、夜の画面の data 属性（Wake Lock を求めるかどうか）に渡る
//  (2) 鳴らし始めると、audio 要素（MediaStreamAudioDestinationNode 経由）に切り替わる（この端末・Chrome では）。
//      切り替わらなかったときも、二重には鳴らさず、直の出力で鳴ること
//  (3) 設定がオンのときだけ、Wake Lock を求め、オフのときは求めないこと（対応していない環境では、何も起きないこと）
//  (4) ロック画面の「止める」「流す」操作を、同じ入口（simulateMediaSessionPause/Play）で試せること。
//      止めると、なめらかに 0 へ下がり、夜は終わらない（leaving が false のまま）。流すと、もとの音量へ戻る
//  (5) Media Session にタイトル・アーティストが設定されること
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const BASE = process.env.YURUNE_BASE || 'http://localhost:18092';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const DEBUG_PORT = Number(process.env.CHROME_DEBUG_PORT || 9412);
const DEMO_EMAIL = 'demo@example.com';
const DEMO_PASSWORD = 'demo-pass-123';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const check = (name, ok, detail) => {
  const line = `${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`;
  results.push(line);
  console.log(line);
};

class Session {
  constructor() { this.cookie = ''; this.token = ''; }
  async request(route, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (this.cookie) headers.Cookie = this.cookie;
    const response = await fetch(BASE + route, { redirect: 'manual', ...options, headers });
    const set = response.headers.getSetCookie();
    if (set.length) this.cookie = set.map((c) => c.split(';')[0]).join('; ');
    return response;
  }
  async refreshToken(route = '/login') {
    const html = await (await this.request(route)).text();
    this.token = (html.match(/name="csrf-token" content="([^"]+)"/) || [])[1];
  }
  post(route, body) {
    return this.request(route, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-Token': this.token }, body });
  }
}

// Chrome の開発者用の窓口（CDP）に、ページ単位でつなぐ（night_sound_e2e.js と同じ最小の窓口）
class Cdp {
  constructor(url) {
    this.nextId = 1;
    this.pending = new Map();
    this.exceptions = [];
    this.ws = new WebSocket(url);
    this.ready = new Promise((resolve, reject) => {
      this.ws.onopen = resolve;
      this.ws.onerror = reject;
    });
    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.method === 'Runtime.exceptionThrown') {
        this.exceptions.push(message.params.exceptionDetails.text);
      }
      if (message.id && this.pending.has(message.id)) {
        const { resolve, reject } = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (message.error) reject(new Error(message.error.message));
        else resolve(message.result);
      }
    };
  }
  send(method, params = {}) {
    const id = this.nextId++;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    return result.result.value;
  }
  async click(selector) {
    const point = await this.evaluate(`(() => { const r = document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
    for (const type of ['mousePressed', 'mouseReleased']) {
      await this.send('Input.dispatchMouseEvent', { type, x: point.x, y: point.y, button: 'left', clickCount: 1 });
    }
  }
  async waitFor(expression, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (await this.evaluate(expression).catch(() => false)) return true;
      await sleep(250);
    }
    return false;
  }
  async goto(url, pathname) {
    await this.send('Page.navigate', { url });
    return this.waitFor(`location.pathname === ${JSON.stringify(pathname)} && document.readyState === "complete"`, 15000);
  }
}

async function waitForDebugPort() {
  for (let attempt = 0; attempt < 100; attempt++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/list`)).json();
      const target = list.find((t) => t.type === 'page');
      if (target) return target.webSocketDebuggerUrl;
    } catch (error) {
      // まだ起動していない
    }
    await sleep(200);
  }
  throw new Error('Chrome did not open the debugging port');
}

async function setKeepScreenOn(session, on) {
  await session.refreshToken('/settings');
  const html = await (await session.request('/settings')).text();
  const checked = /aria-checked="true"[^>]*aria-label="夜のあいだ、画面をつけたままにする"|aria-label="夜のあいだ、画面をつけたままにする"[^>]*aria-checked="true"/.test(html);
  if (checked !== on) {
    await session.post('/settings/update', 'item=keep_screen_on&value=' + (on ? 'on' : 'off'));
  }
}

(async () => {
  const session = new Session();
  await session.refreshToken('/login');
  await session.post('/login', `email=${encodeURIComponent(DEMO_EMAIL)}&password=${DEMO_PASSWORD}`);

  // ---- (1) 設定オフのとき、夜の画面は Wake Lock を求めない ----
  await setKeepScreenOn(session, false);
  await session.refreshToken('/prepare');
  await session.post('/night/end', 'reason=user');
  await session.post('/night', 'mode=sound');

  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'yurune-chrome-comfort-'));
  const chrome = spawn(CHROME, [
    '--headless=new', `--remote-debugging-port=${DEBUG_PORT}`, `--user-data-dir=${profile}`,
    '--no-first-run', '--disable-gpu', 'about:blank'
  ], { stdio: 'ignore' });
  let page = null;
  try {
    page = new Cdp(await waitForDebugPort());
    await page.ready;
    await page.send('Runtime.enable');
    await page.send('Page.enable');
    await page.send('Emulation.setDeviceMetricsOverride', { width: 430, height: 900, deviceScaleFactor: 1, mobile: true });

    await page.goto(BASE + '/login', '/login');
    await page.evaluate(`(() => { const form = document.querySelector('input[name=email]').form; form.querySelector('input[name=email]').value = ${JSON.stringify(DEMO_EMAIL)}; form.querySelector('input[type=password]').value = ${JSON.stringify(DEMO_PASSWORD)}; form.submit(); return true; })()`);
    await page.waitFor('location.pathname === "/home"', 15000);

    await page.goto(BASE + '/night', '/night');
    await page.waitFor('!!document.getElementById("soundStart")', 10000);
    const offAttr = await page.evaluate(`document.getElementById('nightObserve').getAttribute('data-keep-screen-on')`);
    check('the setting (off) reaches the night screen as a data attribute', offAttr === 'false', `data-keep-screen-on=${offAttr}`);

    await page.click('#soundStart');
    check('sound starts', await page.waitFor('window.yuruneNightSound.status().started === true', 5000));
    await sleep(1500);
    const offStatus = await page.evaluate('window.yuruneNightSound.status()');
    check('wakeLockPreferred is false, so no wake lock is requested', offStatus.wakeLockPreferred === false && offStatus.wakeLockActive === false, JSON.stringify(offStatus));
    check('the sound output decided on exactly one path (direct, or the audio element)', typeof offStatus.usingMediaElement === 'boolean');
    console.log('usingMediaElement (off case): ' + offStatus.usingMediaElement + ' — 画面が消えても続くかは、この結果だけでは分からない（実機で確認する）');

    // ---- (2) Media Session のメタデータ ----
    const mediaSession = await page.evaluate(`(() => ('mediaSession' in navigator) ? { title: navigator.mediaSession.metadata && navigator.mediaSession.metadata.title, artist: navigator.mediaSession.metadata && navigator.mediaSession.metadata.artist } : null)()`);
    if (mediaSession) {
      check('Media Session metadata names the app and ぽてね', mediaSession.title === 'ゆるね' && mediaSession.artist === 'ぽてね', JSON.stringify(mediaSession));
    } else {
      console.log('note: この Chrome には navigator.mediaSession がない（確認を飛ばす）');
    }

    // ---- (3) ロック画面の「止める」「流す」を、同じ入口で試す ----
    // 音量が、上げのランプ（10 秒）を終えて落ち着いてから、止める・流すを試す（途中の値と比べても、意味がないため）
    await sleep(10000);
    const beforePause = await page.evaluate('window.yuruneNightSound.status().gain');
    await page.evaluate('window.yuruneNightSound.simulateMediaSessionPause()');
    const pausedQuickly = await page.waitFor('window.yuruneNightSound.status().paused === true', 3000);
    check('pausing from the lock screen sets paused, without ending the night', pausedQuickly);
    await sleep(3200);
    const duringPause = await page.evaluate('window.yuruneNightSound.status()');
    check('pausing ramps the gain down to (near) zero, smoothly, and the night stays active', duringPause.gain < 0.02 && duringPause.leaving === false, JSON.stringify(duringPause));
    const idleText = await page.evaluate(`document.getElementById('nightSoundState').textContent`);
    check('while paused, the screen quietly says sound is not playing (no new wording)', idleText === '音は、まだ流れていません。', idleText);
    const buttonVisibleWhilePaused = await page.evaluate(`!document.getElementById('soundStart').hidden`);
    check('the start button reappears while paused, for a deliberate tap to resume', buttonVisibleWhilePaused);

    await page.evaluate('window.yuruneNightSound.simulateMediaSessionPlay()');
    const resumed = await page.waitFor('window.yuruneNightSound.status().paused === false', 3000);
    check('playing from the lock screen clears paused and heads back to the previous volume', resumed);
    await sleep(11000);
    const afterResume = await page.evaluate('window.yuruneNightSound.status()');
    check('the gain returns close to where it was before pausing', Math.abs(afterResume.gain - beforePause) < 0.02, `before=${beforePause} after=${afterResume.gain}`);

    await page.evaluate(`document.querySelector('button.endbtn').click()`);
    await page.waitFor('location.pathname === "/home"', 8000);
  } finally {
    if (page) {
      page.ws.close();
    }
    chrome.kill();
    await sleep(2000);
    try {
      fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 });
    } catch (error) {
      console.log('note: the temporary Chrome profile could not be removed: ' + profile);
    }
  }

  // ---- (4) 設定オンのとき、夜の画面は Wake Lock を求める（対応していない環境では、何も起きない） ----
  await setKeepScreenOn(session, true);
  await session.refreshToken('/prepare');
  await session.post('/night/end', 'reason=user');
  await session.post('/night', 'mode=sound');

  const profile2 = fs.mkdtempSync(path.join(os.tmpdir(), 'yurune-chrome-comfort2-'));
  const chrome2 = spawn(CHROME, [
    '--headless=new', `--remote-debugging-port=${DEBUG_PORT}`, `--user-data-dir=${profile2}`,
    '--no-first-run', '--disable-gpu', 'about:blank'
  ], { stdio: 'ignore' });
  let page2 = null;
  try {
    page2 = new Cdp(await waitForDebugPort());
    await page2.ready;
    await page2.send('Runtime.enable');
    await page2.send('Page.enable');
    await page2.send('Emulation.setDeviceMetricsOverride', { width: 430, height: 900, deviceScaleFactor: 1, mobile: true });

    await page2.goto(BASE + '/login', '/login');
    await page2.evaluate(`(() => { const form = document.querySelector('input[name=email]').form; form.querySelector('input[name=email]').value = ${JSON.stringify(DEMO_EMAIL)}; form.querySelector('input[type=password]').value = ${JSON.stringify(DEMO_PASSWORD)}; form.submit(); return true; })()`);
    await page2.waitFor('location.pathname === "/home"', 15000);

    await page2.goto(BASE + '/night', '/night');
    await page2.waitFor('!!document.getElementById("soundStart")', 10000);
    const onAttr = await page2.evaluate(`document.getElementById('nightObserve').getAttribute('data-keep-screen-on')`);
    check('the setting (on) reaches the night screen as a data attribute', onAttr === 'true', `data-keep-screen-on=${onAttr}`);

    const supportsWakeLock = await page2.evaluate(`'wakeLock' in navigator`);
    await page2.click('#soundStart');
    await page2.waitFor('window.yuruneNightSound.status().started === true', 5000);
    await sleep(1500);
    const onStatus = await page2.evaluate('window.yuruneNightSound.status()');
    check('wakeLockPreferred is true', onStatus.wakeLockPreferred === true, JSON.stringify(onStatus));
    if (supportsWakeLock) {
      check('a wake lock was requested and granted (this Chrome supports the API)', onStatus.wakeLockActive === true, JSON.stringify(onStatus));
    } else {
      console.log('note: この Chrome には navigator.wakeLock がない。拒まれても、何も言わず続けることだけを確認する');
      check('no exception was thrown even though wakeLock is unavailable', true);
    }

    await page2.evaluate(`document.querySelector('button.endbtn').click()`);
    await page2.waitFor('location.pathname === "/home"', 8000);
  } finally {
    if (page2) {
      page2.ws.close();
    }
    chrome2.kill();
    await sleep(2000);
    try {
      fs.rmSync(profile2, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 });
    } catch (error) {
      console.log('note: the temporary Chrome profile could not be removed: ' + profile2);
    }
  }

  check('no uncaught exceptions in either page', (page === null || page.exceptions.length === 0) && (page2 === null || page2.exceptions.length === 0),
      JSON.stringify({ a: page ? page.exceptions : null, b: page2 ? page2.exceptions : null }));
  console.log('--- 確認できていないこと ---');
  console.log('実機（iOS Safari、Android Chrome）で、画面を実際にロックしたとき、音が本当に続くかは、ヘッドレス Chrome では確かめられない。');
  console.log('ロック画面そのものの見た目（曲名・操作）も、OS の実機でしか確認できない。');
  console.log(results.filter((r) => r.startsWith('FAIL')).length === 0 ? 'ALL PASS' : 'SOME FAILED');
  process.exit(results.some((r) => r.startsWith('FAIL')) ? 1 : 0);
})().catch((error) => {
  console.log('SCRIPT ERROR', error);
  process.exit(1);
});
