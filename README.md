# ゆるね

眠る前に開いて使う、睡眠サポートの Web アプリです。夜の音を流しながら、話したくなったらキャラクターの「ぽてね」と声で話すことができ、朝に昨夜のことをひとこと答えると、夜のすごし方を少しずつ見直していきます。

ゆるねは医療機器ではありません。つらさが続くときは医師に相談してください。

## 動いているもの

https://yurune-4ffhhe3pra-an.a.run.app

Google Cloud の Cloud Run（東京）で動いています。審査用のアカウントを用意してあり、メールを受け取らなくても入れます。ログインの情報はハッカソンの提出フォームの「動作確認の方法」に書きます。このアカウントの画面には「審査員用のデモアカウントです。」と出ます。

## できること

- 夜の音（雨・波・森）を流します。音源のファイルは使わず端末の中で合成していて、音量はゆっくり上げ下げします。
- ぽてねと声で話せます。ボタンを押さなくても話しかければ聞き取り、聞くのをやめたいときはマイクのボタンで「お休み」にできます。画面に出るのはぽてねの最新の返事1行だけで、声を聞いている間は、声に重なって聞き取りの精度が落ちないように環境音を小さくします。憎悪・嫌がらせ・性的な表現などの不適切な言葉と、死ぬことや消えることを言うつらい気持ちの言葉には、返事をせずに無視します。
- 朝に、寝た時刻と起きた時刻が合っているか、気分、昨夜のすごし方が合っていたかを答えます。答えなくても保存できます。
- ふりかえりで、今週と先週の睡眠時間を比べます。
- 夜のめやす（目標）を設定から選べます。

## 作りの考え方

AI（Gemini）を使うのは、夜のあいだの応答とぽてねとの会話だけで、統計、判定、音量の上限、夜を終わらせる条件はプログラムが決めます。AI を使わなくてよいところには使っていません。

Gemini が使えないとき（キーがない、止めてある、呼び出しに失敗した）は、規則だけで夜を進め、会話は決まった文で答えます。医療に関する質問には、Gemini が使えるときでも、モデルに聞かずに「医師に相談してください」という決まった文で答えます。

会話の全文、音声、その要約は保存せず、ログにも出しません。保存するのは、就寝と起床の時刻、朝の答え、夜の計画、エージェントの判断の記録（見たこと、選んだこと、理由）、選択肢ごとの回数です。

## 夜のすごし方を見直すしくみ

1. 最初の7夜は標準のすごし方で夜のようすを見ます。
2. そのあとは3夜に1夜を標準の夜にして、ほかの夜は音か音量の下げ方のどちらか1つだけを変えて試します。
3. 週に1回、変えた夜を日の近い標準の夜と組にして比べます。比べ方は符号反転の並べ替え検定です。
4. 夜の数がそろうまでは結論を出さず、「いまのところ」と伝えます。
5. 「合わなかった」が続いた選択肢は候補から外し、変えた夜で悪い向きが続いたときは標準に戻します。

判断はすべてコードで行い、理由といっしょに記録します（会話の本文は入れません）。7夜や3夜といった数値はどれも仮の値で根拠はなく、[GentlePolicy.java](src/main/java/app/yurune/constant/GentlePolicy.java) に集めています。

見直しのしくみは、寝かたの違う利用者を模したシミュレーションで試しています（[src/test/java/app/yurune/pdca](src/test/java/app/yurune/pdca)）。ただ、事前に置いた12個の基準のうち満たしたのは5個だけで、たとえば、本当に差のある音を84夜のうちに見つけられた割合は64%（基準は70%以上）、差のないものを「よく使う」にしてしまった割合は13%（基準は10%以下）でした。基準は結果に合わせて変えておらず、利用者の寝かたもすべて仮定で、実際の人のデータでは確かめていません。数値を1つずつ変えたときの動きは [tools/pdca_sweep](tools/pdca_sweep/README.md) にあります。

## 手元で動かす

JDK 21、Maven 3.9 以上、Tomcat 10.1 が必要です。

```
mvn package
```

できた `target/yurune.war` を Tomcat の `webapps` に `ROOT.war` として置いて起動します。HTTP で開くときは、Cookie が HTTPS でしか送られないので、環境変数 `YURUNE_COOKIE_SECURE=false` を付けてください（localhost は Chrome ならそのままでも動きます）。

何も設定しないと保存先と認証がメモリ上になり、再起動すると消えます。この状態ではメール確認なしで登録でき、Gemini のキー（`GEMINI_API_KEY`）がなければ規則と決まった文だけで動きます。

審査用のデモアカウントは、`YURUNE_DEMO_EMAIL` と `YURUNE_DEMO_PASSWORD` を設定して起動すると使えます。最初にログインしたときに14日分の記録と朝の答えを待つ1晩が入り、開いた日が変わるとその日の日付で入れ直します。

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

全部で623件あり、Firebase のエミュレータが動いていないと、エミュレータを使う42件は飛ばされます（失敗にはなりません）。

ブラウザを実際に動かす確認は [tools/e2e](tools/e2e) にあり、Chrome と Node.js 24 が必要です。

## Google Cloud への公開

手順は [deploy/README.md](deploy/README.md) にあります。使っているのは Cloud Run（東京）、Firestore、Firebase Authentication、Secret Manager（Gemini のキー）、Cloud Build と Artifact Registry、Model Armor、Cloud Text-to-Speech です。

Model Armor は、東京ではテンプレートを作れなかったので米国のリージョンに置いていて、会話の検査のために、伏せ字にしたあとの文が米国へ送られます。この点はアプリのプライバシーの画面にも書いています。

実行用のサービスアカウントには必要な権限だけを付け、Gemini のキーはリポジトリにも環境変数にも置かずに Secret Manager から読みます。

## フォルダ

| 場所 | 内容 |
| --- | --- |
| `src/main/java/app/yurune` | アプリ本体。Servlet、Controller、Service、Repository の層に分けています。外部サービスへの接続は `gateway` |
| `src/main/webapp` | 画面（`WEB-INF/views` の JSP）、CSS、JavaScript、PWA の設定。夜の音は `js/nightsound.js` |
| `src/test` | テスト |
| `deploy` | Google Cloud へ公開するスクリプトと、確認のスクリプト |
| `docker`、`Dockerfile` | コンテナの設定（Tomcat 10.1、非 root で実行） |
| `firebase` | Firestore のセキュリティルールと、エミュレータの設定 |
| `docs/diagrams` | 構成図などの図と、それを描くスクリプト |
| `tools` | 手元での確認用。ブラウザの通し確認、Gemini の応答の測定、Model Armor の日本語の測定、見直しの数値を変えた結果 |
| `lp` | 紹介のページ（ランディングページ）。作り方は [lp/README.md](lp/README.md) |

## まだ確かめていないこと

- スマホの実機での、ぽてねの声と画面ロックのときの動き。ブラウザの自動操作と、本物の Gemini と Model Armor を使った通しまでは確かめています。
- 数値の根拠。夜の数や検定の水準などは仮の値で、1つずつ変えて結果の動きを調べましたが（[tools/pdca_sweep](tools/pdca_sweep/README.md)）、いまの値が正しいと言える根拠は出ませんでした。通知のしきい値も仮の値です。
- 声を文字にする精度。環境音が声に重なっているのではないかと疑い、声を聞いている間は環境音を小さくしましたが、どれだけ直ったかは確かめていません。
- Model Armor の日本語の検知。小さな試験（72文）で測り、見つけてほしい33文のうち30文を見つけ、普通の会話30文は1件も止めませんでしたが、文が少ないので割合は目安にしかならず、返事の側の検査は測っていません。結果は [tools/armor_eval](tools/armor_eval/README.md) にあります。
- 監視の働き。メトリクス、`/ready` のアップタイムの確認、アラート、ダッシュボードはプロジェクトに作りました（定義は `deploy/monitoring`）。実際の会話を通して、リクエストの遅延と LLM の遅延のメトリクスに値が入ること、アップタイムの確認が6か所とも成功することは確かめました。5xx、LLM の失敗、守り、道具の拒否は起きていないので値が入っておらず、アラートが実際に鳴るかも確かめていません。アラートは5xx、LLM の失敗、`/ready` の死活の3つで、しきい値は仮の値です。
- 最小インスタンスは、起動が冷めないように1にしています。0のときは最初のアクセスに4〜6秒かかっていました。起動しているあいだは少額の料金がかかります（額は料金表では確かめていません）。

コードやテストの中にある「T-MED」「AT-06」「U-14」などの記号は、提出した仕様書とセキュリティ設計の項目番号で、その文書はこのリポジトリには入れていません。
