// 記事と提出用の図を、SVG で書き、Chrome（ヘッドレス）で PNG にする。
// 使い方: node docs/diagrams/build.js   （Chrome と Node.js 24 が要る。フォントは Google Fonts の Noto Sans JP）
// 色は、アプリの画面と同じ（lp/template.html、src/main/webapp/css/yurune.css）。
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const OUT_DIR = __dirname;
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const DEBUG_PORT = 9418;
const SCALE = 2;

const INK = '#1D2326';
const SUB = '#69757B';
const LINE = '#D5DDDE';
const BG = '#F2F4F4';
const ACCENT = '#2E8C87';
const ACCENT_SOFT = '#DDEFED';
const GRAY_DOT = '#8DA09E';
const FONT = "'Noto Sans JP', 'Hiragino Sans', 'Yu Gothic', sans-serif";

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const esc = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;');

function text(x, y, size, fill, content, options = {}) {
  const weight = options.weight || 400;
  const anchor = options.anchor || 'start';
  return `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" font-weight="${weight}" text-anchor="${anchor}">${esc(content)}</text>`;
}

function lines(x, y, size, fill, rows, lineHeight, options = {}) {
  return rows.map((row, index) => text(x, y + index * lineHeight, size, fill, row, options)).join('');
}

function rect(x, y, w, h, fill, stroke, strokeWidth = 1.5, radius = 6) {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${radius}" fill="${fill}" stroke="${stroke || 'none'}" stroke-width="${strokeWidth}"/>`;
}

function arrow(x1, y1, x2, y2, color = SUB) {
  const dx = Math.sign(x2 - x1);
  const head = `<path d="M ${x2} ${y2} l ${-dx * 9} -5 l 0 10 z" fill="${color}"/>`;
  const tail = `<path d="M ${x1} ${y1} l ${dx * 9} -5 l 0 10 z" fill="${color}"/>`;
  return `<line x1="${x1 + dx * 8}" y1="${y1}" x2="${x2 - dx * 8}" y2="${y2}" stroke="${color}" stroke-width="1.5"/>${head}${tail}`;
}

function svgWrap(width, height, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" font-family="${FONT}">`
    + `<rect width="${width}" height="${height}" fill="#FFFFFF"/>${body}</svg>`;
}

// ---- 1 Google Cloud の構成 ----
function architecture() {
  const W = 1200;
  const H = 760;
  let s = '';
  s += text(32, 50, 25, INK, 'ゆるね  Google Cloud の構成', { weight: 700 });
  s += text(32, 80, 16, SUB, '夜の制御、会話、週の見直しは、Cloud Run の 1 つのアプリで動きます');

  const top = 112;
  const panelH = 600;
  // 左の枠: 利用者の端末
  s += rect(32, top, 270, panelH, '#FFFFFF', LINE);
  s += text(52, top + 34, 17, INK, '利用者の端末', { weight: 700 });
  s += text(52, top + 58, 14, SUB, 'スマホのブラウザ（PWA）');
  const phoneItems = [
    ['画面', ['JSP と JavaScript。', 'ぽてねの最新の返事を、', '1 行だけ出す']],
    ['環境音', ['雨、波、森を、', '端末の中で合成する', '（Web Audio）']],
    ['声を文字にする', ['ブラウザの音声認識。', '音声は、このアプリの', 'サーバーに送らない']],
    ['ぽてねの声', ['サーバーでつくった声を、', '環境音と同じ出力で', '流す']],
  ];
  phoneItems.forEach((item, index) => {
    const y = top + 78 + index * 126;
    s += rect(48, y, 238, 114, BG, null);
    s += text(64, y + 30, 17, INK, item[0], { weight: 700 });
    s += lines(64, y + 55, 14.5, SUB, item[1], 21);
  });

  // 中の枠: Cloud Run
  s += rect(386, top, 380, panelH, '#FFFFFF', LINE);
  s += text(406, top + 34, 17, INK, 'アプリ（Cloud Run・東京）', { weight: 700 });
  s += text(406, top + 58, 14, SUB, 'Java 21 + Tomcat 10.1。インスタンスは 1〜3');
  const appItems = [
    ['固定の枠（コード）', ['入出力の検査、音量と呼び出しの上限、', '夜を終える条件、決めた文'], false],
    ['夜の制御', ['反応を見て、ツールを選ぶ。', '使えないときは、規則だけで動く'], true],
    ['会話', ['検査を通した発言を、', 'ぽてねとして返す'], true],
    ['週の見直し', ['近い夜どうしを比べる検定と規則。', 'AI は使わない'], false],
    ['判断の記録', ['見たこと、選んだこと、理由。', '会話の本文は含めない'], false],
  ];
  appItems.forEach((item, index) => {
    const y = top + 78 + index * 103;
    s += rect(402, y, 348, 92, BG, null);
    if (item[2]) s += rect(402, y, 5, 92, ACCENT, null, 0, 2);
    s += text(422, y + 29, 17, INK, item[0], { weight: 700 });
    s += lines(422, y + 53, 14.5, SUB, item[1], 21);
  });

  // 右の枠: つないでいるサービス
  s += rect(850, top, 318, panelH, '#FFFFFF', LINE);
  s += text(870, top + 34, 17, INK, 'つないでいるサービス', { weight: 700 });
  s += text(870, top + 58, 14, SUB, 'Google Cloud');
  const services = [
    ['Firestore', '東京', '利用者ごとのデータを保存', false],
    ['Firebase Authentication', '', 'メールとパスワード', false],
    ['Secret Manager', '', 'Gemini のキー', false],
    ['Gemini API', '', '夜の判断と、ぽてねの会話', true],
    ['Model Armor', '米国', '入力と返事の検査', false],
    ['Cloud Text-to-Speech', '', 'ぽてねの声', false],
    ['Cloud Logging / Monitoring', '', 'ログ、メトリクス、アラート、予算', false],
  ];
  services.forEach((item, index) => {
    const y = top + 74 + index * 73;
    s += rect(866, y, 286, 63, item[3] ? ACCENT_SOFT : BG, item[3] ? ACCENT : null, 1.5);
    s += text(882, y + 26, 15.5, INK, item[0], { weight: 700 });
    if (item[1]) s += text(1136, y + 26, 13.5, SUB, item[1], { anchor: 'end' });
    s += text(882, y + 49, 13.5, SUB, item[2]);
  });

  // 枠どうしの矢印
  s += arrow(302, top + 300, 386, top + 300);
  s += text(344, top + 286, 13.5, SUB, 'HTTPS', { anchor: 'middle' });
  s += arrow(766, top + 300, 850, top + 300);
  s += text(808, top + 286, 13.5, SUB, 'API', { anchor: 'middle' });

  // 凡例
  s += rect(32, 722, 5, 24, ACCENT, null, 0, 2);
  s += text(48, 740, 14, SUB, '青緑の印は、Gemini に聞く所です。ほかは、コードだけで決まります。');
  return svgWrap(W, H, s);
}

// ---- 2 夜のくり返しと、週の見直し ----
function cycle() {
  const W = 1200;
  const H = 440;
  let s = '';
  s += text(32, 50, 25, INK, '夜のくり返しと、週の見直し', { weight: 700 });
  s += text(32, 80, 16, SUB, '丸は 1 夜です。最初の 7 夜で基準をとり、そのあとは、3 夜に 1 夜を標準の夜にします。');

  const x0 = 70;
  const step = 38.5;
  const cy = 196;
  const r = 12;
  const nights = [];
  for (let night = 1; night <= 28; night++) {
    let kind = 'standard';
    if (night > 7) kind = ((night - 8) % 3 === 2) ? 'standard' : 'changed';
    nights.push({ night, kind, x: x0 + (night - 1) * step });
  }

  // 週の区切りと、見直しの日
  for (let week = 0; week < 4; week++) {
    const left = x0 + week * 7 * step - step / 2;
    const right = left + 7 * step;
    s += `<rect x="${left + 3}" y="${cy - 52}" width="${right - left - 6}" height="104" rx="6" fill="${BG}"/>`;
    s += text((left + right) / 2, cy + 80, 14, SUB, `第${week + 1}週`, { anchor: 'middle' });
  }
  [14, 21, 28].forEach((night) => {
    const x = x0 + (night - 1) * step;
    s += `<line x1="${x}" y1="${cy - 52}" x2="${x}" y2="${cy - 70}" stroke="${ACCENT}" stroke-width="2"/>`;
    s += text(x, cy - 80, 14.5, ACCENT, '見直し', { anchor: 'middle', weight: 700 });
  });

  nights.forEach((n) => {
    if (n.kind === 'standard') {
      s += `<circle cx="${n.x}" cy="${cy}" r="${r}" fill="${GRAY_DOT}"/>`;
    } else {
      s += `<circle cx="${n.x}" cy="${cy}" r="${r - 1.5}" fill="#FFFFFF" stroke="${ACCENT}" stroke-width="3"/>`;
    }
  });

  // 基準の括弧
  const bx1 = x0 - r;
  const bx2 = x0 + 6 * step + r;
  s += `<path d="M ${bx1} ${cy + 108} v 8 h ${bx2 - bx1} v -8" fill="none" stroke="${SUB}" stroke-width="1.5"/>`;
  s += text((bx1 + bx2) / 2, cy + 142, 15, INK, '基準', { anchor: 'middle', weight: 700 });
  s += text((bx1 + bx2) / 2, cy + 166, 13.5, SUB, '標準のすごし方で、夜のようすを見る', { anchor: 'middle' });
  const cx1 = x0 + 7 * step - r;
  const cx2 = x0 + 27 * step + r;
  s += `<path d="M ${cx1} ${cy + 108} v 8 h ${cx2 - cx1} v -8" fill="none" stroke="${SUB}" stroke-width="1.5"/>`;
  s += text((cx1 + cx2) / 2, cy + 142, 15, INK, '3 夜に 1 夜が標準。ほかの夜は、音か、音量の下げ方を、1 つだけ変える', { anchor: 'middle', weight: 700 });
  s += text((cx1 + cx2) / 2, cy + 166, 13.5, SUB, '見直しの日に、変えた夜を、日の近い標準の夜と組にして比べ、よく使う・外す・戻す・もう少し見る、を決める', { anchor: 'middle' });

  // 凡例
  s += `<circle cx="40" cy="${H - 22}" r="8" fill="${GRAY_DOT}"/>`;
  s += text(56, H - 17, 14, SUB, '標準の夜');
  s += `<circle cx="150" cy="${H - 22}" r="7" fill="#FFFFFF" stroke="${ACCENT}" stroke-width="3"/>`;
  s += text(166, H - 17, 14, SUB, '少し変えた夜');
  return svgWrap(W, H, s);
}

const FIGURES = [
  { name: 'architecture', svg: architecture(), width: 1200, height: 760 },
  { name: 'cycle', svg: cycle(), width: 1200, height: 440 },
];

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (response) => {
      let body = '';
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => resolve(JSON.parse(body)));
    }).on('error', reject);
  });
}

async function main() {
  const pages = [];
  for (const figure of FIGURES) {
    fs.writeFileSync(path.join(OUT_DIR, `${figure.name}.svg`), figure.svg);
    const html = `<!doctype html><meta charset="utf-8"><link href="https://fonts.googleapis.com/css2?family=Noto+Sans+JP:wght@400;500;700&display=swap" rel="stylesheet"><style>html,body{margin:0;background:#fff}</style>${figure.svg}`;
    const htmlPath = path.join(OUT_DIR, `${figure.name}.render.html`);
    fs.writeFileSync(htmlPath, html);
    pages.push({ ...figure, htmlPath });
  }
  const chrome = spawn(CHROME_PATH, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--allow-file-access-from-files',
    `--remote-debugging-port=${DEBUG_PORT}`, `--user-data-dir=${process.env.TEMP}\\yurune-diagrams-${Date.now()}`, 'about:blank'], { stdio: 'ignore' });
  let targets = [];
  for (let attempt = 0; attempt < 40 && targets.length === 0; attempt++) {
    try { targets = await fetchJson(`http://localhost:${DEBUG_PORT}/json`); } catch (error) { await sleep(250); }
  }
  const socket = new WebSocket(targets.find((target) => target.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve) => { socket.onopen = resolve; });
  let nextId = 0;
  const waiting = new Map();
  socket.onmessage = (message) => {
    const payload = JSON.parse(message.data);
    if (payload.id && waiting.has(payload.id)) { waiting.get(payload.id)(payload); waiting.delete(payload.id); }
  };
  const send = (method, params = {}) => new Promise((resolve) => {
    const id = ++nextId;
    waiting.set(id, resolve);
    socket.send(JSON.stringify({ id, method, params }));
  });
  await send('Page.enable');
  for (const page of pages) {
    await send('Emulation.setDeviceMetricsOverride', { width: page.width, height: page.height, deviceScaleFactor: SCALE, mobile: false });
    await send('Page.navigate', { url: 'file:///' + page.htmlPath.replace(/\\/g, '/') });
    await sleep(2500);
    await send('Runtime.evaluate', { expression: 'document.fonts.ready.then(() => document.fonts.size)', awaitPromise: true, returnByValue: true });
    await sleep(500);
    const shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(OUT_DIR, `${page.name}.png`), Buffer.from(shot.result.data, 'base64'));
    fs.rmSync(page.htmlPath);
    console.log(`saved ${page.name}.png`);
  }
  chrome.kill();
  process.exit(0);
}

main().catch((error) => { console.log('FAILED', error); process.exit(1); });
