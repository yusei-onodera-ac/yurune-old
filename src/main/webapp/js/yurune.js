/* 画面の動き。ページごとに必要なものだけが、data 属性を手がかりに動く。 */
(function () {
  'use strict';

  var HOP_MS = 650;
  var HEART_MS = 1000;
  var HEART_DRIFT_PX = 30;
  var CLOCK_TICK_MS = 1000;
  var TALK_FIRST_MS = 4200;
  var TALK_HISTORY_MAX = 4;
  var TALK_SPEECH_RATE = 0.9;
  var TALK_SPEECH_PITCH = 0.8;
  var TALK_SPEECH_VOLUME = 0.5;
  var TALK_HINT_RESET_MS = 2500;
  /* 話し終えたあと、聞き直すまでの間。ぽてね自身の声のしっぽを、拾わないため */
  var TALK_RESTART_MS = 600;
  /* 聞き始めてすぐ終わってしまうときの間。無駄に回り続けない */
  var TALK_RETRY_MS = 3000;
  var TALK_RETRY_BELOW_MS = 1000;
  /* 読み上げの「終わり」が届かなくても、聞くのを再開する上限 */
  var TALK_SPEAK_MAX_MS = 20000;
  /* ブラウザの読み上げが、この時間のうちに始まらなければ、端末に止められたとみなす */
  var TALK_SPEECH_START_MS = 4000;
  /* 声がないまま、このくらい続いたら、聞くのをお休みする。眠ったあとも、マイクを開けたままにしない。仮置き */
  var TALK_LISTEN_IDLE_MS = 10 * 60000;
  var TALK_IDLE_CHECK_MS = 5000;
  /* 1 文字だけの聞き取りは、物音の聞き違いとみなして、送らない */
  var TALK_MIN_CHARS = 2;
  /* 声を聞いている間の、環境音の大きさ（普段に掛ける割合。仮置き）。環境音が重なると、声を文字にする精度が落ちる */
  var TALK_DUCK = 0.2;
  var TEXT_DOWN = 'いまはお話しできません。';
  var TEXT_HINT_LISTENING = '聞いています…';
  var TEXT_HINT_REPLYING = 'ぽてねの番だよ';
  var TEXT_HINT_TAP = '画面にさわると、ぽてねの声が出るよ';
  var TEXT_HINT_PAUSED = 'お休み中。押すと、また聞くよ';
  var TEXT_HINT_BLOCKED = 'マイクが使えません。押すと、もう一度ためします';
  var TEXT_HINT_MISSED = '聞き取れませんでした';
  var TEXT_HINT_UNSUPPORTED = 'この端末では、声の入力が使えません';
  var TEXT_MIC_PAUSE = '聞くのをお休みする';
  var TEXT_MIC_RESUME = 'また聞く';
  var MS_PER_SECOND = 1000;
  var MS_PER_MINUTE = 60000;
  var PERCENT_MAX = 100;
  /* 夜の画面が上限を渡せなかったときに使う、仕様書の初期値（60%）。大きくしない側に倒す */
  var VOLUME_CAP_FALLBACK = 0.6;

  function each(list, callback) {
    Array.prototype.forEach.call(list, callback);
  }

  /* 状態を変える送信（fetch）には、画面が持つ CSRF トークンを付ける */
  function postHeaders(contentType) {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return {
      'Content-Type': contentType || 'application/x-www-form-urlencoded',
      'X-CSRF-Token': meta ? meta.getAttribute('content') : ''
    };
  }

  /* ぽてねをなでると、跳ねてハートが出て、吹き出しの文が変わる */
  function bindPot() {
    var bubble = document.getElementById('say');
    each(document.querySelectorAll('.potwrap'), function (wrap) {
      var index = 0;
      wrap.addEventListener('click', function () {
        var pot = wrap.querySelector('.pot');
        pot.classList.remove('hop');
        void pot.getBoundingClientRect();
        pot.classList.add('hop');
        setTimeout(function () { pot.classList.remove('hop'); }, HOP_MS);

        var heart = document.createElement('span');
        heart.className = 'heart';
        heart.textContent = '♡';
        heart.style.setProperty('--dx', (Math.random() * HEART_DRIFT_PX - HEART_DRIFT_PX / 2) + 'px');
        wrap.appendChild(heart);
        setTimeout(function () { heart.remove(); }, HEART_MS);

        var says = bubble && bubble.getAttribute('data-says');
        if (says && says.indexOf('|') >= 0) {
          var messages = says.split('|');
          index = (index + 1) % messages.length;
          bubble.textContent = messages[index];
        }
      });
    });
  }

  /* 切り替え。ボタンの data-panel と、本文の data-seg-panel("グループ名:パネル名") を結ぶ */
  function bindSegments() {
    each(document.querySelectorAll('[data-seg]'), function (seg) {
      var group = seg.getAttribute('data-seg');
      each(seg.querySelectorAll('button[data-panel]'), function (button) {
        button.addEventListener('click', function () {
          each(seg.querySelectorAll('button'), function (other) {
            other.setAttribute('aria-pressed', other === button);
          });
          each(document.querySelectorAll('[data-seg-panel^="' + group + ':"]'), function (panel) {
            panel.hidden = panel.getAttribute('data-seg-panel') !== group + ':' + button.getAttribute('data-panel');
          });
          applyTabSideEffects(button);
        });
      });
    });
  }

  /* タブを変えたとき、ぽてねの一言と主操作の出し入れを合わせる */
  function applyTabSideEffects(button) {
    var bubble = document.getElementById('say');
    var action = document.getElementById('action');
    var say = button.getAttribute('data-say');
    var panelName = button.getAttribute('data-panel');

    var panel = document.querySelector('[data-seg-panel$=":' + panelName + '"]');
    var inner = panel && panel.querySelector('[data-seg] button[aria-pressed="true"][data-say]');
    if (inner) {
      say = inner.getAttribute('data-say');
    }
    if (bubble && say) {
      bubble.textContent = say;
    }
    if (action && button.hasAttribute('data-action')) {
      action.hidden = button.getAttribute('data-action') !== 'show';
    }
  }

  /* 設定のスイッチ。押すとその場で切り替えて、設定を保存する。保存できなかったときは、元に戻して知らせる。
     スクリプトが動かないときは、フォームがそのまま送られ、同じ内容が保存される。 */
  function bindSwitches() {
    var failure = document.getElementById('settingsError');
    each(document.querySelectorAll('form[data-switch]'), function (form) {
      form.addEventListener('submit', function (event) {
        event.preventDefault();
        var toggle = form.querySelector('.sw');
        var item = form.querySelector('input[name=item]');
        var next = form.querySelector('input[name=value]');
        var was = toggle.getAttribute('aria-checked') === 'true';
        toggle.disabled = true;
        toggle.setAttribute('aria-checked', String(!was));
        if (failure) {
          failure.hidden = true;
        }
        /* 保存できたときは、サーバーが設定の画面への移動を返す。その移動は追わない（manual） */
        fetch(form.getAttribute('action'), {
          method: 'POST',
          headers: postHeaders(),
          body: 'item=' + encodeURIComponent(item.value) + '&value=' + encodeURIComponent(next.value),
          redirect: 'manual'
        }).then(function (response) {
          if (response.type !== 'opaqueredirect' && !response.ok) {
            throw new Error('not saved');
          }
          next.value = was ? 'on' : 'off';
        }).catch(function () {
          toggle.setAttribute('aria-checked', String(was));
          if (failure) {
            failure.hidden = false;
          }
        }).then(function () {
          toggle.disabled = false;
        });
      });
    });
  }

  /* 夜を終えるとき、音を下げる秒数の既定（サーバーが残りの時間を返さなかったとき） */
  var NIGHT_LEAVE_FADE_SEC = 2.5;
  /* 無反応で端末が終えるときに下げる時間（15 分。NightConstants.GOODNIGHT_FADE_MINUTES と同じ） */
  var NIGHT_GOODNIGHT_FADE_SEC = 15 * 60;
  /* 音の端末（nightsound.js）へ渡す出来事の名前。この 2 つで、画面の読み込みの中の音とつながる */
  var NIGHT_STATE_EVENT = 'yurune:night-state';
  var NIGHT_FADE_EVENT = 'yurune:night-fade';
  /* 声が聞こえたときの出来事。画面に触れなくても、「反応があった」と数えるため（夜の観測の画面とつながる） */
  var NIGHT_REACTION_EVENT = 'yurune:reaction';

  /* 夜の音量の上限（0〜1）。サーバーが、設定と運用の上限から決めて、夜の画面に渡す。端末の再生は、これをこえない */
  function readVolumeCap() {
    var holder = document.getElementById('nightObserve');
    var percent = holder ? parseInt(holder.getAttribute('data-volume-cap'), 10) : NaN;
    if (isNaN(percent)) {
      return VOLUME_CAP_FALLBACK;
    }
    return Math.max(0, Math.min(percent, PERCENT_MAX)) / PERCENT_MAX;
  }

  /* 画面の属性にある数を読む。ないとき、数でないときは、もとの値を使う */
  function readNumber(element, name, fallback) {
    var value = parseFloat(element.getAttribute(name));
    return isNaN(value) ? fallback : value;
  }

  /* 記録の日付を押すと、時刻の入力が開く。両方そろったら、その場で保存する */
  function bindDayEditors() {
    each(document.querySelectorAll('.dayhead'), function (head) {
      head.addEventListener('click', function () {
        var editor = head.nextElementSibling;
        var open = head.getAttribute('aria-expanded') === 'true';
        head.setAttribute('aria-expanded', !open);
        editor.hidden = open;
      });
    });
    each(document.querySelectorAll('.dayedit'), function (form) {
      form.addEventListener('change', function () {
        var inputs = form.querySelectorAll('input[type=time]');
        if (inputs[0].value && inputs[1].value) {
          form.submit();
        }
      });
    });
  }

  /* 朝のひとこと。「直す」を選んだときだけ、時刻の入力を出す */
  function bindMorningSheet() {
    var times = document.getElementById('sheetTimes');
    if (!times) {
      return;
    }
    each(document.querySelectorAll('input[name=timeMode]'), function (radio) {
      radio.addEventListener('change', function () {
        times.hidden = radio.value !== 'fix';
      });
    });
    /* 選んだ気分や答えを、もう一度押すと外せる（どちらも任意のため） */
    each(document.querySelectorAll('.seg input[type=radio]'), function (radio) {
      radio.addEventListener('click', function () {
        if (radio.getAttribute('data-was') === 'true') {
          radio.checked = false;
          radio.setAttribute('data-was', 'false');
        } else {
          each(document.getElementsByName(radio.name), function (other) {
            other.setAttribute('data-was', 'false');
          });
          radio.setAttribute('data-was', 'true');
        }
      });
    });
  }

  /* 残してある最後の反応の時刻を読む。なければ、いまを最後の反応とする */
  function readLastReaction(storageKey) {
    try {
      var saved = parseInt(sessionStorage.getItem(storageKey), 10);
      if (!isNaN(saved)) {
        return saved;
      }
      sessionStorage.setItem(storageKey, String(Date.now()));
    } catch (error) {
      /* 保存できないときは、この画面を開いた時刻から数える */
    }
    return Date.now();
  }

  /* 夜の画面（音だけ）。時計を進め、残り時間を数える */
  function bindNightClock() {
    var clock = document.getElementById('nightClock');
    if (!clock) {
      return;
    }
    var remaining = document.getElementById('nightRemaining');
    var total = parseInt(clock.getAttribute('data-total-minutes'), 10);
    var startedAt = Date.now();

    function pad(value) {
      return value < 10 ? '0' + value : String(value);
    }
    function tick() {
      var now = new Date();
      clock.textContent = pad(now.getHours()) + ':' + pad(now.getMinutes());
      var left = Math.max(0, total - Math.floor((Date.now() - startedAt) / MS_PER_MINUTE));
      remaining.textContent = 'あと ' + left + '分';
    }
    tick();
    setInterval(tick, CLOCK_TICK_MS);
  }

  /* 夜の画面。画面を触った時刻から、反応のない秒数を数え、一定の間隔でサーバーに送る。
     音声がつながるまでは、画面の操作を「反応」とみなし、応答の間隔にも同じ値を使う。
     返ってきた結果に従い、すごし方が変わったときは画面を読み直し、終わったときはホームへ移る。 */
  function bindNightObserver() {
    var config = document.getElementById('nightObserve');
    if (!config) {
      return;
    }
    var url = config.getAttribute('data-url');
    var endUrl = config.getAttribute('data-end-url');
    var home = config.getAttribute('data-home');
    var intervalMs = parseInt(config.getAttribute('data-interval'), 10) * MS_PER_SECOND;
    var noReactionSec = parseInt(config.getAttribute('data-no-reaction-sec'), 10);
    var ended = false;
    /* 画面を読み直しても、反応のない時間が 0 に戻らないよう、夜の始まりごとにブラウザへ残す */
    var storageKey = 'yurune.lastReactionAt.' + config.getAttribute('data-started-at');
    var lastReactionAt = readLastReaction(storageKey);

    function markReaction() {
      lastReactionAt = Date.now();
      try {
        sessionStorage.setItem(storageKey, String(lastReactionAt));
      } catch (error) {
        /* 保存できなくても、この画面の間は数えられる */
      }
    }
    ['pointerdown', 'keydown', 'touchstart'].forEach(function (name) {
      document.addEventListener(name, markReaction, { passive: true });
    });
    /* 声での会話は、画面に触れない。聞き取れた発言を、反応として数える */
    document.addEventListener(NIGHT_REACTION_EVENT, markReaction);

    function agoSeconds() {
      return Math.max(0, Math.floor((Date.now() - lastReactionAt) / MS_PER_SECOND));
    }

    /* 端末のタイマー。サーバーに届かなくても、反応のない時間が基準に届いたら、端末が自分で終える */
    function endByDevice() {
      if (ended) {
        return;
      }
      ended = true;
      var sent = fetch(endUrl, {
        method: 'POST',
        headers: postHeaders(),
        body: 'reason=no_reaction&lastReactionAgoSec=' + agoSeconds()
      }).catch(function () {
        /* 通信できなくても、端末の終了は止めない */
      });
      leaveNight(home, NIGHT_GOODNIGHT_FADE_SEC, sent);
    }
    setInterval(function () {
      if (agoSeconds() >= noReactionSec) {
        endByDevice();
      }
    }, CLOCK_TICK_MS);

    function send() {
      if (ended) {
        return;
      }
      var body = 'lastReactionAgoSec=' + agoSeconds();
      fetch(url, {
        method: 'POST',
        headers: postHeaders(),
        body: body
      }).then(function (response) {
        return response.json();
      }).then(function (result) {
        if (result.ended) {
          ended = true;
          leaveNight(home, result.sound ? result.sound.fadeRemainingSec : 0);
          return;
        }
        if (result.sound) {
          document.dispatchEvent(new CustomEvent(NIGHT_STATE_EVENT, { detail: result }));
        }
        if (result.reload) {
          window.location.reload();
        }
      }).catch(function () {
        /* 通信できなくても、音と画面は止めない。次の間隔で、もう一度送る */
      });
    }
    setInterval(send, intervalMs);
  }

  /* 夜を終えるときは、音を、なめらかに下げてから、ホームへ移る。下げる時間は、サーバーが残りを返したときはその時間（フェード中の夜）、なければ短く。
     request があれば、それも終わるのを待つ（終わりの記録が、移動で途切れないように） */
  function leaveNight(home, fadeSec, request) {
    var seconds = fadeSec > 0 ? fadeSec : NIGHT_LEAVE_FADE_SEC;
    document.dispatchEvent(new CustomEvent(NIGHT_FADE_EVENT, { detail: { seconds: seconds } }));
    var faded = new Promise(function (resolve) {
      setTimeout(resolve, seconds * MS_PER_SECOND);
    });
    Promise.all([faded, request]).then(function () {
      window.location.href = home;
    });
  }

  /* 夜の画面（会話）。はじめのあいさつの間だけ口が動き、そのあとは、話している間だけ動く */
  function bindNightTalk() {
    var talkingPot = document.querySelector('.pot.talk');
    if (!talkingPot) {
      return;
    }
    setTimeout(function () { talkingPot.classList.remove('talk'); }, TALK_FIRST_MS);
  }

  /* 夜の画面（会話）。声を文字にした発言をサーバーへ送り、検査を通った返事を、画面に出して読み上げる。
     発言も返事も、この画面の中にだけ置く（サーバーにも端末にも、保存しない）。吹き出しには、文字として入れる。
     眠る前に、ボタンを押さなくていいように、会話の画面を開くと、自分から聞き始める。ぽてねが話している間は聞かず、
     話し終えると、また聞く。声がないまま長く続いたときと、利用者が「お休み」にしたときは、聞くのをやめる（ボタンで、また聞く）。 */
  function bindNightTalkVoice() {
    var config = document.getElementById('nightTalk');
    var chat = document.querySelector('.chat');
    var mic = document.getElementById('micButton');
    var hint = document.getElementById('micHint');
    if (!config || !chat || !mic || !hint) {
      return;
    }
    var url = config.getAttribute('data-url');
    var pot = document.querySelector('.pot');
    var volumeCap = readVolumeCap();
    var speechRate = readNumber(config, 'data-speech-rate', TALK_SPEECH_RATE);
    var speechPitch = readNumber(config, 'data-speech-pitch', TALK_SPEECH_PITCH);
    var Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    var history = [];
    var busy = false;
    var speaking = false;
    /* 利用者が「お休み」にしていないか。標準は、聞く */
    var wantListening = true;
    /* マイクが許されなかった */
    var blocked = false;
    /* 聞いている間だけ持つ */
    var recognition = null;
    /* 声を出せなかった返事。端末は、さわる操作のない画面での再生を許さないことがある。次にさわったときに、出す */
    var pendingVoice = null;
    var restartTimer = null;
    var speakTimer = null;
    var lastHeardAt = Date.now();
    /* 環境音を下げているか */
    var ducked = false;

    /* 聞く状態（お休みでも、許されないのでもない）のあいだは、環境音を下げる。ぽてねが話している間なども、聞く状態のままなので、上げ下げしない */
    function applyDuck(on) {
      if (on === ducked) {
        return;
      }
      ducked = on;
      var sound = window.yuruneNightSound;
      if (sound && typeof sound.setDuck === 'function') {
        sound.setDuck(on ? TALK_DUCK : 1);
      }
    }

    /* 声がないまま続いたら、聞くのをやめる長さ。画面の属性で変えられる */
    function idleLimitMs() {
      return readNumber(config, 'data-listen-idle-ms', TALK_LISTEN_IDLE_MS);
    }

    function canListen() {
      return Boolean(Recognition) && wantListening && !blocked && !busy && !speaking && !document.hidden;
    }

    /* 画面の文と、マイクの見た目は、いまの状態から決める */
    function renderHint() {
      var text = TEXT_HINT_LISTENING;
      if (!Recognition) {
        text = TEXT_HINT_UNSUPPORTED;
      } else if (blocked) {
        text = TEXT_HINT_BLOCKED;
      } else if (!wantListening) {
        text = TEXT_HINT_PAUSED;
      } else if (busy || speaking) {
        text = TEXT_HINT_REPLYING;
      } else if (pendingVoice) {
        text = TEXT_HINT_TAP;
      }
      hint.textContent = text;
      mic.classList.toggle('on', Boolean(recognition));
      var listening = Boolean(Recognition) && wantListening && !blocked;
      mic.setAttribute('aria-pressed', listening ? 'true' : 'false');
      applyDuck(listening);
      mic.setAttribute('aria-label', listening ? TEXT_MIC_PAUSE : TEXT_MIC_RESUME);
    }

    function showTransient(text) {
      hint.textContent = text;
      setTimeout(renderHint, TALK_HINT_RESET_MS);
    }

    /* ぽてねの最新の返事を、1 つだけ出す（前の返事は、入れ替わる）。利用者の発言は、画面に出さない。
       返事が出るたびに、data-n を 1 増やす（画面の外から、返事が来たことを数えるため） */
    function showReply(text) {
      var line = document.getElementById('talkReply');
      if (!line) {
        return;
      }
      line.textContent = text;
      line.setAttribute('data-n', String((parseInt(line.getAttribute('data-n'), 10) || 0) + 1));
    }

    function remember(userText, replyText) {
      history.push({ role: 'user', text: userText });
      if (replyText) {
        history.push({ role: 'model', text: replyText });
      }
      if (history.length > TALK_HISTORY_MAX) {
        history.splice(0, history.length - TALK_HISTORY_MAX);
      }
    }

    function setTalking(on) {
      if (pot) {
        pot.classList.toggle('talk', on);
      }
    }

    function stopListening() {
      clearTimeout(restartTimer);
      restartTimer = null;
      if (recognition) {
        var current = recognition;
        recognition = null;
        current.onresult = null;
        current.onerror = null;
        current.onend = null;
        try {
          current.abort();
        } catch (error) {
          /* すでに止まっている */
        }
      }
      renderHint();
    }

    function scheduleListening(delayMs) {
      clearTimeout(restartTimer);
      restartTimer = null;
      if (!canListen()) {
        renderHint();
        return;
      }
      restartTimer = setTimeout(startListening, delayMs);
      renderHint();
    }

    function startListening() {
      restartTimer = null;
      if (recognition || !canListen()) {
        renderHint();
        return;
      }
      var session = new Recognition();
      var openedAt = Date.now();
      session.lang = 'ja-JP';
      session.continuous = true;
      session.interimResults = false;
      session.maxAlternatives = 1;
      session.onresult = function (event) {
        var text = '';
        for (var index = event.resultIndex || 0; index < event.results.length; index++) {
          var alternative = event.results[index] && event.results[index][0];
          if (alternative && alternative.transcript) {
            text += alternative.transcript;
          }
        }
        text = text.trim();
        if (text.length < TALK_MIN_CHARS) {
          return;
        }
        lastHeardAt = Date.now();
        document.dispatchEvent(new CustomEvent(NIGHT_REACTION_EVENT));
        send(text);
      };
      session.onerror = function (event) {
        var error = event && event.error;
        if (error === 'not-allowed' || error === 'service-not-allowed') {
          blocked = true;
          return;
        }
        if (error === 'no-speech' || error === 'aborted') {
          return;
        }
        showTransient(TEXT_HINT_MISSED);
      };
      session.onend = function () {
        if (recognition !== session) {
          return;
        }
        recognition = null;
        scheduleListening(Date.now() - openedAt < TALK_RETRY_BELOW_MS ? TALK_RETRY_MS : TALK_RESTART_MS);
      };
      recognition = session;
      try {
        session.start();
      } catch (error) {
        recognition = null;
        scheduleListening(TALK_RETRY_MS);
        return;
      }
      renderHint();
    }

    /* 返事を待っている間と、ぽてねが話している間は、聞かない（自分の声を、発言と取り違えない） */
    function beginSpeaking() {
      speaking = true;
      stopListening();
      clearTimeout(speakTimer);
      speakTimer = setTimeout(endSpeaking, TALK_SPEAK_MAX_MS);
    }

    function endSpeaking() {
      if (!speaking) {
        return;
      }
      speaking = false;
      clearTimeout(speakTimer);
      setTalking(false);
      lastHeardAt = Date.now();
      scheduleListening(TALK_RESTART_MS);
    }

    function decodeBase64(base64) {
      return Uint8Array.from(atob(base64), function (character) { return character.charCodeAt(0); });
    }

    /* どの方法でも声を出せなかったとき。返事は、吹き出しに出ている。次にさわったときに、同じ返事を声にする */
    function holdForTap(audioBase64, text) {
      pendingVoice = { audio: audioBase64, text: text };
      endSpeaking();
    }

    function speakWithBrowser(text, audioBase64) {
      if (!window.speechSynthesis || !window.SpeechSynthesisUtterance) {
        holdForTap(audioBase64, text);
        return;
      }
      var started = false;
      var blockedTimer = setTimeout(function () {
        if (!started) {
          try {
            window.speechSynthesis.cancel();
          } catch (error) {
            /* 止められなくても、さわったときにやり直す */
          }
          holdForTap(audioBase64, text);
        }
      }, TALK_SPEECH_START_MS);
      var utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'ja-JP';
      utterance.rate = speechRate;
      utterance.pitch = speechPitch;
      utterance.volume = Math.min(TALK_SPEECH_VOLUME, volumeCap);
      utterance.onstart = function () { started = true; clearTimeout(blockedTimer); setTalking(true); };
      utterance.onend = function () { clearTimeout(blockedTimer); endSpeaking(); };
      utterance.onerror = function (event) {
        clearTimeout(blockedTimer);
        if (!started && event && event.error === 'not-allowed') {
          holdForTap(audioBase64, text);
        } else {
          endSpeaking();
        }
      };
      window.speechSynthesis.speak(utterance);
    }

    /* audio 要素で流す。端末が操作のない再生を許さなければ、ブラウザの読み上げへ */
    function playWithElement(audioBase64, text) {
      try {
        var audio = new Audio(URL.createObjectURL(new Blob([decodeBase64(audioBase64)], { type: 'audio/mpeg' })));
        audio.volume = volumeCap;
        audio.onplay = function () { setTalking(true); };
        audio.onended = function () { endSpeaking(); };
        audio.onerror = function () { setTalking(false); speakWithBrowser(text, audioBase64); };
        audio.play().catch(function () { speakWithBrowser(text, audioBase64); });
      } catch (error) {
        speakWithBrowser(text, audioBase64);
      }
    }

    /* 声は、まず、夜の環境音と同じ出力（一度さわれば鳴らせる）で流す。だめなら、audio 要素、ブラウザの読み上げの順 */
    function playServerAudio(audioBase64, text) {
      var sound = window.yuruneNightSound;
      if (sound && sound.playVoice) {
        try {
          sound.playVoice(decodeBase64(audioBase64).buffer, volumeCap, function () { setTalking(true); }).then(function () {
            endSpeaking();
          }, function () {
            playWithElement(audioBase64, text);
          });
          return;
        } catch (error) {
          /* 下の方法へ */
        }
      }
      playWithElement(audioBase64, text);
    }

    function speak(result) {
      if (!result.speak) {
        return;
      }
      beginSpeaking();
      if (result.audio) {
        playServerAudio(result.audio, result.reply);
      } else {
        speakWithBrowser(result.reply, '');
      }
    }

    /* 声を出せなかった返事は、利用者が画面にさわったとき（その操作で、端末が再生を許す）に出す */
    function speakHeldReply() {
      if (!pendingVoice || speaking || busy) {
        return;
      }
      var held = pendingVoice;
      pendingVoice = null;
      beginSpeaking();
      if (held.audio) {
        playServerAudio(held.audio, held.text);
      } else {
        speakWithBrowser(held.text, '');
      }
    }

    function send(text) {
      busy = true;
      pendingVoice = null;
      stopListening();
      fetch(url, {
        method: 'POST',
        headers: postHeaders('application/json'),
        body: JSON.stringify({ text: text, history: history })
      }).then(function (response) {
        return response.json();
      }).then(function (result) {
        if (result.state === 'talk_disabled') {
          /* 設定で夜の会話がオフになった。読み直すと、環境音だけの画面になる */
          window.location.reload();
          return;
        }
        if (result.state !== 'ok') {
          return;
        }
        if (result.reply) {
          showReply(result.reply);
          remember(text, result.reply);
          speak(result);
        }
      }).catch(function () {
        /* つながらないときは、定型文の「いまはお話しできません」で応じる。音と自動終了は、止まらない */
        showReply(TEXT_DOWN);
      }).then(function () {
        busy = false;
        lastHeardAt = Date.now();
        scheduleListening(TALK_RESTART_MS);
      });
    }

    /* マイクのボタンは、聞くのを「お休み」にする・また聞く、の切り替え。許されなかったあとは、もう一度ためす */
    mic.addEventListener('click', function () {
      if (!Recognition) {
        showTransient(TEXT_HINT_UNSUPPORTED);
        return;
      }
      if (wantListening && !blocked) {
        wantListening = false;
        stopListening();
        return;
      }
      wantListening = true;
      blocked = false;
      lastHeardAt = Date.now();
      if (canListen()) {
        startListening();
      } else {
        renderHint();
      }
    });

    ['pointerup', 'click', 'keydown'].forEach(function (name) {
      document.addEventListener(name, speakHeldReply, { passive: true });
    });

    /* 画面が隠れたら、聞くのをやめる（隠れている間は、ブラウザが止める。戻ったら、また聞く） */
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        stopListening();
      } else {
        scheduleListening(TALK_RESTART_MS);
      }
    });

    /* 声がないまま長く続いたら、マイクを開けたままにしない。眠ったあとは、聞くのをやめる */
    setInterval(function () {
      if (wantListening && !busy && !speaking && Date.now() - lastHeardAt > idleLimitMs()) {
        wantListening = false;
        stopListening();
      }
    }, TALK_IDLE_CHECK_MS);

    renderHint();
    if (Recognition) {
      startListening();
    }
  }

  /* つながらないときの案内ページを、端末に持たせる（個人のデータは持たせない）。
     読み込みが終わってから登録するのは、画面の表示と、通信を取り合わないため。登録できなくても、画面は何も変わらない */
  function registerServiceWorker() {
    var meta = document.querySelector('meta[name="service-worker"]');
    if (!meta || !('serviceWorker' in navigator)) {
      return;
    }
    window.addEventListener('load', function () {
      navigator.serviceWorker.register(meta.getAttribute('content')).catch(function () {
        /* 登録できない環境（古いブラウザ、閲覧の制限）でも、アプリは、これまでどおり動く */
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    bindPot();
    bindSegments();
    bindSwitches();
    bindDayEditors();
    bindMorningSheet();
    bindNightClock();
    bindNightObserver();
    bindNightTalk();
    bindNightTalkVoice();
    registerServiceWorker();
  });
}());
