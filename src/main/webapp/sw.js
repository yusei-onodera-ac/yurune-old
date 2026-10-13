/* ゆるねの Service Worker。通信がつながらないときに、ページを開こうとしたら、固定の案内ページ（offline.html）を見せるためだけにある。
   利用者ごとの画面、操作（POST）、夜の通信、ログインまわりは、保存もせず、手も加えない（いつもネットワークへ行く）。
   アプリの入口（ルート）に置くのは、そこなら、追加の許可なしに、アプリ全体を受け持てるから。 */
'use strict';

/* 案内ページが使う固定の資源（STATIC_PATHS）を変えたら、ここを上げる。
   版が変わると新しい Service Worker が入り、古い版のキャッシュは activate で消える。 */
var CACHE_VERSION = '1';
var CACHE_PREFIX = 'yurune-static-';
var CACHE_NAME = CACHE_PREFIX + CACHE_VERSION;

/* キャッシュに入れてよいのは、利用者によらず同じ、この 2 つだけ。ここに無い URL は、どんな応答も入れない。
   Service Worker の URL からの相対で書くのは、アプリの置き場所が変わっても、そのまま使えるようにするため。 */
var OFFLINE_PATH = 'offline.html';
var STATIC_PATHS = ['offline.html', 'css/yurune.css'];

var OFFLINE_URL = new URL(OFFLINE_PATH, self.location.href).href;
var STATIC_URLS = STATIC_PATHS.map(function (path) {
  return new URL(path, self.location.href).href;
});

/* キャッシュへ書くのは、この関数だけ。
   ログイン画面への転送や、失敗の応答を、固定の資源として残さないために、書く前に確かめる。 */
function saveStatic(cache, url, response) {
  var storable = STATIC_URLS.indexOf(url) >= 0 && response.ok && response.type === 'basic' && !response.redirected;
  if (!storable) {
    return Promise.resolve(false);
  }
  return cache.put(url, response).then(function () {
    return true;
  });
}

/* HTTP キャッシュに残った古い写しを、新しい版として入れてしまわないよう、いつもサーバーから取る */
function precacheOne(cache, url) {
  return fetch(new Request(url, { cache: 'reload' })).then(function (response) {
    return saveStatic(cache, url, response);
  }).then(function (saved) {
    if (!saved) {
      throw new Error('static resource was not saved: ' + url);
    }
  });
}

/* 案内ページと CSS のどちらかが欠けると、案内ページが崩れる。1 つでも取れなければ、この版は入れない */
self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function (cache) {
      return Promise.all(STATIC_URLS.map(function (url) {
        return precacheOne(cache, url);
      }));
    }).then(function () {
      /* 取っておくのは固定の資源だけで、版が違っても互いに依存しない。古い版の終わりを待つ理由がない。
         待つと、アプリを閉じずに使い続ける端末（ホーム画面から開くもの）で、いつまでも古い版のままになる */
      return self.skipWaiting();
    })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (names) {
      /* 同じ出どころの、ほかのキャッシュを巻き込まないよう、この Service Worker が作った名前だけを消す */
      return Promise.all(names.filter(function (name) {
        return name.indexOf(CACHE_PREFIX) === 0 && name !== CACHE_NAME;
      }).map(function (name) {
        return caches.delete(name);
      }));
    }).then(function () {
      /* 入れた直後に開いているページも、すぐ受け持つ。そうしないと、その画面で通信が切れたとき、案内が出ない */
      return self.clients.claim();
    })
  );
});

/* つながっているときは、サーバーの応答をそのまま返す（エラーの応答も、そのまま）。
   案内ページに替えるのは、通信そのものの失敗のときだけ。サーバーの不調を、通信の切断と取り違えない */
function respondToNavigation(request) {
  return fetch(request).catch(function () {
    return caches.open(CACHE_NAME).then(function (cache) {
      return cache.match(OFFLINE_URL);
    }).then(function (offline) {
      return offline || Response.error();
    });
  });
}

/* つながっているときに最新を入れ直すのは、画面の CSS が変わったとき、案内ページも同じ見た目になるようにするため。
   使うのは、つながらないときだけ（画面は、いつも最新を取る） */
function respondToStatic(request) {
  return caches.open(CACHE_NAME).then(function (cache) {
    return fetch(request).then(function (response) {
      saveStatic(cache, request.url, response.clone()).catch(function () {
        /* 入れ直せなくても、画面に返す応答には関係しない */
      });
      return response;
    }).catch(function () {
      return cache.match(request.url).then(function (cached) {
        return cached || Response.error();
      });
    });
  });
}

self.addEventListener('fetch', function (event) {
  var request = event.request;
  if (request.method !== 'GET') {
    return;
  }
  if (request.mode === 'navigate') {
    event.respondWith(respondToNavigation(request));
  } else if (STATIC_URLS.indexOf(request.url) >= 0) {
    event.respondWith(respondToStatic(request));
  }
});
