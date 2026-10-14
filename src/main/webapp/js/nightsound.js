/* 夜の音（雨・波・森）を、端末の中で合成して鳴らす。音源のファイルは使わない。
   曲は標準のノードだけで組む（AudioWorklet は使わない）。音量は、必ずゲインのランプで変える。
   サーバーが決めた状態（曲、音量、フェードの残り）に従う。通信できないときは、いま鳴っている音を、そのまま鳴らし続ける。 */
(function () {
  'use strict';

  var MS_PER_SECOND = 1000;
  var PERCENT_MAX = 100;
  var VOLUME_CAP_FALLBACK_PCT = 60;
  var DEFAULT_TRACK = 'rain';
  var END_PATH = '/night/end';
  var EVENT_STATE = 'yurune:night-state';
  var EVENT_FADE = 'yurune:night-fade';
  /* 鳴らせる状態が変わったときに出す。会話の画面が、「画面にさわってね」の案内を出し入れするため */
  var EVENT_VIEW = 'yurune:sound-view';
  var RENDER_RATE = 48000;

  /* 100% のときの出力。ピークを小さく保つため、1 よりずっと小さくする */
  var MASTER_MAX_GAIN = 0.6;
  /* 上げるときは、少なくともこの秒数をかける。急に大きくして起こさないため */
  var UP_RAMP_SEC = 10;
  var DOWN_RAMP_SEC = 3;
  /* 声を聞いている間、環境音を、この割合まで下げる（仮置き）。環境音が、声に重なると、声を文字にする精度が落ちるため */
  var DUCK_RAMP_SEC = 1;
  /* 曲を変えるときは、この秒数で重ねる */
  var CROSSFADE_SEC = 4;
  var RETIRE_MARGIN_SEC = 0.5;
  /* 利用者が「終わる」を押したときは、この秒数で、なめらかに下げてから送る */
  var LEAVE_FADE_SEC = 2.5;

  /* 雑音の一つ分の長さと、つなぎ目を重ねる長さ（秒）。つなぎ目で、音がとぎれないようにする */
  var LOOP_SEC = 12;
  var SEAM_SEC = 1;
  var HALF_PI = Math.PI / 2;
  var NOISE_LIMIT = 0.9;
  var WHITE_RMS = 0.12;
  var BROWN_RMS = 0.2;
  var BROWN_LEAK = 0.02;
  var BROWN_GAIN = 3.5;
  var DROP_RMS = 0.05;
  var DROPS_PER_SEC = 18;
  var DROP_MIN_SEC = 0.004;
  var DROP_MAX_SEC = 0.012;
  var DROP_MIN_AMP = 0.2;
  var DROP_AMP_RANGE = 0.4;
  var DROP_DECAY = 6;

  /* 3 つの曲の聞こえの大きさをそろえる値（同じ音量の設定で、ほぼ同じ大きさになるように、測って決めた） */
  var RAIN_HISS_HIGH_HZ = 250;
  var RAIN_HISS_LOW_HZ = 5000;
  var RAIN_HISS_GAIN = 1;
  var RAIN_DROP_HIGH_HZ = 1800;
  var RAIN_DROP_GAIN = 0.6;

  var WAVE_LOW_HZ = 520;
  var WAVE_FILTER_SWING_HZ = 80;
  var WAVE_GAIN = 0.43;
  var WAVE_SWING = 0.12;
  var WAVE_PERIOD_SEC = 10.5;
  var WAVE_PERIOD_B_SEC = 13.3;

  var FOREST_LOW_HZ = 650;
  var FOREST_GAIN = 0.42;
  var FOREST_SWING = 0.1;
  var FOREST_PERIOD_SEC = 23;
  var FOREST_PERIOD_B_SEC = 31;

  var DC_CUT_HZ = 20;
  var LIMITER_THRESHOLD_DB = -12;
  var LIMITER_KNEE_DB = 6;
  var LIMITER_RATIO = 12;
  var LIMITER_ATTACK_SEC = 0.003;
  var LIMITER_RELEASE_SEC = 0.25;

  var TEXT_IDLE = '音は、まだ流れていません。';
  var TEXT_PLAYING_HEAD = '今夜は';
  var TEXT_PLAYING_TAIL = 'を流しているよ。';
  var TEXT_UNSUPPORTED = 'この端末では、夜の音を流せません。';

  /* 画面が消えても続けられないか試す構成。ロック画面には、この名前と役を出す */
  var MEDIA_TITLE = 'ゆるね';
  var MEDIA_ARTIST = 'ぽてね';
  var MEDIA_SESSION_PLAYING = 'playing';
  var MEDIA_SESSION_PAUSED = 'paused';

  var context = null;
  var master = null;
  var buffers = null;
  var current = null;
  var unsupported = false;
  var started = false;
  var leaving = false;
  var paused = false;
  var pending = null;
  var appliedTarget = null;
  /* 環境音に掛ける割合。1 は、そのまま。声を聞いている間だけ、会話の画面が下げる（setDuck） */
  var duck = 1;
  var capPct = VOLUME_CAP_FALLBACK_PCT;
  var label = '';
  var stateElement = null;
  var startButton = null;
  /* 画面が消えても鳴り続けられないか試す出力（MediaStreamAudioDestinationNode 経由の audio 要素）。
     使えない・鳴らせないときは、これまでどおり audioContext.destination に直接つなぐ（二重には鳴らさない） */
  var usingMediaElement = false;
  var audioElement = null;
  /* ぽてねの声（会話の返事）を流す先。環境音と同じ出力にそろえる（audio 要素経由か、直の出力か） */
  var voiceOutput = null;
  /* 出力先を決めている最中の Promise。「音を流す」は、タップ（touchend）とボタンの click の、
     2 つから呼ばれうる。決め終わる前に 2 度目が来ても、同じ Promise を待たせ、master を 2 度作らない */
  var outputReady = null;
  /* Wake Lock（画面をつけたままにする）。設定がオンで、鳴っている間だけ求める */
  var wakeLockPreferred = false;
  var wakeLockSentinel = null;

  function clamp(value, low, high) {
    return Math.min(high, Math.max(low, value));
  }

  /* 画面の属性にある数を読む。ないとき、数でないときは、もとの値を使う */
  function readNumber(element, name, fallback) {
    var value = parseFloat(element.getAttribute(name));
    return isNaN(value) ? fallback : value;
  }

  /* 白い雑音。広い帯域に、同じ強さで音が広がる（雨の土台） */
  function fillWhite(data) {
    for (var index = 0; index < data.length; index++) {
      data[index] = Math.random() * 2 - 1;
    }
  }

  /* 茶色の雑音。少しずつ揺れる値を足していくので、低い音が多い（波と森の土台） */
  function fillBrown(data) {
    var last = 0;
    for (var index = 0; index < data.length; index++) {
      last = (last + BROWN_LEAK * (Math.random() * 2 - 1)) / (1 + BROWN_LEAK);
      data[index] = last * BROWN_GAIN;
    }
  }

  /* 雨粒。まばらな、ごく短い、不規則なバースト。1 粒は、指数的に減衰する雑音 */
  function fillDrops(data, rate) {
    var count = Math.round(DROPS_PER_SEC * LOOP_SEC);
    for (var drop = 0; drop < count; drop++) {
      var start = Math.floor(Math.random() * data.length);
      var span = Math.round((DROP_MIN_SEC + Math.random() * (DROP_MAX_SEC - DROP_MIN_SEC)) * rate);
      var amplitude = DROP_MIN_AMP + Math.random() * DROP_AMP_RANGE;
      for (var offset = 0; offset < span; offset++) {
        var position = (start + offset) % data.length;
        data[position] += amplitude * (Math.random() * 2 - 1) * Math.exp(-DROP_DECAY * offset / span);
      }
    }
  }

  /* 直流（0 を中心からずらす成分）を消し、目標の大きさ（二乗平均の平方根）にそろえる。クリップしない上限を超えない */
  function normalize(data, targetRms) {
    var mean = 0;
    var index;
    for (index = 0; index < data.length; index++) {
      mean += data[index];
    }
    mean /= data.length;
    var power = 0;
    for (index = 0; index < data.length; index++) {
      power += (data[index] - mean) * (data[index] - mean);
    }
    var rms = Math.sqrt(power / data.length);
    var scale = rms > 0 ? targetRms / rms : 0;
    for (index = 0; index < data.length; index++) {
      data[index] = clamp((data[index] - mean) * scale, -NOISE_LIMIT, NOISE_LIMIT);
    }
  }

  /* 雑音の一つ分を作り、ループできる材料（AudioBuffer）にする。
     先頭と末尾を、等パワーで重ねる。ループの戻りで、音がとぎれたり、クリックが出たりしないため */
  function makeLoop(audioContext, fill, rms) {
    var rate = audioContext.sampleRate;
    var length = Math.round(LOOP_SEC * rate);
    var seam = Math.round(SEAM_SEC * rate);
    var raw = new Float32Array(length + seam);
    fill(raw, rate);
    var buffer = audioContext.createBuffer(1, length, rate);
    var data = buffer.getChannelData(0);
    var index;
    for (index = 0; index < length; index++) {
      data[index] = raw[index];
    }
    for (index = 0; index < seam; index++) {
      var angle = (index / seam) * HALF_PI;
      data[index] = raw[index] * Math.sin(angle) + raw[length + index] * Math.cos(angle);
    }
    normalize(data, rms);
    return buffer;
  }

  function makeBuffers(audioContext) {
    return {
      white: makeLoop(audioContext, fillWhite, WHITE_RMS),
      brown: makeLoop(audioContext, fillBrown, BROWN_RMS),
      drops: makeLoop(audioContext, fillDrops, DROP_RMS)
    };
  }

  function gainNode(audioContext, value) {
    var node = audioContext.createGain();
    node.gain.value = value;
    return node;
  }

  function filterNode(audioContext, type, frequency) {
    var node = audioContext.createBiquadFilter();
    node.type = type;
    node.frequency.value = frequency;
    return node;
  }

  function loopSource(audioContext, buffer) {
    var source = audioContext.createBufferSource();
    source.buffer = buffer;
    source.loop = true;
    source.start();
    return source;
  }

  /* ゆっくりした揺れ（周期は秒）を、param（音量やフィルターの周波数）に足す。深さは param の単位 */
  function swing(audioContext, periodSec, depth, param) {
    var lfo = audioContext.createOscillator();
    var amount = gainNode(audioContext, depth);
    lfo.frequency.value = 1 / periodSec;
    lfo.connect(amount);
    amount.connect(param);
    lfo.start();
    return lfo;
  }

  /* 雨: 広い帯域の雑音を整えた土台に、小さな雨粒を重ねる */
  function buildRain(audioContext, bufferSet, out) {
    var hiss = loopSource(audioContext, bufferSet.white);
    var hissHigh = filterNode(audioContext, 'highpass', RAIN_HISS_HIGH_HZ);
    var hissLow = filterNode(audioContext, 'lowpass', RAIN_HISS_LOW_HZ);
    var hissGain = gainNode(audioContext, RAIN_HISS_GAIN);
    hiss.connect(hissHigh);
    hissHigh.connect(hissLow);
    hissLow.connect(hissGain);
    hissGain.connect(out);

    var drops = loopSource(audioContext, bufferSet.drops);
    var dropHigh = filterNode(audioContext, 'highpass', RAIN_DROP_HIGH_HZ);
    var dropGain = gainNode(audioContext, RAIN_DROP_GAIN);
    drops.connect(dropHigh);
    dropHigh.connect(dropGain);
    dropGain.connect(out);
    return [hiss, drops];
  }

  /* 波: 低い雑音を、周期の違う 2 つの揺れで、音量と明るさを、ゆっくりゆらす（周期は 10 秒台） */
  function buildWave(audioContext, bufferSet, out) {
    var source = loopSource(audioContext, bufferSet.brown);
    var filter = filterNode(audioContext, 'lowpass', WAVE_LOW_HZ);
    var swell = gainNode(audioContext, WAVE_GAIN);
    source.connect(filter);
    filter.connect(swell);
    swell.connect(out);
    return [
      source,
      swing(audioContext, WAVE_PERIOD_SEC, WAVE_SWING, swell.gain),
      swing(audioContext, WAVE_PERIOD_B_SEC, WAVE_SWING, swell.gain),
      swing(audioContext, WAVE_PERIOD_SEC, WAVE_FILTER_SWING_HZ, filter.frequency),
      swing(audioContext, WAVE_PERIOD_B_SEC, WAVE_FILTER_SWING_HZ, filter.frequency)
    ];
  }

  /* 森: 低域寄りの、やわらかい風の雑音。高い音は入れない */
  function buildForest(audioContext, bufferSet, out) {
    var source = loopSource(audioContext, bufferSet.brown);
    var filter = filterNode(audioContext, 'lowpass', FOREST_LOW_HZ);
    var breeze = gainNode(audioContext, FOREST_GAIN);
    source.connect(filter);
    filter.connect(breeze);
    breeze.connect(out);
    return [
      source,
      swing(audioContext, FOREST_PERIOD_SEC, FOREST_SWING, breeze.gain),
      swing(audioContext, FOREST_PERIOD_B_SEC, FOREST_SWING, breeze.gain)
    ];
  }

  var BUILDERS = {
    rain: buildRain,
    wave: buildWave,
    forest: buildForest
  };

  /* 出力の最後の段。直流を消し、ピークを押さえる（聞こえ方より、安全側）。
     destinationNode を渡さなければ、これまでどおり audioContext.destination へ直接つなぐ
     （確認用の OfflineAudioContext は、これまでどおり自分の destination に描く）。 */
  function buildMaster(audioContext, destinationNode) {
    var output = gainNode(audioContext, 0);
    var dcCut = filterNode(audioContext, 'highpass', DC_CUT_HZ);
    var limiter = audioContext.createDynamicsCompressor();
    limiter.threshold.value = LIMITER_THRESHOLD_DB;
    limiter.knee.value = LIMITER_KNEE_DB;
    limiter.ratio.value = LIMITER_RATIO;
    limiter.attack.value = LIMITER_ATTACK_SEC;
    limiter.release.value = LIMITER_RELEASE_SEC;
    output.connect(dcCut);
    dcCut.connect(limiter);
    limiter.connect(destinationNode || audioContext.destination);
    return output;
  }

  /* 音量は、必ずランプで変える。急に大きくしない */
  function rampTo(audioContext, param, target, seconds) {
    var now = audioContext.currentTime;
    param.cancelScheduledValues(now);
    param.setValueAtTime(param.value, now);
    param.linearRampToValueAtTime(target, now + Math.max(0, seconds));
  }

  /* 利用者の音量（%）を、上限の内側のゲインにする。サーバーの上限と、端末の上限の、どちらも超えない */
  function toGain(percent) {
    return clamp(percent, 0, capPct) / PERCENT_MAX * MASTER_MAX_GAIN * duck;
  }

  function createContext() {
    var Constructor = window.AudioContext || window.webkitAudioContext;
    if (!Constructor) {
      unsupported = true;
      return false;
    }
    try {
      context = new Constructor();
    } catch (error) {
      unsupported = true;
      return false;
    }
    buffers = makeBuffers(context);
    context.onstatechange = updateView;
    return true;
  }

  /* 端末が、合成した音を audio 要素（MediaStreamAudioDestinationNode 経由）から鳴らせるか試す。
     OS が「メディアの再生中」とみなせば、画面が消えても続く見込み（未確認。実機で確かめる）。
     使えない・鳴らせない（play が拒まれる）ときは、これまでどおり audioContext.destination に直接つなぐ。
     どちらか一方にだけつなぐ（二重には鳴らさない）。戻り値は、どちらに決まったかを待つ Promise。 */
  function setUpOutput() {
    if (!canTryMediaElement()) {
      master = buildMaster(context);
      return Promise.resolve();
    }
    var destination;
    var element;
    try {
      destination = context.createMediaStreamDestination();
      element = document.createElement('audio');
      element.srcObject = destination.stream;
      element.setAttribute('playsinline', '');
    } catch (error) {
      master = buildMaster(context);
      return Promise.resolve();
    }
    var attempt = element.play();
    if (!attempt || typeof attempt.then !== 'function') {
      /* play が Promise を返さない古い端末。信頼できないので、直の出力にする */
      master = buildMaster(context);
      return Promise.resolve();
    }
    return attempt.then(function () {
      audioElement = element;
      usingMediaElement = true;
      master = buildMaster(context, destination);
      voiceOutput = destination;
      setUpMediaSession();
    }, function () {
      /* 自動再生が拒まれた。いまより悪くしないため、直の出力に倒す */
      master = buildMaster(context);
    });
  }

  function canTryMediaElement() {
    return !!(context && typeof context.createMediaStreamDestination === 'function'
        && typeof window.MediaStream !== 'undefined');
  }

  /* ロック画面に、曲名と役を出す。操作は、なめらかに止める・流し直すの 2 つだけ（次の曲・頭出しは渡さない） */
  function setUpMediaSession() {
    if (!('mediaSession' in navigator)) {
      return;
    }
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: MEDIA_TITLE,
        artist: MEDIA_ARTIST,
        artwork: findArtwork()
      });
      navigator.mediaSession.setActionHandler('pause', pauseSound);
      navigator.mediaSession.setActionHandler('play', resumeSound);
    } catch (error) {
      /* 対応していない端末でも、音はこれまでどおり鳴る */
    }
  }

  function findArtwork() {
    var link = document.querySelector('link[rel="icon"]');
    return link ? [{ src: link.href, sizes: '192x192', type: 'image/png' }] : [];
  }

  function setMediaSessionPlaybackState(state) {
    if ('mediaSession' in navigator) {
      navigator.mediaSession.playbackState = state;
    }
  }

  /* 曲を切り替える。前の曲は、重ねてから止める（音が切れないように） */
  function switchTrack(key) {
    var previous = current;
    var next = { key: key, fade: gainNode(context, previous ? 0 : 1), nodes: null };
    next.fade.connect(master);
    next.nodes = BUILDERS[key](context, buffers, next.fade);
    current = next;
    if (previous) {
      rampTo(context, next.fade, 1, CROSSFADE_SEC);
      retire(previous);
    }
  }

  function retire(track) {
    var stopAt = context.currentTime + CROSSFADE_SEC + RETIRE_MARGIN_SEC;
    rampTo(context, track.fade, 0, CROSSFADE_SEC);
    track.nodes.forEach(function (node) {
      node.stop(stopAt);
    });
    setTimeout(function () {
      track.fade.disconnect();
    }, (CROSSFADE_SEC + RETIRE_MARGIN_SEC) * MS_PER_SECOND);
  }

  /* サーバーの状態に合わせる。フェード中は、残りの時間をかけて 0 へ。上限を超えない音量へ、ゆっくり。
     同じ目標へは、もう一度ランプをかけ直さない（毎回かけ直すと、目標に届くまでの時間が延びてしまうため）。
     利用者がロック画面などで止めている間（paused）は、サーバーの状態が変わっても、音量を動かさない */
  function applyState(state) {
    pending = state;
    if (!started || leaving || paused) {
      return;
    }
    if (state.soundKey !== current.key && BUILDERS[state.soundKey]) {
      switchTrack(state.soundKey);
    }
    var fading = state.fadeRemainingSec > 0;
    var target = fading ? 0 : toGain(state.volumePct);
    var seconds = fading ? state.fadeRemainingSec : (target > master.gain.value ? UP_RAMP_SEC : DOWN_RAMP_SEC);
    if (!fading && target === appliedTarget) {
      return;
    }
    appliedTarget = fading ? null : target;
    rampTo(context, master.gain, target, seconds);
  }

  /* 声を聞いている間だけ、環境音を下げる（factor は 0〜1）。ゆっくり下げる・戻す。フェード中と、止めている間は、触らない。
     上限は超えない（掛けるのは 1 以下だけ） */
  function setDuck(factor) {
    duck = clamp(Number(factor), 0, 1);
    if (!started || leaving || paused || !context || !master || pending.fadeRemainingSec > 0) {
      return;
    }
    var target = toGain(pending.volumePct);
    appliedTarget = target;
    /* 下げるのは早く、戻すのは、ほかの音量の上げ方と同じに、ゆっくり（眠りかけの人を、急に起こさない） */
    rampTo(context, master.gain, target, target > master.gain.value ? UP_RAMP_SEC : DUCK_RAMP_SEC);
  }

  function begin() {
    started = true;
    paused = false;
    var key = BUILDERS[pending.soundKey] ? pending.soundKey : DEFAULT_TRACK;
    master.gain.value = 0;
    appliedTarget = null;
    switchTrack(key);
    applyState(pending);
    requestWakeLockIfNeeded();
  }

  /* 電話の着信などで、audio 要素も止められていることがある。鳴らし直すついでに、それも起こす */
  function reviveMediaElementIfNeeded() {
    if (audioElement && audioElement.paused) {
      audioElement.play().catch(function () {
        /* だめでも、鳴らせなければ、いつもどおり「音は、まだ流れていません」が出る */
      });
    }
  }

  /* context ができたあと、鳴らせる状態（running）まで進める。すでに始めていれば、鳴らし直すだけ */
  function resumeAndBegin() {
    reviveMediaElementIfNeeded();
    if (context.state === 'running') {
      if (!started) {
        begin();
      }
      updateView();
      return;
    }
    context.resume().then(function () {
      if (context.state === 'running' && !started) {
        begin();
      }
      updateView();
    }, updateView);
  }

  /* 音を流し始める。端末が許したときだけ、鳴る（resume の結果で決まる）。
     はじめての呼び出しでだけ、出力先（audio 要素か、直の出力か）を決める。
     決めている途中に、もう一度呼ばれても（touchend とボタンの click の、両方から来ることがある）、
     同じ Promise を待つだけにする（master が整う前に begin() が走ってしまわないように） */
  function startSound() {
    if (unsupported) {
      updateView();
      return;
    }
    if (!context) {
      if (!createContext()) {
        updateView();
        return;
      }
      outputReady = setUpOutput();
    }
    outputReady.then(resumeAndBegin);
  }

  /* 「音を流す」のタップと、ロック画面の「流す」操作の、共通の入口。
     止めている（paused）ときは、そこから戻す。鳴らしたことがなければ、はじめから始める */
  function resumeSound() {
    if (unsupported) {
      updateView();
      return;
    }
    if (started && paused) {
      paused = false;
      reviveMediaElementIfNeeded();
      appliedTarget = null;
      applyState(pending);
      requestWakeLockIfNeeded();
      setMediaSessionPlaybackState(MEDIA_SESSION_PLAYING);
      updateView();
      return;
    }
    startSound();
  }

  /* ロック画面の「止める」操作。なめらかに下げて止めるだけで、夜は終わらせない（サーバーには伝えない） */
  function pauseSound() {
    if (!started || leaving || paused) {
      return;
    }
    paused = true;
    appliedTarget = null;
    rampTo(context, master.gain, 0, DOWN_RAMP_SEC);
    releaseWakeLock();
    setMediaSessionPlaybackState(MEDIA_SESSION_PAUSED);
    updateView();
  }

  /* Wake Lock。設定がオンで、画面が見えていて、鳴っている間だけ求める。
     拒まれても、何も言わず続ける（対応していない端末でも、音はこれまでどおり鳴る） */
  function requestWakeLockIfNeeded() {
    if (!wakeLockPreferred || wakeLockSentinel || !('wakeLock' in navigator)) {
      return;
    }
    if (document.visibilityState !== 'visible' || !started || paused || leaving) {
      return;
    }
    navigator.wakeLock.request('screen').then(function (sentinel) {
      wakeLockSentinel = sentinel;
      sentinel.addEventListener('release', function () {
        wakeLockSentinel = null;
      });
    }, function () {
      /* 拒まれた・使えない。何もしない */
    });
  }

  function releaseWakeLock() {
    if (!wakeLockSentinel) {
      return;
    }
    var sentinel = wakeLockSentinel;
    wakeLockSentinel = null;
    sentinel.release().catch(function () {
      /* すでに解けていても、問題ない */
    });
  }

  /* 「終わる」を押したとき。音を下げてから、送る（送るのは、同じフォームで form.submit する） */
  function onSubmit(event) {
    var form = event.target;
    var action = form && form.getAttribute ? form.getAttribute('action') || '' : '';
    if (action.indexOf(END_PATH) === -1 || !started || leaving) {
      return;
    }
    event.preventDefault();
    fadeOut(LEAVE_FADE_SEC);
    setTimeout(function () {
      form.submit();
    }, LEAVE_FADE_SEC * MS_PER_SECOND);
  }

  /* 夜を終えるとき。音を、残りの時間をかけて下げる。以後は、サーバーの状態で音を変えない */
  function fadeOut(seconds) {
    leaving = true;
    releaseWakeLock();
    if (started) {
      rampTo(context, master.gain, 0, seconds);
    }
  }

  /* 画面を離れるとき。音は、すぐに止める（なめらかな下げは、夜を終えるときだけ） */
  function stopNow() {
    releaseWakeLock();
    if (current) {
      current.nodes.forEach(function (node) {
        node.stop();
      });
      current.fade.disconnect();
      current = null;
    }
    if (master) {
      master.gain.value = 0;
    }
    started = false;
    paused = false;
    appliedTarget = null;
  }

  function onPageHide() {
    stopNow();
    if (context && context.state === 'running') {
      context.suspend();
    }
  }

  /* 鳴っているときだけ、鳴っていると書く。鳴らせていない・止めているときは、その旨と、操作（音を流す）を出す */
  function updateView() {
    if (!stateElement) {
      return;
    }
    var playing = started && !paused && context !== null && context.state === 'running';
    var text = TEXT_IDLE;
    if (unsupported) {
      text = TEXT_UNSUPPORTED;
    } else if (playing) {
      text = TEXT_PLAYING_HEAD + label + TEXT_PLAYING_TAIL;
    }
    stateElement.textContent = text;
    if (startButton) {
      startButton.hidden = playing || unsupported;
    }
    document.dispatchEvent(new CustomEvent(EVENT_VIEW));
  }

  function onGesture() {
    if (!started && !leaving) {
      startSound();
    }
  }

  function init() {
    var holder = document.getElementById('nightObserve');
    stateElement = document.getElementById('nightSoundState');
    startButton = document.getElementById('soundStart');
    if (!holder || !stateElement) {
      return;
    }
    capPct = clamp(readNumber(holder, 'data-volume-cap', VOLUME_CAP_FALLBACK_PCT), 0, PERCENT_MAX);
    label = stateElement.getAttribute('data-sound-label') || '';
    wakeLockPreferred = holder.getAttribute('data-keep-screen-on') === 'true';
    pending = {
      soundKey: holder.getAttribute('data-sound-key') || DEFAULT_TRACK,
      volumePct: readNumber(holder, 'data-sound-pct', 0),
      fadeRemainingSec: readNumber(holder, 'data-fade-sec', 0)
    };
    if (startButton) {
      startButton.addEventListener('click', resumeSound);
    }
    /* iPhone の Safari が再生を許す「操作」は、指を離したとき（touchend）、click、keydown だけ（WebKit の公式記事）。
       触れた瞬間（pointerdown・touchstart）や pointerup で始めると、audio 要素の再生が拒まれ、出力先が直の出力に決まってしまう
       （決めるのは 1 回だけ。直の出力は、マナーモードの無音スイッチで消される）。だから、この 3 つのときだけ始める */
    ['touchend', 'click', 'keydown'].forEach(function (name) {
      document.addEventListener(name, onGesture, { passive: true });
    });
    document.addEventListener(EVENT_STATE, function (event) {
      if (event.detail && event.detail.sound) {
        applyState(event.detail.sound);
      }
    });
    document.addEventListener(EVENT_FADE, function (event) {
      fadeOut(event.detail.seconds);
    });
    document.addEventListener('submit', onSubmit);
    window.addEventListener('pagehide', onPageHide);
    window.addEventListener('pageshow', function (event) {
      if (event.persisted) {
        window.location.reload();
      }
    });
    /* 画面に戻ったとき、Wake Lock を求め直す（鳴っていなければ、何もしない） */
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'visible') {
        requestWakeLockIfNeeded();
      }
    });
    updateView();
    /* 以前に操作があった画面なら、端末が鳴らしてよいかをそのまま試す。なければ、利用者の操作を待つ */
    if (navigator.userActivation && navigator.userActivation.hasBeenActive) {
      startSound();
    }
  }

  /* ぽてねの声（mp3 のバイト列）を、環境音と同じ出力で流す。会話の画面は、押さずに話すので、
     返事のたびに「さわる」操作がない。端末は、さわる操作のない画面での audio 要素の再生を許さないが、
     一度さわって鳴らせるようになった出力（context が running）なら、あとから声を足せる。
     鳴らせない（まだ操作がない、デコードできない）ときは、鳴らし始める前に reject する（呼び側が、ほかの方法を試す）。
     流し終えたら、resolve する。onStart は、鳴り始めたときに 1 回呼ぶ */
  function playVoice(bytes, volume, onStart) {
    return new Promise(function (resolve, reject) {
      if (!context || context.state !== 'running' || !master) {
        reject(new Error('locked'));
        return;
      }
      var finished = false;
      var played = false;
      function fail(error) {
        if (!finished && !played) {
          finished = true;
          reject(error || new Error('decode'));
        }
      }
      function play(buffer) {
        if (played || finished) {
          return;
        }
        played = true;
        try {
          var source = context.createBufferSource();
          source.buffer = buffer;
          var gain = gainNode(context, clamp(volume, 0, 1));
          source.connect(gain);
          gain.connect(voiceOutput || context.destination);
          source.onended = function () {
            try {
              gain.disconnect();
            } catch (error) {
              /* すでに外れている */
            }
            finished = true;
            resolve();
          };
          source.start(0);
          if (onStart) {
            onStart();
          }
        } catch (error) {
          finished = true;
          reject(error);
        }
      }
      try {
        /* 新しい端末は Promise を返し、古い端末は callback だけ。どちらでも、1 回だけ鳴らす */
        var decoding = context.decodeAudioData(bytes, play, fail);
        if (decoding && typeof decoding.then === 'function') {
          decoding.then(play, fail);
        }
      } catch (error) {
        fail(error);
      }
    });
  }

  /* 確認用（tools/e2e）。曲を、ブラウザの中で、同じ部品で描画する。鳴らさない */
  function renderTrack(key, seconds) {
    var Offline = window.OfflineAudioContext || window.webkitOfflineAudioContext;
    var offline = new Offline(1, Math.round(seconds * RENDER_RATE), RENDER_RATE);
    var output = buildMaster(offline);
    output.gain.value = MASTER_MAX_GAIN;
    var fade = gainNode(offline, 1);
    fade.connect(output);
    BUILDERS[key](offline, makeBuffers(offline), fade);
    return offline.startRendering().then(function (rendered) {
      return rendered.getChannelData(0);
    });
  }

  function status() {
    return {
      supported: !unsupported,
      started: started,
      leaving: leaving,
      paused: paused,
      duck: duck,
      track: current ? current.key : null,
      contextState: context ? context.state : null,
      gain: master ? master.gain.value : null,
      usingMediaElement: usingMediaElement,
      mediaElementPaused: audioElement ? audioElement.paused : null,
      wakeLockPreferred: wakeLockPreferred,
      wakeLockActive: !!wakeLockSentinel
    };
  }

  /* playVoice は、会話の画面（yurune.js）が使う。ほかは、確認用（tools/e2e）。
     ロック画面の操作は、実機・ブラウザの外からしか起きないので、同じ入口（pauseSound・resumeSound）を、直に呼んで確かめる */
  window.yuruneNightSound = {
    playVoice: playVoice,
    setDuck: setDuck,
    reviveOutput: reviveMediaElementIfNeeded,
    renderTrack: renderTrack,
    status: status,
    simulateMediaSessionPause: pauseSound,
    simulateMediaSessionPlay: resumeSound
  };
  document.addEventListener('DOMContentLoaded', init);
}());
