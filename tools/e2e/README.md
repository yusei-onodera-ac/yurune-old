# ブラウザでの通し確認

Chrome（ヘッドレス）と Node.js 24 で動かします。Tomcat に載せたアプリを、実際に画面から操作して確かめます。手元で確かめるためのもので、Maven のテストには入っていません。

## 準備

1. Firebase のエミュレータを起動します。`firebase` フォルダで、次を実行します（Firebase CLI が要ります）。

   ```
   firebase emulators:start --only firestore,auth --project yurune-test
   ```

2. 擬似の Google サービスを起動します。Gemini、Model Armor、Text-to-Speech の代わりに、決めた応答を返します（18090 番）。

   ```
   node tools/e2e/fake_google.js
   ```

3. アプリを、次の環境変数を付けて、Tomcat（18080 番）に載せます。

   ```
   YURUNE_STORAGE=firestore
   YURUNE_AUTH=firebase
   GOOGLE_CLOUD_PROJECT=yurune-test
   FIREBASE_API_KEY=fake-key
   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080
   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099
   YURUNE_DEMO_EMAIL=demo@example.com
   GEMINI_API_KEY=fake-key
   GEMINI_ENDPOINT=http://127.0.0.1:18090/models/
   YURUNE_ARMOR_TEMPLATE=yurune-template
   YURUNE_ARMOR_LOCATION=us-central1
   YURUNE_ARMOR_ENDPOINT=http://127.0.0.1:18090
   YURUNE_TTS_ENABLED=true
   YURUNE_TTS_ENDPOINT=http://127.0.0.1:18090
   YURUNE_GCP_ACCESS_TOKEN=fake-token
   ```

4. 審査用アカウントを、エミュレータに作ります（メールの確認が済んだ状態で）。

   ```
   POST http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/yurune-test/accounts
   Authorization: Bearer owner
   {"email":"demo@example.com","password":"demo-pass-123","emailVerified":true}
   ```

   エミュレータを起動し直すと、このアカウントは消えます。そのときは、作り直してください。

5. スクリプトを実行します。

   ```
   node tools/e2e/account_e2e.js
   ```

## スクリプト

アプリのポートと起動のしかたは、スクリプトごとに違います。表の「アプリ」の列を見てください。

「メモリ」と書いたものは、上の準備の 1〜4 を使わず、保存先と認証をメモリ上にして動かします。次の環境変数だけを付けて、Tomcat に載せてください。

```
YURUNE_NO_REACTION_MIN=1
YURUNE_OBSERVATION_INTERVAL_SEC=5
YURUNE_DEMO_EMAIL=demo@example.com
YURUNE_DEMO_PASSWORD=demo-pass-123
```

| スクリプト | 確かめること | アプリ |
| --- | --- | --- |
| `account_e2e.js` | 登録、メール確認、ログイン、利用者ごとのデータの分離、データの消去、総当たりの制限 | 18080 |
| `conversation_e2e.js` | 会話の流れ（医療の質問への決まった文、障害のときの文、伏せ字、検査サービス）。ボタンを押さずに話せること（画面を開くと聞き始める、話し終えたらまた聞く、ぽてねが話しているあいだは聞かない、お休み、長い無音、画面が隠れたとき）。声が出せないときの流れ。音声認識と読み上げは、ブラウザ側を差し替えて再現しています。本物のマイクと声は、実機で確かめます | 18080 |
| `log_privacy_e2e.js <Tomcat の標準出力のファイル>` | 会話のあと、Firestore の全文書とログに、会話の本文が残っていないこと。ログが、トレース ID でつながり、種類と件数だけを持つこと | 18080 |
| `load_e2e.js` | 高頻度の観測と要求を送って、LLM の呼び出しが上限に収まること、5xx が出ないこと、遅くならないこと。65 秒かかります | 18080。無反応の終了を 30 分にして起動する（待つあいだに夜が終わらないように） |
| `features_e2e.js` | 設定の保存、設定の下の3ページ、PWA（manifest と Service Worker）。どの画面も、スクリプトの例外と CSP 違反が出ないこと。画像は、環境変数 `SHOTS_DIR` の場所へ出します | 18080 |
| `night_sound_e2e.js` | 夜の音（雨、波、森）。鳴らし始めは、操作のあとだけ。音量はゆっくり上がり、上限を超えない。「終わる」と、無反応での終わりは、音量を下げてから移る | 18085（メモリ） |
| `night_pdca_e2e.js` | 夜を始めて、無反応で終え、朝のひとことに答えると、ふりかえりの「目標」に「いまのところ」が、3 行以内で、禁止語なしで出ること。「今は表示しない」が、ログインと CSRF を要ること。設定「夜のめやす」の選択肢と保存。HTTP の流れだけで、見た目は見ていません | 18087（メモリ） |
| `night_comfort_e2e.js` | 設定「画面をつけたままにする」が夜の画面に渡ること。音の出力先がちょうど1つに決まること。設定どおりに Wake Lock を求める・求めないこと。ロック画面の「止める」「流す」を模した操作で、なめらかに下がる・戻ること。実機の画面ロックそのものは、ヘッドレスでは確かめられません | 18092（メモリ） |
| `review_goal_e2e.js` | ふりかえりのタブが「週」と「目標」の2つであること。「目標」に、いまの夜のめやす、変える入口、「いまのところ」が出ること。審査用アカウントに、サンプルの記録がなく、「審査員用のデモアカウントです。」と出ること。明るい画面と暗い画面の画像も撮ります | 18095（メモリ） |
