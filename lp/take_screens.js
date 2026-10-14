// Takes the app screens used on the landing page. Uses the review account, so every figure on the screens is sample data.
// The review screen has two tabs now, "week" and "goal". Both are taken. The review note tab is gone from the app.
// Set APP_BASE_URL (the app) and OUTPUT_DIR (an existing folder) before running.
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const BASE_URL = process.env.APP_BASE_URL || 'http://localhost:18080';
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const DEBUG_PORT = 9416;
const OUTPUT_DIR = process.env.OUTPUT_DIR;
const VIEWPORT_WIDTH = 390;
const VIEWPORT_HEIGHT = 844;
const DEVICE_SCALE = 2;
const IMAGE_QUALITY = 88;
const SHOWN_HOUR = 23;
const SHOWN_MINUTE = 41;
const SETTLE_MS = 900;
const SOUND_RAMP_MS = 4500;
const SOUND_SCRIPT_READY_MS = 1800;
const LOAD_TIMEOUT_MS = 9000;
const DEBUGGER_POLL_MS = 250;
const DEBUGGER_POLL_LIMIT = 40;
const REVIEW_ACCOUNT_EMAIL = 'demo@example.com';
const REVIEW_ACCOUNT_PASSWORD = 'demo-pass-123';

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (response) => {
      let body = '';
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => resolve(JSON.parse(body)));
    }).on('error', reject);
  });
}

// Headless Chrome has no real speech recognition, so the talk screen would show "the microphone is not available".
// This stand-in only lets the page start listening; it never returns any speech.
function buildSpeechStandInSource() {
  return 'window.SpeechRecognition = function () { this.start = function () {}; this.abort = this.stop = function () {}; };';
}

// The night clock shows the real time; the shown time is fixed so the screen reads as a night screen.
function buildClockShiftSource() {
  return `(() => {
    const RealDate = Date;
    const shownTime = new RealDate();
    shownTime.setHours(${SHOWN_HOUR}, ${SHOWN_MINUTE}, 0, 0);
    const offsetMilliseconds = shownTime.getTime() - RealDate.now();
    class ShiftedDate extends RealDate {
      constructor(...args) {
        if (args.length === 0) { super(RealDate.now() + offsetMilliseconds); } else { super(...args); }
      }
      static now() { return RealDate.now() + offsetMilliseconds; }
    }
    window.Date = ShiftedDate;
  })();`;
}

async function openPage() {
  const chrome = spawn(CHROME_PATH, ['--headless=new', '--disable-gpu', '--hide-scrollbars',
    `--remote-debugging-port=${DEBUG_PORT}`, `--user-data-dir=${process.env.TEMP}\\lp-shots-${Date.now()}`, 'about:blank'],
  { stdio: 'ignore' });
  let targets = [];
  for (let attempt = 0; attempt < DEBUGGER_POLL_LIMIT && targets.length === 0; attempt++) {
    try { targets = await fetchJson(`http://localhost:${DEBUG_PORT}/json`); } catch (error) { await sleep(DEBUGGER_POLL_MS); }
  }
  const socket = new WebSocket(targets.find((target) => target.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve) => { socket.onopen = resolve; });
  return { chrome, socket };
}

function createSession(socket) {
  let nextId = 0;
  const pendingReplies = new Map();
  const loadWaiters = [];
  socket.onmessage = (message) => {
    const payload = JSON.parse(message.data);
    if (payload.id && pendingReplies.has(payload.id)) {
      pendingReplies.get(payload.id)(payload);
      pendingReplies.delete(payload.id);
    }
    if (payload.method === 'Page.loadEventFired') { loadWaiters.splice(0).forEach((waiter) => waiter()); }
  };
  const send = (method, params = {}) => new Promise((resolve) => {
    const id = ++nextId;
    pendingReplies.set(id, resolve);
    socket.send(JSON.stringify({ id, method, params }));
  });
  const waitForLoad = async () => {
    const loaded = new Promise((resolve) => loadWaiters.push(resolve));
    await Promise.race([loaded, sleep(LOAD_TIMEOUT_MS)]);
    await sleep(SETTLE_MS);
  };
  const evaluate = async (expression) => {
    const reply = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true, userGesture: true });
    return reply.result.result ? reply.result.result.value : undefined;
  };
  const goTo = async (pathName) => {
    const loaded = waitForLoad();
    await send('Page.navigate', { url: BASE_URL + pathName });
    await loaded;
  };
  const saveScreen = async (name) => {
    const reply = await send('Page.captureScreenshot', { format: 'webp', quality: IMAGE_QUALITY });
    fs.writeFileSync(path.join(OUTPUT_DIR, `${name}.webp`), Buffer.from(reply.result.data, 'base64'));
    console.log(`saved ${name}`);
  };
  return { send, evaluate, goTo, saveScreen, waitForLoad };
}

async function logIn(session) {
  await session.goTo('/login');
  await session.evaluate(`document.querySelector('[name=email]').value = '${REVIEW_ACCOUNT_EMAIL}';
    document.querySelector('[name=password]').value = '${REVIEW_ACCOUNT_PASSWORD}'`);
  const loaded = session.waitForLoad();
  await session.evaluate(`document.querySelector('#action button').click()`);
  await loaded;
}

async function startNight(session) {
  await session.goTo('/prepare');
  const loaded = session.waitForLoad();
  await session.evaluate(`document.querySelector('#action button').click()`);
  await loaded;
  await sleep(SOUND_SCRIPT_READY_MS);
  await session.evaluate(`(() => { const start = document.getElementById('soundStart'); if (start) { start.click(); } })()`);
  await sleep(SOUND_RAMP_MS);
}

async function main() {
  const { chrome, socket } = await openPage();
  const session = createSession(socket);
  await session.send('Page.enable');
  await session.send('Emulation.setDeviceMetricsOverride',
    { width: VIEWPORT_WIDTH, height: VIEWPORT_HEIGHT, deviceScaleFactor: DEVICE_SCALE, mobile: true });
  await session.send('Page.addScriptToEvaluateOnNewDocument', { source: buildClockShiftSource() });
  await session.send('Page.addScriptToEvaluateOnNewDocument', { source: buildSpeechStandInSource() });

  await logIn(session);
  await session.goTo('/home');
  await session.saveScreen('home');
  await session.goTo('/review');
  await session.saveScreen('review-week');
  await session.evaluate(`document.querySelector('[data-seg="review"] button[data-panel="goal"]').click()`);
  await sleep(SETTLE_MS);
  await session.saveScreen('review-goal');
  await session.goTo('/settings');
  await session.saveScreen('settings');
  await session.goTo('/settings/memory');
  await session.saveScreen('memory');

  await startNight(session);
  await session.saveScreen('night');
  const talking = session.waitForLoad();
  await session.evaluate(`document.querySelector('form[action$="/night/mode"] .nightlink').click()`);
  await talking;
  // The device allows no sound after a page change until one touch; the real screen asks for it. One touch, as a user does, then shoot.
  await session.evaluate(`(() => { const start = document.getElementById('soundStart'); if (start) { start.click(); } })()`);
  await sleep(SOUND_RAMP_MS);
  await session.saveScreen('talk');

  chrome.kill();
  process.exit(0);
}

main().catch((error) => { console.log('FAILED', error); process.exit(1); });
