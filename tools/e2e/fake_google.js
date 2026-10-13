// 擬似の Google サービス（Gemini、Model Armor、Text-to-Speech）。ブラウザの通し確認で、アプリの接続先として使う。
const http = require('http');
// 短い無音の WAV（8kHz・モノラル・16bit・0.1 秒）。ブラウザが、実際にデコードして鳴らせる音声（本物の読み上げは mp3 だが、デコードは中身で判断される）
function makeSilentWav() {
  const sampleRate = 8000;
  const samples = sampleRate / 10;
  const dataSize = samples * 2;
  const buffer = Buffer.alloc(44 + dataSize);
  buffer.write('RIFF', 0); buffer.writeUInt32LE(36 + dataSize, 4); buffer.write('WAVE', 8);
  buffer.write('fmt ', 12); buffer.writeUInt32LE(16, 16); buffer.writeUInt16LE(1, 20); buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(sampleRate, 24); buffer.writeUInt32LE(sampleRate * 2, 28); buffer.writeUInt16LE(2, 32); buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36); buffer.writeUInt32LE(dataSize, 40);
  return buffer;
}
const SILENT_WAV = makeSilentWav();
const state = { llm: 'up', armor: 'up', tts: 'up', llmCalls: 0, agentCalls: 0, armorCalls: 0, ttsCalls: 0, lastUtterance: '', lastRequest: '' };

function readBody(req) { return new Promise((resolve) => { let d = ''; req.on('data', (c) => (d += c)); req.on('end', () => resolve(d)); }); }
function json(res, status, obj) { const b = JSON.stringify(obj); res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(b); }
function candidate(text) { return { candidates: [{ content: { role: 'model', parts: [{ text }] } }] }; }

function replyFor(utterance) {
  if (utterance.includes('こんにちは')) return 'こんにちは。ここにいるよ。';
  if (utterance.includes('危険')) return 'うつ病かもしれません';
  if (utterance.includes('xss')) return '<img src=x onerror=stealcookies>';
  if (utterance.includes('時間')) return 'あと{{minutes_left}}分だよ。';
  return 'うん、ここにいるよ。';
}

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const body = await readBody(req);
  if (url.pathname === '/state') return json(res, 200, state);
  if (url.pathname === '/control') { for (const [k, v] of url.searchParams) { state[k] = v; } return json(res, 200, state); }
  if (url.pathname === '/reset') { Object.assign(state, { llmCalls: 0, agentCalls: 0, armorCalls: 0, ttsCalls: 0, lastUtterance: '', lastRequest: '' }); return json(res, 200, state); }
  if (/:generateContent$/.test(url.pathname)) {
    const request = JSON.parse(body);
    if (request.tools) { state.agentCalls++; return json(res, 200, candidate('様子を見ます')); }
    state.llmCalls++;
    state.lastRequest = body;
    const last = request.contents[request.contents.length - 1].parts[0].text;
    const m = last.match(/<utterance>([\s\S]*?)<\/utterance>\s*$/);
    state.lastUtterance = m ? m[1] : last;
    if (state.llm === 'down') return json(res, 503, { error: 'down' });
    return json(res, 200, candidate(replyFor(state.lastUtterance)));
  }
  if (/:sanitize(UserPrompt|ModelResponse)$/.test(url.pathname)) {
    state.armorCalls++;
    if (state.armor === 'down') return json(res, 503, { error: 'down' });
    const parsed = JSON.parse(body);
    const text = (parsed.userPromptData || parsed.modelResponseData).text;
    const hit = /ARMOR_BLOCK/.test(text);
    return json(res, 200, { sanitizationResult: { filterMatchState: hit ? 'MATCH_FOUND' : 'NO_MATCH_FOUND', invocationResult: 'SUCCESS',
      filterResults: { pi_and_jailbreak: { piAndJailbreakFilterResult: { executionState: 'EXECUTION_SUCCESS', matchState: hit ? 'MATCH_FOUND' : 'NO_MATCH_FOUND' } } } } });
  }
  if (url.pathname === '/v1/text:synthesize') {
    state.ttsCalls++;
    if (state.tts === 'down') return json(res, 503, { error: 'down' });
    return json(res, 200, { audioContent: SILENT_WAV.toString('base64') });
  }
  json(res, 404, { error: 'not found', path: url.pathname });
}).listen(18090, '127.0.0.1', () => console.log('fake google on 18090'));
