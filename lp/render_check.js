// Renders the built landing page at desktop and phone widths and saves it in slices for visual review.
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const PAGE_FILE = process.env.PAGE_FILE || 'index.html';
const PAGE_PATH = path.join(__dirname, 'dist', PAGE_FILE);
const OUTPUT_PREFIX = PAGE_FILE === 'index.html' ? '' : PAGE_FILE.replace('.html', '') + '-';
const OUTPUT_DIR = path.join(__dirname, 'render');
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const DEBUG_PORT = 9417;
const FONT_SETTLE_MS = 2500;
const DEBUGGER_POLL_MS = 250;
const DEBUGGER_POLL_LIMIT = 40;
const SLICE_HEIGHT = 1500;
const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 900, scale: 1, mobile: false, colorScheme: 'light' },
  { name: 'phone', width: 390, height: 844, scale: 2, mobile: true, colorScheme: 'light' },
  { name: 'desktop-dark', width: 1280, height: 900, scale: 1, mobile: false, colorScheme: 'dark' },
];

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

async function connect() {
  const chrome = spawn(CHROME_PATH, ['--headless=new', '--disable-gpu', '--hide-scrollbars',
    `--remote-debugging-port=${DEBUG_PORT}`, `--user-data-dir=${process.env.TEMP}\\lp-render-${Date.now()}`, 'about:blank'],
  { stdio: 'ignore' });
  let targets = [];
  for (let attempt = 0; attempt < DEBUGGER_POLL_LIMIT && targets.length === 0; attempt++) {
    try { targets = await fetchJson(`http://localhost:${DEBUG_PORT}/json`); } catch (error) { await sleep(DEBUGGER_POLL_MS); }
  }
  const socket = new WebSocket(targets.find((target) => target.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve) => { socket.onopen = resolve; });
  return { chrome, socket };
}

function createSender(socket) {
  let nextId = 0;
  const pending = new Map();
  socket.onmessage = (message) => {
    const payload = JSON.parse(message.data);
    if (payload.id && pending.has(payload.id)) { pending.get(payload.id)(payload); pending.delete(payload.id); }
  };
  return (method, params = {}) => new Promise((resolve) => {
    const id = ++nextId;
    pending.set(id, resolve);
    socket.send(JSON.stringify({ id, method, params }));
  });
}

async function evaluate(send, expression) {
  const reply = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  return reply.result.result ? reply.result.result.value : undefined;
}

async function renderViewport(send, viewport) {
  await send('Emulation.setDeviceMetricsOverride',
    { width: viewport.width, height: viewport.height, deviceScaleFactor: viewport.scale, mobile: viewport.mobile });
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: viewport.colorScheme }] });
  await send('Page.navigate', { url: 'file:///' + PAGE_PATH.replace(/\\/g, '/') });
  await sleep(FONT_SETTLE_MS);
  const metrics = await evaluate(send, `JSON.stringify({ height: document.documentElement.scrollHeight,
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    fonts: Array.from(document.fonts).filter((face) => face.status === 'loaded').map((face) => face.family).join(',') })`);
  const { height, overflow, fonts } = JSON.parse(metrics);
  console.log(`${viewport.name}: height=${height} horizontalOverflow=${overflow} loadedFonts=${fonts}`);
  for (let top = 0, index = 0; top < height; top += SLICE_HEIGHT, index++) {
    const clip = { x: 0, y: top, width: viewport.width, height: Math.min(SLICE_HEIGHT, height - top), scale: 1 };
    const reply = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true, clip });
    fs.writeFileSync(path.join(OUTPUT_DIR, `${OUTPUT_PREFIX}${viewport.name}-${String(index).padStart(2, '0')}.png`), Buffer.from(reply.result.data, 'base64'));
  }
}

async function main() {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  const { chrome, socket } = await connect();
  const send = createSender(socket);
  await send('Page.enable');
  for (const viewport of VIEWPORTS) { await renderViewport(send, viewport); }
  chrome.kill();
  process.exit(0);
}

main().catch((error) => { console.log('FAILED', error); process.exit(1); });
