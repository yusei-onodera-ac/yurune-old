// 夜の音（端末で合成する雨・波・森）の確認。Chrome（ヘッドレス。自動再生を許すフラグは付けない）と Node.js 24 で動かす。
// 前提: アプリを 18085 番で Tomcat に載せ、無反応の時間を 1 分にしておく。
// 確かめること:
//  (1) 観測の応答に sound（曲、音量の%、フェードの残り秒）が入り、既存の ended、reload、actions は残っている
//  (2) 利用者が押すまで、音は鳴らない。鳴らせていないときは、その旨を出す（嘘をつかない）
//  (3) 鳴り始めの音量は、ゆっくり上がる（ランプ）。上限（60%）と、音量（40%）の値を超えない
//  (4) 雨・波・森の波形（ピーク、直流、ループの戻りの跳ね、帯域、波の周期）が、狙いの範囲にある
//  (5) 「終わる」は、音を下げてから移る。無反応の終了は、残りの時間をかけて下げる（ホームへは、すぐに移らない）
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const BASE = process.env.YURUNE_BASE || 'http://localhost:18085';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const DEBUG_PORT = Number(process.env.CHROME_DEBUG_PORT || 9405);
const SHOT_DIR = process.env.SHOT_DIR || os.tmpdir();
const DEMO_EMAIL = 'demo@example.com';
const DEMO_PASSWORD = 'demo-pass-123';
const SAMPLE_RATE = 48000;
const TEXT_IDLE = '音は、まだ流れていません。';
const NO_REACTION_WAIT_MS = 90000;

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

// Chrome の開発者用の窓口（CDP）に、ページ単位でつなぐ。Node 24 の WebSocket を使う
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
  async screenshot(file) {
    const shot = await this.send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(SHOT_DIR, file), Buffer.from(shot.data, 'base64'));
    return path.join(SHOT_DIR, file);
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

// ---- 波形の計測。ブラウザの中で動かす（この関数だけを送る）。雑音の部品を、ブラウザの同じ式で描画した結果を測る ----
function measure(samples, rate) {
  const n = samples.length;
  let sum = 0;
  let square = 0;
  let peak = 0;
  for (let i = 0; i < n; i++) {
    const v = samples[i];
    sum += v;
    square += v * v;
    if (Math.abs(v) > peak) peak = Math.abs(v);
  }
  const mean = sum / n;
  const rms = Math.sqrt(square / n);

  // ループの戻り（12 秒ごと）での跳ね。ふつうの 1 サンプルの差と比べる
  const loop = 12 * rate;
  let boundaryStep = 0;
  const steps = new Float64Array(n);
  for (let i = 1; i < n; i++) {
    const step = Math.abs(samples[i] - samples[i - 1]);
    steps[i] = step;
    if (i % loop === 0 && step > boundaryStep) boundaryStep = step;
  }
  const sorted = Array.from(steps).sort((a, b) => a - b);
  const typicalStep = sorted[Math.floor(sorted.length * 0.99)];

  // 帯域の割合。Hann 窓の FFT を、区切って平均する
  const size = 4096;
  const re = new Float64Array(size);
  const im = new Float64Array(size);
  const bands = { low: 0, mid: 0, high: 0, veryHigh: 0 };
  let total = 0;
  let segments = 0;
  for (let start = 0; start + size <= n && segments < 200; start += size, segments++) {
    for (let k = 0; k < size; k++) {
      re[k] = samples[start + k] * (0.5 - 0.5 * Math.cos((2 * Math.PI * k) / size));
      im[k] = 0;
    }
    for (let i = 1, j = 0; i < size; i++) {
      let bit = size >> 1;
      for (; j & bit; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        [re[i], re[j]] = [re[j], re[i]];
        [im[i], im[j]] = [im[j], im[i]];
      }
    }
    for (let len = 2; len <= size; len <<= 1) {
      const angle = (-2 * Math.PI) / len;
      for (let i = 0; i < size; i += len) {
        for (let k = 0; k < len / 2; k++) {
          const wr = Math.cos(angle * k);
          const wi = Math.sin(angle * k);
          const a = i + k;
          const b = i + k + len / 2;
          const tr = re[b] * wr - im[b] * wi;
          const ti = re[b] * wi + im[b] * wr;
          re[b] = re[a] - tr;
          im[b] = im[a] - ti;
          re[a] += tr;
          im[a] += ti;
        }
      }
    }
    for (let k = 1; k < size / 2; k++) {
      const hz = (k * rate) / size;
      const power = re[k] * re[k] + im[k] * im[k];
      total += power;
      if (hz < 500) bands.low += power;
      else if (hz < 2000) bands.mid += power;
      else if (hz < 8000) bands.high += power;
      else bands.veryHigh += power;
    }
  }

  // 包絡（0.1 秒ごとの大きさ）の、いちばん強い周期（秒）。波の、ゆっくりした揺れを見る
  const blockSamples = Math.round(0.1 * rate);
  const envelope = [];
  for (let start = 0; start + blockSamples <= n; start += blockSamples) {
    let block = 0;
    for (let k = 0; k < blockSamples; k++) block += samples[start + k] * samples[start + k];
    envelope.push(Math.sqrt(block / blockSamples));
  }
  const envMean = envelope.reduce((a, b) => a + b, 0) / envelope.length;
  let bestFreq = 0;
  let bestPower = -1;
  for (let f = 0.02; f <= 0.3; f += 0.002) {
    let r = 0;
    let q = 0;
    for (let j = 0; j < envelope.length; j++) {
      const v = envelope[j] - envMean;
      r += v * Math.cos((2 * Math.PI * f * j) / 10);
      q += v * Math.sin((2 * Math.PI * f * j) / 10);
    }
    if (r * r + q * q > bestPower) {
      bestPower = r * r + q * q;
      bestFreq = f;
    }
  }

  const share = (value) => Number((value / total).toFixed(3));
  return {
    peak: Number(peak.toFixed(3)),
    rms: Number(rms.toFixed(4)),
    mean: Number(mean.toFixed(5)),
    loopBoundaryStepRatio: Number((boundaryStep / typicalStep).toFixed(2)),
    lowShare: share(bands.low),
    midShare: share(bands.mid),
    highShare: share(bands.high),
    veryHighShare: share(bands.veryHigh),
    envelopePeriodSec: Number((1 / bestFreq).toFixed(1))
  };
}

(async () => {
  // ---- (1) 観測の応答の形（サーバー） ----
  const session = new Session();
  await session.refreshToken('/login');
  await session.post('/login', `email=${encodeURIComponent(DEMO_EMAIL)}&password=${DEMO_PASSWORD}`);
  await session.refreshToken('/prepare');
  await session.post('/night/end', 'reason=user');
  await session.post('/night', 'mode=talk');
  await session.refreshToken('/night');
  const observed = await (await session.post('/night/observe', 'lastReactionAgoSec=0')).json();
  check('observe keeps ended, reload and actions', typeof observed.ended === 'boolean' && typeof observed.reload === 'boolean' && Array.isArray(observed.actions), JSON.stringify(observed));
  check('observe reports the sound state', observed.sound && typeof observed.sound.track === 'string' && observed.sound.volumePct === 40 && observed.sound.fadeRemainingSec === 0, JSON.stringify(observed.sound));

  // ---- ブラウザ ----
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'yurune-chrome-'));
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
    check('browser login reaches home', await page.waitFor('location.pathname === "/home"', 15000));

    // ---- (2) 夜を、談話の画面から始め、音だけにもどる。鳴る前は、鳴っていないと書く ----
    await page.goto(BASE + '/night', '/night');
    await page.waitFor('!!document.getElementById("nightSoundState")', 10000);
    const idleTalk = await page.evaluate(`(() => ({ text: document.getElementById('nightSoundState').textContent, started: window.yuruneNightSound.status().started, buttonVisible: !document.getElementById('soundStart').hidden }))()`);
    check('before a tap, the talk screen says no sound is playing', idleTalk.text === TEXT_IDLE && idleTalk.started === false && idleTalk.buttonVisible, JSON.stringify(idleTalk));
    console.log('screenshot: ' + await page.screenshot('night_talk_idle.png'));

    await page.evaluate(`(() => { document.querySelector('input[name=mode][value=sound]').form.querySelector('button').click(); return true; })()`);
    await page.waitFor('location.pathname === "/night" && !!document.getElementById("soundStart") && document.readyState === "complete"', 15000);
    await page.waitFor('!!document.getElementById("nightSoundState")', 10000);
    const idleSound = await page.evaluate(`(() => ({ text: document.getElementById('nightSoundState').textContent, label: document.getElementById('nightSoundState').getAttribute('data-sound-label'), started: window.yuruneNightSound.status().started }))()`);
    check('sound screen starts silent and says so', idleSound.text === TEXT_IDLE && idleSound.started === false, JSON.stringify(idleSound));
    console.log('screenshot: ' + await page.screenshot('night_sound_idle.png'));

    // 指が触れた瞬間（pointerdown・touchstart）や pointerup では、始めない。iPhone の Safari は、これらでは再生を許さず、
    // 始めてしまうと出力先が直の出力に決まってしまう（マナーモードの無音スイッチで消される）。許されるのは touchend・click・keydown だけ
    const earlyStart = await page.evaluate(`(() => { ['pointerdown', 'touchstart', 'pointerup'].forEach((name) => document.dispatchEvent(new Event(name, { bubbles: true }))); const state = window.yuruneNightSound.status(); return { started: state.started, contextState: state.contextState }; })()`);
    check('touching down (pointerdown, touchstart, pointerup) does not start the sound or create the output', earlyStart.started === false && earlyStart.contextState === null, JSON.stringify(earlyStart));

    // 利用者が、音を流す を押す（操作のあとだけ、鳴る）
    await page.click('#soundStart');
    check('the sound starts after the tap', await page.waitFor('window.yuruneNightSound.status().started === true && window.yuruneNightSound.status().contextState === "running"', 5000));
    const label = idleSound.label;
    check('the screen says what is playing', await page.evaluate(`document.getElementById('nightSoundState').textContent === ${JSON.stringify('今夜は' + label + 'を流しているよ。')}`));
    const buttonHidden = await page.evaluate(`document.getElementById('soundStart').hidden`);
    check('the start button goes away while playing', buttonHidden === true);

    // (3) ゆっくり上がる。音量 40% は、ゲイン 0.24（40 / 100 * 0.6）
    const early = await page.evaluate(`window.yuruneNightSound.status().gain`);
    await sleep(2000);
    const soon = await page.evaluate(`window.yuruneNightSound.status().gain`);
    await sleep(10000);
    const settled = await page.evaluate(`window.yuruneNightSound.status().gain`);
    check('the level rises over time, not in one step', early < 0.05 && soon > early && soon < 0.15, `t0=${early} t2s=${soon}`);
    check('the level settles at the volume (40% of 0.6 = 0.24)', Math.abs(settled - 0.24) < 0.01, `gain=${settled}`);
    // 声を聞いている間は、環境音を下げる（会話の画面が呼ぶ）。下げるのは早く、戻すのは、ゆっくり
    await page.evaluate(`window.yuruneNightSound.setDuck(0.2)`);
    await sleep(1700);
    const ducked = await page.evaluate(`window.yuruneNightSound.status().gain`);
    check('ducking lowers the level quickly to 0.2 of the volume (0.24 * 0.2 = 0.048)', Math.abs(ducked - 0.048) < 0.01, `gain=${ducked}`);
    await page.evaluate(`window.yuruneNightSound.setDuck(1)`);
    await sleep(1500);
    const returning = await page.evaluate(`window.yuruneNightSound.status().gain`);
    check('the level comes back slowly, not in one step, and never above the volume', returning > ducked && returning < 0.12 && returning <= 0.241, `gain=${returning}`);
    check('after the slow return the level is the volume again', await page.waitFor('Math.abs(window.yuruneNightSound.status().gain - 0.24) < 0.01', 14000));
    console.log('screenshot: ' + await page.screenshot('night_sound_playing.png'));

    // (4) 波形。ブラウザの同じ部品を、30 秒ぶん描画して測る
    const expected = { rain: 'rain', wave: 'wave', forest: 'forest' };
    const metrics = {};
    for (const key of Object.keys(expected)) {
      metrics[key] = await page.evaluate(`(async () => { const samples = await window.yuruneNightSound.renderTrack(${JSON.stringify(key)}, 30); return (${measure.toString()})(samples, ${SAMPLE_RATE}); })()`);
      console.log(key, JSON.stringify(metrics[key]));
    }
    for (const key of Object.keys(metrics)) {
      const m = metrics[key];
      check(`${key}: peak stays below 1 (no clipping)`, m.peak < 0.95, `peak=${m.peak}`);
      check(`${key}: no DC offset`, Math.abs(m.mean) < 0.005, `mean=${m.mean}`);
      check(`${key}: no click at the loop turn`, m.loopBoundaryStepRatio < 3, `ratio=${m.loopBoundaryStepRatio}`);
    }
    check('rain is broadband (energy above 2 kHz)', metrics.rain.highShare + metrics.rain.veryHighShare > 0.05, `high=${metrics.rain.highShare + metrics.rain.veryHighShare}`);
    check('wave is low (most energy below 500 Hz)', metrics.wave.lowShare > 0.7 && metrics.wave.highShare < 0.05, `low=${metrics.wave.lowShare} high=${metrics.wave.highShare}`);
    check('wave swells every 8 to 14 seconds', metrics.wave.envelopePeriodSec >= 8 && metrics.wave.envelopePeriodSec <= 14, `period=${metrics.wave.envelopePeriodSec}`);
    check('forest is low (no high, sharp sounds)', metrics.forest.lowShare > 0.85 && metrics.forest.highShare < 0.05 && metrics.forest.veryHighShare < 0.01, `low=${metrics.forest.lowShare} high=${metrics.forest.highShare}`);

    // (5a) 「終わる」は、音を下げてから移る
    await page.click('button.endbtn');
    await sleep(1000);
    const fadingNow = await page.evaluate(`({ leaving: window.yuruneNightSound.status().leaving, gain: window.yuruneNightSound.status().gain })`);
    check('ending by the button starts a fade, not a cut', fadingNow.leaving === true && fadingNow.gain < 0.24, JSON.stringify(fadingNow));
    check('after the fade the screen goes home', await page.waitFor('location.pathname === "/home"', 8000));

    // (5b) 無反応のときは、残りの時間をかけて下げる。ホームへは、すぐに移らない
    await session.post('/night', 'mode=sound');
    const observedAgain = await (await session.post('/night/observe', 'lastReactionAgoSec=0')).json();
    check('a new night reports the sound state again', observedAgain.sound && observedAgain.sound.volumePct === 40, JSON.stringify(observedAgain.sound));
    await page.goto(BASE + '/night', '/night');
    await page.waitFor('!!document.getElementById("soundStart")', 10000);
    await page.click('#soundStart');
    await page.waitFor('window.yuruneNightSound.status().started === true', 5000);
    await sleep(12000);
    const beforeSilence = await page.evaluate(`window.yuruneNightSound.status().gain`);
    check('the silence test started with sound playing', beforeSilence > 0.2, `gain=${beforeSilence}`);
    const endedFading = await page.waitFor('window.yuruneNightSound.status().leaving === true', NO_REACTION_WAIT_MS);
    check('with no reaction the night ends and the sound starts to fade', endedFading);
    const gainA = await page.evaluate(`window.yuruneNightSound.status().gain`);
    await sleep(5000);
    const gainB = await page.evaluate(`window.yuruneNightSound.status().gain`);
    check('the fade keeps going down, smoothly', gainB < gainA && gainB > gainA - 0.02, `${gainA} -> ${gainB}`);
    check('the screen stays until the fade is over', await page.evaluate('location.pathname') === '/night');
  } finally {
    if (page) {
      page.ws.close();
    }
    chrome.kill();
    // Chrome が閉じきるまで、一時のプロファイルは消せないことがあるので、少し待って、何度か試す
    await sleep(2000);
    try {
      fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 });
    } catch (error) {
      console.log('note: the temporary Chrome profile could not be removed: ' + profile);
    }
  }

  check('no uncaught exceptions in the page', page === null || page.exceptions.length === 0, page ? page.exceptions.join(' | ') : 'no page');
  console.log(results.filter((r) => r.startsWith('FAIL')).length === 0 ? 'ALL PASS' : 'SOME FAILED');
  process.exit(results.some((r) => r.startsWith('FAIL')) ? 1 : 0);
})().catch((error) => {
  console.log('SCRIPT ERROR', error);
  process.exit(1);
});
