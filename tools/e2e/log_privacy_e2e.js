// AT-06: 会話のあとに、データ（Firestore の全文書）と、アプリの記録（標準出力）を探して、会話の本文が残っていないことを確かめる。
// あわせて、記録が、トレース ID でつながり、種類と件数だけを持つことを確かめる。
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const BASE = 'http://localhost:18080';
const FIRESTORE = 'http://127.0.0.1:8080/v1/projects/yurune-test/databases/(default)/documents';
const TOMCAT_OUTPUT = process.argv[2];
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 9371;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const check = (name, ok, detail) => { const line = `${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`; results.push(line); console.log(line); };
const getJson = (url) => new Promise((resolve, reject) => { http.get(url, (res) => { let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve(JSON.parse(d))); }).on('error', reject); });

async function dumpAllDocuments(path = '') {
  // 全文書を、再帰的に文字列にして集める（コレクションを、たどれる所まで）
  const auth = { headers: { Authorization: 'Bearer owner' } };
  let text = '';
  async function visit(docPath) {
    const ids = await (await fetch(`${FIRESTORE}/${docPath}:listCollectionIds`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' }, body: '{"pageSize":100}' })).json();
    for (const id of ids.collectionIds || []) {
      let token = '';
      do {
        const r = await (await fetch(`${FIRESTORE}/${docPath}/${id}?pageSize=300&showMissing=true${token ? '&pageToken=' + token : ''}`, auth)).json();
        for (const d of r.documents || []) {
          text += JSON.stringify(d) + '\n';
          await visit(d.name.split('/documents/')[1]);
        }
        token = r.nextPageToken || '';
      } while (token);
    }
  }
  const root = await (await fetch(`${FIRESTORE}:listCollectionIds`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' }, body: '{"pageSize":100}' })).json();
  for (const id of root.collectionIds || []) {
    let token = '';
    do {
      const r = await (await fetch(`${FIRESTORE}/${id}?pageSize=300&showMissing=true${token ? '&pageToken=' + token : ''}`, auth)).json();
      for (const d of r.documents || []) { text += JSON.stringify(d) + '\n'; await visit(d.name.split('/documents/')[1]); }
      token = r.nextPageToken || '';
    } while (token);
  }
  return text;
}

(async () => {
  const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${PORT}`,
    '--window-size=480,900', '--user-data-dir=' + process.env.TEMP + '\\yurune-cdp-at06-' + Date.now(), 'about:blank'], { stdio: 'ignore' });
  let targets;
  for (let i = 0; i < 40; i++) { try { targets = await getJson(`http://localhost:${PORT}/json`); if (targets.length) break; } catch (e) {} await sleep(250); }
  const ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0; const pending = new Map(); const loadWaiters = [];
  ws.onmessage = (m) => { const msg = JSON.parse(m.data); if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); } if (msg.method === 'Page.loadEventFired') loadWaiters.splice(0).forEach((w) => w()); };
  const send = (method, params = {}) => new Promise((resolve) => { const i = ++id; pending.set(i, resolve); ws.send(JSON.stringify({ id: i, method, params })); });
  await send('Page.enable');
  // 音声認識と読み上げの差し替え。本物と同じく、start() のあと、聞き続ける（window.__rec が、聞いている最中のもの）。
  // window.__speak(文) が、「声で話した」こと。聞いていないときは、次に聞き始めたときに届く。
  // 読み上げ（音声・ブラウザ）は、すぐ終わる。window.__holdSpeech = true の間は、window.__endSpeech() を呼ぶまで終わらない。
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `
    window.__recStarts = 0; window.__rec = null; window.__nextUtterance = ''; window.__holdSpeech = false;
    window.__blockAudio = false; window.__played = 0; window.__spoken = 0; window.__webAudioVoices = 0; window.__webAudioEnded = 0;
    function deliver(session, text) {
      if (!session.__active || !session.onresult) return;
      var alternatives = [{ transcript: text }]; alternatives.isFinal = true;
      session.onresult({ resultIndex: 0, results: [alternatives] });
    }
    window.SpeechRecognition = function () {
      var self = this; self.__active = false;
      self.start = function () {
        window.__recStarts++; self.__active = true; window.__rec = self;
        if (window.__nextUtterance) { var text = window.__nextUtterance; window.__nextUtterance = ''; setTimeout(function () { deliver(self, text); }, 30); }
      };
      self.abort = self.stop = function () {
        self.__active = false; if (window.__rec === self) window.__rec = null;
        setTimeout(function () { if (self.onend) self.onend(); }, 0);
      };
    };
    window.__speak = function (text) { if (window.__rec) deliver(window.__rec, text); else window.__nextUtterance = text; };
    function finishLater(done) { if (window.__holdSpeech) { window.__endSpeech = done; } else { setTimeout(done, 150); } }
    // 端末が「さわる操作のない画面での再生」を許さない状態は、window.__blockAudio = true で再現する（audio 要素は拒まれ、ブラウザの読み上げは not-allowed になる）
    HTMLMediaElement.prototype.play = function () {
      var media = this;
      if (window.__blockAudio) return Promise.reject(new DOMException('blocked', 'NotAllowedError'));
      if (media.src && media.src.indexOf('blob:') === 0) window.__played++;
      setTimeout(function () { if (media.onplay) media.onplay(); }, 10);
      finishLater(function () { if (media.onended) media.onended(); });
      return Promise.resolve();
    };
    // 環境音と同じ出力で流す入口（nightsound.js の playVoice）を、数えるために包む
    document.addEventListener('DOMContentLoaded', function () {
      var night = window.yuruneNightSound;
      if (night && night.playVoice) {
        var original = night.playVoice;
        night.playVoice = function () {
          window.__webAudioVoices++;
          var promise = original.apply(night, arguments);
          promise.then(function () { window.__webAudioEnded++; }, function () {});
          return promise;
        };
      }
    });
    Object.defineProperty(window, 'speechSynthesis', { configurable: true, value: {
      speak: function (u) {
        if (window.__blockAudio) { setTimeout(function () { if (u.onerror) u.onerror({ error: 'not-allowed' }); }, 10); return; }
        window.__spoken++;
        setTimeout(function () { if (u.onstart) u.onstart(); }, 10); finishLater(function () { if (u.onend) u.onend(); });
      },
      cancel: function () {}
    } });` });
  const ev = async (expr) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true }); return r.result.exceptionDetails ? 'EXC' : r.result.result.value; };
  const go = async (p) => { const l = new Promise((r) => loadWaiters.push(r)); await send('Page.navigate', { url: BASE + p }); await Promise.race([l, sleep(8000)]); await sleep(500); };
  const waitLoad = async () => { const l = new Promise((r) => loadWaiters.push(r)); await Promise.race([l, sleep(8000)]); await sleep(500); };
  const replyCount = () => ev(`parseInt(document.getElementById('talkReply').getAttribute('data-n'), 10)`);
  const replyText = () => ev(`document.getElementById('talkReply').textContent`);
  async function say(utterance) {
    const before = await replyCount();
    await ev(`window.__speak(${JSON.stringify(utterance)})`);
    for (let i = 0; i < 40; i++) { await sleep(150); if ((await replyCount()) >= before + 1) break; }
    const reply = (await replyCount()) >= before + 1 ? await replyText() : undefined;
    await sleep(2300); return reply;
  }

  // 前の夜が残っていないように、いちど、ログインして、夜を終える
  await go('/login');
  await ev(`document.querySelector('[name=email]').value = 'demo@example.com'; document.querySelector('[name=password]').value = 'demo-pass-123'`);
  await ev(`document.querySelector('#action button').click()`); await waitLoad();
  await go('/night');
  if ((await ev(`!!document.querySelector('.endbtn')`)) === true) { await ev(`document.querySelector('.endbtn').click()`); await waitLoad(); }
  else if ((await ev(`!!document.getElementById('micButton')`)) === true) { await ev(`document.querySelector('form[action$="/night/mode"] .nightlink').click()`); await waitLoad(); await ev(`document.querySelector('.endbtn').click()`); await waitLoad(); }

  // 夜を会話で始めて、固有の文を話す
  await go('/prepare');
  await ev(`document.querySelector('input[name=mode][value=talk]').click()`);
  await ev(`document.querySelector('#action button').click()`); await waitLoad();
  const utterances = ['ひみつのことばZXQUNIQUEALPHA', '電話は090-1234-5678でメールはsecret.person@example.com', 'こんにちはZXQUNIQUEBETA'];
  const replies = [];
  for (const u of utterances) replies.push(await say(u));
  check('the conversation really happened (replies came back)', replies.every((r) => typeof r === 'string' && r.length > 0), JSON.stringify(replies).slice(0, 160));

  // 終える
  await ev(`document.querySelector('form[action$="/night/mode"] .nightlink').click()`); await waitLoad();
  await ev(`document.querySelector('.endbtn').click()`); await waitLoad();
  await sleep(500);

  // 探す
  const stored = await dumpAllDocuments();
  const logs = fs.readFileSync(TOMCAT_OUTPUT, 'utf8');
  const needles = ['ZXQUNIQUEALPHA', 'ZXQUNIQUEBETA', '090-1234-5678', '5678', 'secret.person', 'example.com', 'ひみつのことば', 'こんにちは', 'ここにいるよ'];
  // トレース ID・利用者のハッシュはランダムな 16 進数、時刻は数字の並びなので、短い needle（"5678" など）を、
  // たまたま含むことがある（本文の漏洩ではない誤検知）。この3種類の項目だけを除いて探す
  const logsWithoutTraceIds = logs.replace(/"traceId":"[0-9a-f]+"/g, '"traceId":"-"')
      .replace(/"logging\.googleapis\.com\/trace":"[^"]*"/g, '"logging.googleapis.com/trace":"-"')
      .replace(/"time":"[^"]*"/g, '"time":"-"')
      .replace(/"(user|uidHash)":"[0-9a-f]+"/g, '"$1":"-"');
  const storedHits = needles.filter((n) => stored.includes(n) && n !== 'example.com');
  const logHits = needles.filter((n) => logsWithoutTraceIds.includes(n) && n !== 'example.com');
  check('no conversation text or personal data is stored in Firestore', storedHits.length === 0, `${stored.split('\n').length} documents scanned; hits: ${JSON.stringify(storedHits)}`);
  check('no conversation text or personal data is in the application log', logHits.length === 0, `${logs.split('\n').length} lines scanned; hits: ${JSON.stringify(logHits)}`);

  // 記録の形
  const jsonLines = logs.split('\n').filter((l) => l.startsWith('{"severity"')).map((l) => JSON.parse(l));
  const conv = jsonLines.filter((l) => l.event === 'conversation');
  check('one conversation line per utterance exists', conv.length >= 3, String(conv.length));
  check('conversation lines carry only kinds and counts', conv.every((l) => Object.keys(l.detail).every((k) => ['input', 'reply', 'truncated', 'latencyMs', 'masked', 'outputViolation', 'down'].includes(k))), JSON.stringify(conv[0] && conv[0].detail));
  check('masking is recorded as a count by kind', conv.some((l) => l.detail.masked && l.detail.masked.phone === 1 && l.detail.masked.email === 1), JSON.stringify(conv.map((l) => l.detail.masked)));
  const requests = jsonLines.filter((l) => l.event === 'request' && l.detail.path === '/night/talk' && l.detail.status === 200);
  check('request lines exist for the talk endpoint with status and time', requests.length >= 3 && requests.every((l) => l.detail.status === 200 && typeof l.detail.latencyMs === 'number'), String(requests.length));
  const sameTrace = requests.every((r) => conv.some((c) => c.traceId === r.traceId));
  check('the conversation line and the request line share one trace id', sameTrace && requests.every((r) => /^[0-9a-f]{32}$/.test(r.traceId)));
  check('request lines have no query string, parameters or body fields', jsonLines.filter((l) => l.event === 'request').every((l) => Object.keys(l.detail).every((k) => ['method', 'path', 'status', 'latencyMs', 'user'].includes(k))));
  const decisions = jsonLines.filter((l) => l.event === 'decision');
  check('decision lines exist and carry only phase and outcome', decisions.length >= 1 && decisions.every((l) => Object.keys(l.detail).sort().join() === 'accepted,phase'), String(decisions.length));
  check('user ids in the log are hashed (not the raw uid)', jsonLines.filter((l) => l.detail && l.detail.user).every((l) => /^[0-9a-f]{16}$/.test(l.detail.user)));
  const auth = jsonLines.filter((l) => l.event === 'auth');
  check('auth events record the action and outcome but not the email', auth.length >= 1 && !JSON.stringify(auth).includes('demo@example.com'), JSON.stringify(auth[0] && auth[0].detail));

  // 運用の記録（可観測性）: agent（道具の呼び出し）、llm（LLM 呼び出し）、control（制御の状態）のログの形と、本文が無いこと
  const AGENT_FIELDS = ['tool', 'accepted', 'rejectReason', 'source', 'latencyMs', 'fallbackReason'];
  const LLM_FIELDS = ['outcome', 'latencyMs', 'model', 'purpose', 'promptTokens', 'candidateTokens'];
  const CONTROL_FIELDS = ['kind', 'state', 'rejectedCount'];
  const agentLines = jsonLines.filter((l) => l.event === 'agent');
  const llmLines = jsonLines.filter((l) => l.event === 'llm');
  const controlLines = jsonLines.filter((l) => l.event === 'control');
  check('agent lines exist for the tool calls made while switching mode and ending the night',
      agentLines.length >= 1, String(agentLines.length));
  check('agent lines carry only known fields, and a fixed source (llm/rule/fallback)',
      agentLines.every((l) => Object.keys(l.detail).every((k) => AGENT_FIELDS.includes(k))
          && ['llm', 'rule', 'fallback'].includes(l.detail.source)),
      JSON.stringify(agentLines[0] && agentLines[0].detail));
  check('llm lines exist for the talk LLM calls', llmLines.length >= 1, String(llmLines.length));
  check('llm lines carry only known fields (no free text)',
      llmLines.every((l) => Object.keys(l.detail).every((k) => LLM_FIELDS.includes(k))),
      JSON.stringify(llmLines[0] && llmLines[0].detail));
  check('control lines, if any appeared, carry only known fields',
      controlLines.every((l) => Object.keys(l.detail).every((k) => CONTROL_FIELDS.includes(k))),
      String(controlLines.length));
  const observability = [...agentLines, ...llmLines, ...controlLines];
  const obsHits = needles.filter((n) => n !== 'example.com' && observability.some((l) => JSON.stringify(l).includes(n)));
  check('agent/llm/control lines carry no utterance text or personal data', obsHits.length === 0, JSON.stringify(obsHits));

  ws.close(); chrome.kill();
  process.exit(results.some((r) => r.startsWith('FAIL')) ? 1 : 0);
})().catch((e) => { console.log('SCRIPT ERROR', e); process.exit(1); });
