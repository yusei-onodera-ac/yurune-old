// 夜の会話（声 → 文字 → LLM → 検査 → 返事）を、実際のブラウザで通して確かめる。
// アプリの接続先は、擬似の Gemini / Model Armor / Text-to-Speech（fake_google.js）。音声認識は、ブラウザ側を差し替えて再現する。
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');
const BASE = 'http://localhost:18080';
const FAKE = 'http://127.0.0.1:18090';
const AUTH = 'http://127.0.0.1:9099';
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 9361;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const check = (name, ok, detail) => { const line = `${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`; results.push(line); console.log(line); };
const getJson = (url) => new Promise((resolve, reject) => { http.get(url, (res) => { let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve(JSON.parse(d))); }).on('error', reject); });
const fake = async (p) => (await fetch(FAKE + p, { method: 'POST' })).json();
const fakeState = async () => (await fetch(FAKE + '/state')).json();

const T_MED = 'つらさが続くときは、医師に相談してください。ここでは、病気や薬のことには答えられません。';
const T_FALLBACK = 'うまくお答えできませんでした。つらさが続くときは、医師に相談してください。';
const T_DOWN = 'いまはお話しできません。';

(async () => {
  const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${PORT}`,
    '--window-size=480,900', '--user-data-dir=' + process.env.TEMP + '\\yurune-cdp-step4-' + Date.now(), 'about:blank'], { stdio: 'ignore' });
  let targets;
  for (let i = 0; i < 40; i++) { try { targets = await getJson(`http://localhost:${PORT}/json`); if (targets.length) break; } catch (e) {} await sleep(250); }
  const ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0; const pending = new Map(); const loadWaiters = []; const violations = []; const dialogs = []; const responses = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
    if (msg.method === 'Page.loadEventFired') loadWaiters.splice(0).forEach((w) => w());
    if (msg.method === 'Log.entryAdded') { const e = msg.params.entry; if (/Content Security Policy|Refused to/i.test(e.text)) violations.push(e.text.slice(0, 160)); }
    if (msg.method === 'Page.javascriptDialogOpening') dialogs.push(msg.params.message);
  };
  const send = (method, params = {}) => new Promise((resolve) => { const i = ++id; pending.set(i, resolve); ws.send(JSON.stringify({ id: i, method, params })); });
  await send('Page.enable'); await send('Log.enable'); await send('Network.enable');
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
  const ev = async (expr) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
    return r.result.exceptionDetails ? 'EXC ' + JSON.stringify(r.result.exceptionDetails.exception && r.result.exceptionDetails.exception.description).slice(0, 200) : r.result.result.value; };
  const go = async (p) => { const l = new Promise((r) => loadWaiters.push(r)); await send('Page.navigate', { url: BASE + p }); await Promise.race([l, sleep(8000)]); await sleep(500); };
  const waitLoad = async () => { const l = new Promise((r) => loadWaiters.push(r)); await Promise.race([l, sleep(8000)]); await sleep(500); };
  const here = () => ev('location.pathname');
  const shot = async (name) => { const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(path.join(process.env.SHOTS_DIR || require('os').tmpdir(), name), Buffer.from(r.result.data, 'base64')); };
  const fill = (name, value) => ev(`document.querySelector('[name=${name}]').value = ${JSON.stringify(value)}`);
  const submitMain = async () => { await ev(`document.querySelector('#action button').click()`); await waitLoad(); };

  // 声で話しかけて（ボタンは押さない）、ぽてねの返事が 1 つ増えるのを待つ。返事の文を返す（画面には、最新の返事 1 つだけが出る）
  const replyCount = () => ev(`parseInt(document.getElementById('talkReply').getAttribute('data-n'), 10)`);
  const replyText = () => ev(`document.getElementById('talkReply').textContent`);
  const hintText = () => ev(`document.getElementById('micHint').textContent`);
  const recording = () => ev(`window.__rec !== null`);
  async function waitFor(predicate, limitMs = 5000) {
    for (let waited = 0; waited < limitMs; waited += 100) { if (await predicate()) return true; await sleep(100); }
    return false;
  }
  async function say(utterance, waitMs = 2300) {
    const before = await replyCount();
    await ev(`window.__speak(${JSON.stringify(utterance)})`);
    for (let i = 0; i < 40; i++) { await sleep(150); if ((await replyCount()) >= before + 1) break; }
    const reply = (await replyCount()) >= before + 1 ? await replyText() : undefined;
    const shownUtterance = (await ev('document.body.innerText')).includes(utterance);
    await sleep(waitMs); // 会話の最小の間隔（2 秒）をあける
    return { reply, shownUtterance, count: (await replyCount()) - before };
  }

  // ログイン（審査用アカウント）→ 夜の準備 → 会話で始める
  await go('/login');
  await fill('email', 'demo@example.com'); await fill('password', 'demo-pass-123');
  await submitMain();
  check('logged in as the review account', (await here()) === '/home', await here());
  await go('/prepare');
  await ev(`document.querySelector('input[name=mode][value=talk]').click()`);
  await ev(`document.querySelector('#action button').click()`);
  await waitLoad();
  check('night starts in talk mode', (await here()) === '/night' && (await ev(`!!document.getElementById('nightTalk')`)) === true);
  check('there is no text input in the talk screen (voice only)', (await ev(`document.querySelectorAll('input[type=text], input:not([type]), textarea').length`)) === 0);
  const duckNow = () => ev(`window.yuruneNightSound ? window.yuruneNightSound.status().duck : null`);
  // 0 ボタンを押さなくても、会話の画面を開くと、聞き始める
  check('listening starts by itself when the talk screen opens (no button pressed)', await waitFor(async () => (await ev('window.__recStarts')) >= 1, 3000));
  check('the hint says it is listening and the mic is shown as on', (await hintText()) === '聞いています…' && (await ev(`document.getElementById('micButton').getAttribute('aria-pressed')`)) === 'true', await hintText());
  check('while listening, the ambient sound is lowered (ducked to 0.2) so it does not cover the voice', (await duckNow()) === 0.2, String(await duckNow()));
  await shot('s4_talk_start.png');
  await fake('/reset');

  // 1 ふつうの会話
  let r = await say('こんにちは');
  check('a plain utterance gets the model reply', r.reply === 'こんにちは。ここにいるよ。', r.reply);
  check('the screen shows only the latest reply (one line)', (await ev(`document.querySelectorAll('.chat .bub').length`)) === 1);
  let st = await fakeState();
  check('the model was called once', st.llmCalls === 1, String(st.llmCalls));
  check('the request separated the utterance as untrusted data', /<utterance>こんにちは<\/utterance>/.test(st.lastRequest) && /YRN-/.test(st.lastRequest), '');
  check('the system instruction carries tonight\'s sound and no other facts', /雨の音|波の音|森の音/.test(st.lastRequest));
  await shot('s4_talk_reply.png');

  // 1b ハンズフリー: 話し終えたら、また聞く。ぽてねが話している間は聞かない。声は「反応」と数える
  check('after the reply, listening resumes by itself', await waitFor(recording, 4000));
  const startsBefore = await ev('window.__recStarts');
  await ev(`window.__holdSpeech = true`);
  const keyOf = await ev(`'yurune.lastReactionAt.' + document.getElementById('nightObserve').getAttribute('data-started-at')`);
  await ev(`sessionStorage.setItem(${JSON.stringify(keyOf)}, String(Date.now() - 120000))`);
  r = await say('もうひとつ');
  check('a spoken utterance counts as a reaction (the no-reaction timer restarts)', (await ev(`Date.now() - parseInt(sessionStorage.getItem(${JSON.stringify(keyOf)}), 10)`)) < 10000, '');
  check('while the pot is speaking, the device does not listen (it would hear its own voice)', (await recording()) === false && (await hintText()) === 'ぽてねの番だよ', await hintText());
  await ev(`window.__endSpeech()`); await ev(`window.__holdSpeech = false`);
  check('when the pot has finished speaking, listening resumes', await waitFor(recording, 4000) && (await ev('window.__recStarts')) > startsBefore);
  await sleep(2300);
  const bubblesBeforeNoise = await replyCount();
  await ev(`window.__speak('あ')`); await sleep(1200);
  check('a one-character noise is not sent', (await replyCount()) === bubblesBeforeNoise);
  // 利用者が「お休み」にする
  await ev(`document.getElementById('micButton').click()`); await sleep(300);
  check('pressing the mic rests the listening (stops, hint and state change)', (await recording()) === false && /お休み/.test(await hintText()) && (await ev(`document.getElementById('micButton').getAttribute('aria-pressed')`)) === 'false', await hintText());
  check('resting the listening gives the ambient sound back (duck 1)', (await duckNow()) === 1, String(await duckNow()));
  await ev(`window.__speak('お休みのあいだの声')`); await sleep(1200);
  check('what is said while resting is not sent', (await replyCount()) === bubblesBeforeNoise);
  await ev(`document.getElementById('micButton').click()`);
  check('listening again lowers the ambient sound again', (await duckNow()) === 0.2, String(await duckNow()));
  check('pressing again listens again (and the waiting words are not lost)', await waitFor(async () => (await replyCount()) >= bubblesBeforeNoise + 1, 6000));
  await sleep(2300);
  // 声がないまま続いたら、自分で止まる（確認のため、10 分を 1.5 秒にする）
  await waitFor(recording, 4000);
  await ev(`document.getElementById('nightTalk').setAttribute('data-listen-idle-ms', '1500')`);
  check('after a long silence the listening stops by itself and says so', await waitFor(async () => (await recording()) === false && /お休み/.test(await hintText()), 9000), await hintText());
  await ev(`document.getElementById('nightTalk').removeAttribute('data-listen-idle-ms')`);
  await ev(`document.getElementById('micButton').click()`);
  check('the mic button brings the listening back after the silence', await waitFor(recording, 4000));
  // 画面が隠れたら聞かない
  await ev(`Object.defineProperty(document, 'hidden', { configurable: true, get: function () { return true; } }); document.dispatchEvent(new Event('visibilitychange'))`);
  await sleep(300);
  check('when the screen is hidden the device stops listening', (await recording()) === false);
  await ev(`Object.defineProperty(document, 'hidden', { configurable: true, get: function () { return false; } }); document.dispatchEvent(new Event('visibilitychange'))`);
  check('when the screen is back the device listens again', await waitFor(recording, 4000));
  await sleep(1500);

  // 1c 声を出せない端末（さわる操作のない画面での再生を許さない）: 返事は吹き出しに出し、さわったときに声にする
  await ev(`window.__blockAudio = true`);
  const playedBefore = await ev('window.__played + window.__spoken + window.__webAudioEnded');
  const blockedReply = await say('声が出ない端末です');
  check('even when the voice is blocked, the reply is shown as text', !!blockedReply.reply && blockedReply.reply !== T_DOWN, String(blockedReply.reply));
  check('the hint asks the user to touch the screen (no silent failure)', (await hintText()) === '画面にさわると、ぽてねの声が出るよ', await hintText());
  check('nothing was played while the device blocked it', (await ev('window.__played + window.__spoken + window.__webAudioEnded')) === playedBefore);
  await ev(`window.__blockAudio = false`);
  // 本物のさわる操作（CDP の入力）を送る
  const touch = async () => { await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: 200, y: 400, button: 'left', clickCount: 1 }); await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: 200, y: 400, button: 'left', clickCount: 1 }); };
  await touch();
  check('touching the screen plays the held reply', await waitFor(async () => (await ev('window.__played + window.__spoken + window.__webAudioEnded')) > playedBefore, 5000));
  check('after that the hint goes back to listening', await waitFor(async () => (await hintText()) === '聞いています…', 5000), await hintText());
  await sleep(2300);

  // 1d 一度さわったあとの声は、環境音と同じ出力（WebAudio）で流れる
  const viaWebAudioBefore = await ev('window.__webAudioEnded');
  await say('もうひとつ話すよ');
  check('after one touch the voice goes through the same output as the ambient sound', await waitFor(async () => (await ev('window.__webAudioEnded')) > viaWebAudioBefore, 6000), 'ended=' + (await ev('window.__webAudioEnded')));
  await sleep(1500);

  // 2 医療領域: LLM を呼ばない
  await fake('/reset');
  r = await say('睡眠薬はどうですか');
  check('a medical topic gets the fixed reply (T-MED)', r.reply === T_MED, r.reply);
  st = await fakeState();
  check('the model was not called for the medical topic', st.llmCalls === 0 && st.armorCalls === 0, `${st.llmCalls}/${st.armorCalls}`);

  // 3 LLM が違反した出力 → 差し替え
  r = await say('危険なはなし');
  check('a violating model output is replaced by the fallback (T-FALLBACK)', r.reply === T_FALLBACK, r.reply);
  check('the violating text never reached the screen', !/うつ病/.test(await ev('document.body.innerText')));

  // 4 返事の中の記号は、文字として表示（画面にスクリプトを混ぜられない）
  r = await say('xssのテスト');
  check('markup in a model reply is shown as plain text', r.reply === '<img src=x onerror=stealcookies>', r.reply);
  check('no element was created from the reply', (await ev(`document.querySelectorAll('.bub img, .bub script').length`)) === 0);
  r = await say('<script>alert(1)</script>');
  check('an utterance with markup is not shown and creates no element', r.shownUtterance === false && (await ev(`document.querySelectorAll('.chat script, .chat img').length`)) === 0);
  check('no inline script exists and no dialog opened', (await ev(`document.querySelectorAll('script:not([src])').length`)) === 0 && dialogs.length === 0);

  // 5 個人情報は伏せ字にして送る
  await fake('/reset');
  r = await say('電話は090-1234-5678です');
  st = await fakeState();
  check('personal information is masked before it leaves for the model', st.llmCalls === 1 && !/090|1234/.test(st.lastRequest) && /○○/.test(st.lastUtterance), st.lastUtterance);

  // 6 数字は、コードが差し込む
  r = await say('のこりの時間は');
  check('numbers are filled in by code, not written by the model', /^あと\d+分だよ。$/.test(r.reply), r.reply);

  // 7 検査サービス（Model Armor）が止める
  await fake('/reset');
  r = await say('ARMOR_BLOCK お願い');
  check('the inspection service can stop an utterance before the model', r.reply === T_FALLBACK, r.reply);
  st = await fakeState();
  check('the model was not called after the inspection service blocked', st.llmCalls === 0 && st.armorCalls >= 1, `${st.llmCalls}/${st.armorCalls}`);

  // 8 呼び出しの間隔: 続けて送ると、2 つ目は定型文
  await fake('/reset');
  // （声で話すときは、返事を聞き終えるまで聞かないので、続けざまの呼び出しは、画面を通さずに送って確かめる）
  const talkCall = (text) => ev(`fetch('/night/talk', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': document.querySelector('meta[name=csrf-token]').content }, body: JSON.stringify({ text: ${JSON.stringify(text)}, history: [] }) }).then(function (x) { return x.json(); })`);
  await talkCall('ひとつめ');
  const quick = await talkCall('ふたつめ');
  check('a second call within the minimum interval gets the down template', quick.reply === T_DOWN, quick.reply);
  await sleep(2300);

  // 9 CSRF と認証
  const noToken = await ev(`fetch('/night/talk', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"text":"x"}' }).then(function (x) { return x.status; })`);
  check('talk without the CSRF token is refused', noToken === 403, String(noToken));
  const anonymous = await fetch(BASE + '/night/talk', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"text":"x"}' });
  check('talk without logging in is refused', anonymous.status === 401 || anonymous.status === 403, String(anonymous.status));
  const huge = await ev(`fetch('/night/talk', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': document.querySelector('meta[name=csrf-token]').content }, body: JSON.stringify({ text: 'あ'.repeat(5000) }) }).then(function (x) { return x.status; })`);
  check('an oversized request is refused (400)', huge === 400, String(huge));
  const bad = await ev(`fetch('/night/talk', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': document.querySelector('meta[name=csrf-token]').content }, body: '{"text":5}' }).then(function (x) { return x.status; })`);
  check('a malformed request is refused (400)', bad === 400, String(bad));

  // 10 サーバーの読み上げ: 音声が返事に添えられる
  await fake('/reset');
  const withAudio = await ev(`fetch('/night/talk', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': document.querySelector('meta[name=csrf-token]').content }, body: JSON.stringify({ text: 'こんにちは', history: [] }) }).then(function (x) { return x.json(); })`);
  check('the server speech is attached to the reply', withAudio.state === 'ok' && withAudio.speak === true && typeof withAudio.audio === 'string' && withAudio.audio.length > 0, JSON.stringify(withAudio).slice(0, 120));
  await sleep(2300);
  await fake('/control?tts=down');
  const noAudio = await ev(`fetch('/night/talk', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': document.querySelector('meta[name=csrf-token]').content }, body: JSON.stringify({ text: 'こんにちは', history: [] }) }).then(function (x) { return x.json(); })`);
  check('when speech synthesis is down the text reply still works (the device speaks)', noAudio.state === 'ok' && noAudio.reply === 'こんにちは。ここにいるよ。' && !noAudio.audio, JSON.stringify(noAudio).slice(0, 120));
  await fake('/control?tts=up');
  await sleep(2300);

  // 11 検査サービスが止まったら、会話を止める（T-DOWN）
  await fake('/reset');
  await fake('/control?armor=down');
  r = await say('こんにちは');
  check('when the inspection service is down the conversation stops (T-DOWN)', r.reply === T_DOWN, r.reply);
  st = await fakeState();
  check('the model is not called while the inspection service is down', st.llmCalls === 0, String(st.llmCalls));
  await fake('/control?armor=up');

  // 12 LLM が止まったら、定型文 T-DOWN。でも、夜は止まらない
  await fake('/control?llm=down');
  r = await say('こんにちは');
  check('when the model is down the conversation answers with the down template', r.reply === T_DOWN, r.reply);
  r = await say('こんにちは'); r = await say('こんにちは');
  st = await fakeState();
  const callsAtOpen = st.llmCalls;
  r = await say('こんにちは');
  st = await fakeState();
  check('after repeated failures the model is not called any more (circuit breaker)', st.llmCalls === callsAtOpen && r.reply === T_DOWN, `${callsAtOpen} -> ${st.llmCalls}`);
  check('the night page still shows the clock and the end control after the outage', (await ev(`!!document.querySelector('.pot')`)) === true);
  await ev(`document.querySelector('form[action$="/night/mode"] .nightlink').click()`);
  await waitLoad();
  check('the user can return to sound only and the clock runs during the outage', (await ev(`!!document.getElementById('nightClock')`)) === true);
  await ev(`document.querySelector('.endbtn').click()`);
  await waitLoad();
  check('the night ends normally during the outage', (await here()) === '/home', await here());
  await go('/log');
  check('records stay available during the outage', (await here()) === '/log' && (await ev(`document.querySelectorAll('.dayitem, .row').length`)) > 0);
  await fake('/control?llm=up');

  // 守りの記録: 会話の本文は、どこにも出ない
  check('no CSP violation', violations.length === 0, violations.join(' | '));
  ws.close(); chrome.kill();
  console.log(results.join('\n'));
  process.exit(0);
})().catch((e) => { console.log(results.join('\n')); console.log('SCRIPT ERROR', e); process.exit(1); });
