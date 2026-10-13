# ゆるね

眠る前に開いて使う、睡眠の支えのための Web アプリです。夜の音を流して、話したくなったら、キャラクターの「ぽてね」と声で話せます。朝に昨夜のことをひとこと答えると、夜のすごし方を、少しずつ見直していきます。

ゆるねは医療機器ではありません。つらさが続くときは、医師に相談してください。

## 動いているもの

https://yurune-4ffhhe3pra-an.a.run.app

Google Cloud の Cloud Run（東京）で動いています。審査用のアカウントを用意してあり、メールを受け取らなくても入れます。ログインの情報は、ハッカソンの提出フォームの「動作確認の方法」に書きます。このアカウントの画面には、「審査員用のデモアカウントです。」と出ます。

## できること

- 夜の音（雨、波、森）を流す。音は端末の中でつくっていて、音源のファイルは使っていません。音量はゆっくり上げて、ゆっくり下げます。
- ぽてねと声で話す。ボタンを押さなくても、話しかけると聞き取ります。聞くのをやめたいときは、マイクのボタンで「お休み」にします。画面に出るのは、ぽてねの最新の返事1行だけです。
- 朝に、寝た時刻と起きた時刻が合っているか、気分、昨夜のすごし方が合っていたかを答える。答えなくても保存できます。
- ふりかえりで、今週と先週の睡眠時間を比べる。
- 夜のめやす（目標）を、設定から選ぶ。

## 作りの考え方

AI（Gemini）を使うのは、夜のあいだの応答と、ぽてねとの会話だけです。統計、判定、音量の上限、夜を終わらせる条件は、プログラムが決めます。AI を使わなくてよいところには、使っていません。

Gemini が使えないとき（キーがない、止めてある、呼び出しに失敗した）は、規則だけで夜を進めます。会話は、決まった文で答えます。医療に関する質問には、Gemini が使えるときでも、モデルに聞かずに、「医師に相談してください」という決まった文で答えます。

会話の全文、音声、その要約は、保存しません。ログにも出しません。保存するのは、就寝と起床の時刻、朝の答え、夜の計画、エージェントの判断の記録（見たこと、選んだこと、理由）、選択肢ごとの回数です。

## 夜のすごし方を見直すしくみ

1. 最初の7夜は、標準のすごし方で、夜のようすを見ます。
2. そのあとは、3夜に1夜を標準の夜にします。ほかの夜は、音か、音量の下げ方のどちらか1つだけを変えて試します。
3. 週に1回、変えた夜を、日の近い標準の夜と組にして比べます。比べ方は、符号反転の並べ替え検定です。
4. 夜の数がそろうまでは、結論を出しません。「いまのところ」と伝えます。
5. 「合わなかった」が続いた選択肢は、候補から外します。変えた夜で悪い向きが続いたら、標準に戻します。

判断はすべてコードで行い、理由といっしょに記録します（会話の本文は入れません）。7夜や3夜といった数値は、どれも仮置きで、根拠はありません。[GentlePolicy.java](src/main/java/app/yurune/constant/GentlePolicy.java) に集めています。

見直しがうまく動くかは、寝かたの違う利用者を模したシミュレーションで確かめています（[src/test/java/app/yurune/pdca](src/test/java/app/yurune/pdca)）。

## 手元で動かす

必要なものは、JDK 21、Maven 3.9 以上、Tomcat 10.1 です。

```
mvn package
```

できた `target/yurune.war` を、Tomcat の `webapps` に `ROOT.war` として置いて、起動します。HTTP で開くときは、環境変数 `YURUNE_COOKIE_SECURE=false` を付けてください。付けないと、Cookie が HTTPS でしか送られません（localhost は、Chrome ならそのままでも動きます）。

何も設定しないと、保存先と認証がメモリ上になり、再起動すると消えます。この状態では、メール確認なしで登録できます。Gemini のキー（`GEMINI_API_KEY`）がなければ、規則と決まった文だけで動きます。

審査用のデモアカウントは、`YURUNE_DEMO_EMAIL` と `YURUNE_DEMO_PASSWORD` を設定して起動すると使えます。最初にログインしたときに、14日分の記録と、朝の答えを待つ1晩が入ります。開いた日が変わると、その日の日付で入れ直します。このアカウントの画面には、「審査員用のデモアカウントです。」と出ます。

### 環境変数

| 名前 | 内容 | 初期値 |
| --- | --- | --- |
| `GEMINI_API_KEY` | Gemini のキー。なければ LLM を使わない | なし |
| `GEMINI_MODEL` | 使うモデル | gemini-3.5-flash-lite |
| `YURUNE_AGENT_ENABLED` | `false` で LLM を止める（規則だけで動く） | true |
| `YURUNE_NO_REACTION_MIN` | 反応がないまま夜を終えるまでの分 | 15 |
| `YURUNE_VOLUME_CAP_PCT` | 音量の上限（%） | 60 |
| `YURUNE_OBSERVATION_INTERVAL_SEC` | 端末が夜のようすを送る間隔（秒） | 30 |
| `YURUNE_LLM_MIN_INTERVAL_SEC` | 夜の判断で LLM を呼ぶ最短の間隔（秒） | 20 |
| `YURUNE_LLM_DAILY_CAP` | 1日に LLM を呼べる上限（回） | 2000 |
| `YURUNE_TALK_MIN_INTERVAL_SEC` | 会話の最短の間隔（秒） | 2 |
| `YURUNE_STORAGE` | `firestore` で Firestore に保存する。なければメモリ | メモリ |
| `YURUNE_AUTH` | `firebase` で Firebase Authentication を使う。なければメモリ | メモリ |
| `GOOGLE_CLOUD_PROJECT` | プロジェクト ID（Firestore、Model Armor、読み上げに必要） | なし |
| `FIREBASE_API_KEY` | Firebase の Web API キー（`YURUNE_AUTH=firebase` のとき必要） | なし |
| `YURUNE_ARMOR_TEMPLATE`、`YURUNE_ARMOR_LOCATION` | Model Armor のテンプレート名と場所。2つそろえて設定する | なし（検査しない） |
| `YURUNE_TTS_ENABLED` | `true` で、ぽてねの声を Cloud Text-to-Speech でつくる。なければ端末の読み上げ | false |
| `YURUNE_COOKIE_SECURE` | `false` で、HTTP でも Cookie を送る（開発用） | true |
| `YURUNE_DEMO_EMAIL`、`YURUNE_DEMO_PASSWORD` | 審査用アカウント。パスワードは、メモリ上の認証のときだけ使う | なし |

テスト用に、接続先を差し替える変数もあります（`FIRESTORE_EMULATOR_HOST`、`FIREBASE_AUTH_EMULATOR_HOST`、`GEMINI_ENDPOINT`、`YURUNE_ARMOR_ENDPOINT`、`YURUNE_TTS_ENDPOINT`、`YURUNE_GCP_ACCESS_TOKEN`）。使い方は [tools/e2e/README.md](tools/e2e/README.md) にあります。

## テスト

```
mvn test
```

全部で 619 件あります。Firebase のエミュレータが動いていないと、エミュレータを使う 42 件は飛ばされます（失敗にはなりません）。

ブラウザを実際に動かす確認は、[tools/e2e](tools/e2e) にあります。Chrome と Node.js 24 が要ります。

## Google Cloud への公開

手順は [deploy/README.md](deploy/README.md) に書きました。使っているものは、Cloud Run（東京）、Firestore、Firebase Authentication、Secret Manager（Gemini のキー）、Cloud Build と Artifact Registry、Model Armor、Cloud Text-to-Speech です。

Model Armor は、東京ではテンプレートを作れなかったので、米国のリージョンに置いています。会話の検査のために、伏せ字にしたあとの文が米国へ送られます。この点は、アプリのプライバシーの画面にも書いています。

実行用のサービスアカウントには、必要な権限だけを付けています。Gemini のキーは、リポジトリにも環境変数にも置かず、Secret Manager から読みます。

## フォルダ

| 場所 | 内容 |
| --- | --- |
| `src/main/java/app/yurune` | アプリ本体。Servlet、Controller、Service、Repository の層に分けています。外部サービスへの接続は `gateway` |
| `src/main/webapp` | 画面（`WEB-INF/views` の JSP）、CSS、JavaScript、PWA の設定。夜の音は `js/nightsound.js` |
| `src/test` | テスト |
| `deploy` | Google Cloud へ公開するスクリプトと、確認のスクリプト |
| `docker`、`Dockerfile` | コンテナの設定（Tomcat 10.1、非 root で実行） |
| `firebase` | Firestore のセキュリティルールと、エミュレータの設定 |
| `tools` | 手元での確認用。ブラウザの通し確認、Gemini の応答の測定 |
| `lp` | 紹介のページ（ランディングページ）。作り方は [lp/README.md](lp/README.md) |

## まだ確かめていないこと

- スマホの実機での、ぽてねの声と、画面ロックのときの動き。ブラウザの自動操作と、本物の Gemini、Model Armor を使った通しまでは、確かめています。
- 数値の根拠。夜の数、検定の水準、通知のしきい値などは、仮置きのままです。
- Model Armor が、日本語の危ない文をどのくらい見つけるか。測っていません。
- 監視（メトリクスとアラート）。定義は `deploy/monitoring` にありますが、プロジェクトには適用していません。
- 最小インスタンスを 0 にしているので、しばらく使っていないと、最初のアクセスが遅くなります。

コードやテストの中にある「T-MED」「AT-06」「U-14」などの記号は、提出した仕様書とセキュリティ設計の項目番号です。その文書は、このリポジトリには入れていません。
