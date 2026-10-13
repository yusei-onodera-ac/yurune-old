# Google Cloud への公開と運用

東京の Cloud Run に公開する手順と、そのあとの確認、運用のメモです。2026-10-12 に、実際のプロジェクト（東京）で、はじめて実行しました。

スクリプトは、Windows PowerShell 5.1 で動かしています。コマンドプロンプト（cmd.exe）からは、次の形で呼びます。キーは、cmd の `set 名前=値` で渡します（値に引用符は付けません）。

```
set GEMINI_API_KEY=（Gemini のキー）
set FIREBASE_API_KEY=（Firebase の Web API キー）
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\deploy.ps1 -ProjectId （プロジェクト ID） -AllowUnauthenticated -ArmorLocation us-central1
```

## 前提

- Google Cloud のプロジェクトと、課金の設定。予算アラートは、クレジットを引く前の金額で数える設定にします。
- gcloud を入れて、`gcloud auth login` を済ませておく。
- Firebase を、同じプロジェクトに追加する。Authentication で「メール / パスワード」を有効にします。
- Gemini API のキーと、Firebase の Web API キー。

## 手順

PowerShell の場合です。

```powershell
$env:GEMINI_API_KEY = '<Gemini のキー>'
$env:FIREBASE_API_KEY = '<Firebase の Web API キー>'
.\deploy\deploy.ps1 -ProjectId <プロジェクト ID> -AllowUnauthenticated -ArmorLocation us-central1

# Firestore のセキュリティルール
cd firebase
npx firebase-tools deploy --only firestore:rules --project <プロジェクト ID>
cd ..

# 審査用アカウント（メールの確認が済んだ状態で作る。メールを受け取る必要がない）
$env:YURUNE_DEMO_PASSWORD = '<パスワード>'
.\deploy\create-demo-account.ps1 -ProjectId <プロジェクト ID> -Email review@yurune.example

# 方針どおりに設定されているかの確認
.\deploy\verify.ps1 -ProjectId <プロジェクト ID>

# 監視（メトリクス、アラート、ダッシュボード）。まだ実行していない
.\deploy\apply-monitoring.ps1 -ProjectId <プロジェクト ID> -ServiceHost <Cloud Run のホスト名>
```

`deploy.ps1` がすることは、次の順番です。

1. 必要な API を有効にする。
2. 実行用のサービスアカウント `yurune-app` を作り、必要な権限だけを付ける。
3. Firestore のデータベース、Gemini のキーのシークレット、Artifact Registry の脆弱性検査、Model Armor のテンプレートを作る。
4. ログを 30 日残す設定にする。
5. Cloud Build でイメージを作り、Cloud Run に公開する。最大インスタンス数は 3 にしてあります。

Model Armor は、東京（`asia-northeast1`）では、テンプレートを作れませんでした（`PERMISSION_DENIED: Write access ... was denied`）。そのため、米国（`-ArmorLocation us-central1`）に置いています。検査する文は、米国のリージョンへ送られます。

## 実行用のサービスアカウントの権限

専用のアカウント `yurune-app` だけで動かします。Owner、Editor、管理者の権限は付けません。

| 権限 | 使いみち |
| --- | --- |
| `roles/datastore.user` | Firestore で、利用者のデータを読み書きする |
| `roles/modelarmor.user` | 入出力の検査（sanitize の API だけ。テンプレートの管理はできない） |
| `roles/logging.logWriter` | 構造化ログを書く |
| `roles/cloudtrace.agent` | トレースを書く |
| `roles/serviceusage.serviceUsageConsumer` | Text-to-Speech と Model Armor の API を、このプロジェクトとして呼ぶ |
| `roles/secretmanager.secretAccessor`（Gemini のキーのシークレット 1 つだけ） | Gemini のキーを読む |

`verify.ps1` が、余分な権限がないこと、Owner / Editor / 管理者がないことを確かめます。

## 守りの設定

| 項目 | 設定 | どこで |
| --- | --- | --- |
| 画面の守り | CSP、`nosniff`、`DENY`、`no-store`、HSTS、CSRF、Cookie（HttpOnly、Secure、SameSite=Strict） | アプリ（Filter）。確認は `verify.ps1` |
| 鍵 | Gemini のキーは Secret Manager に置く。環境変数にも、リポジトリにも置かない | `deploy.ps1`、自動検査 |
| 使いすぎを抑える | Cloud Run の最大インスタンス数（初期値 3）。利用者ごとの LLM の呼び出し間隔と、1 日の上限 | `deploy.ps1`、アプリ |
| コンテナ | ベースイメージは、版とダイジェストで固定。非 root で実行。停止用のポートは閉じる。エラーの画面に版を出さない | `Dockerfile`、`docker/server.xml` |
| 脆弱性の検査 | Artifact Registry の自動検査（`containerscanning.googleapis.com`） | `deploy.ps1`。確認は `verify.ps1` |
| ログの保持 | 30 日 | `deploy.ps1` |
| Firestore | 本人の、メール確認済みの読み取りだけ許可する。書き込みは、だれにも許可しない（書くのはサーバーだけ） | `firebase/firestore.rules` |

## ログ

個人情報と会話の本文は、ログのどこにも入れません。検査は `AuditLogTest` と、`tools/e2e/log_privacy_e2e.js` です。

| 種類（`event`） | いつ | 項目（`detail`） |
| --- | --- | --- |
| `request` | リクエストごと（`/ready` と静的ファイルは、200 のときは出さない） | `method`、`path`、`status`、`latencyMs`、`user`（任意） |
| `conversation` | 会話の応答ごと | `input`、`reply`、`truncated`、`latencyMs`、`masked`、`outputViolation`（任意）、`down`（任意） |
| `decision` | 夜の判断を保存するごと | `phase`、`accepted` |
| `agent` | 夜のエージェントが道具を呼ぶごと | `tool`、`accepted`、`rejectReason`（任意）、`source`、`latencyMs`、`fallbackReason`（任意） |
| `llm` | LLM を呼ぶごと | `outcome`、`latencyMs`、`model`、`purpose`、`promptTokens`（任意）、`candidateTokens`（任意） |
| `control` | 制御の状態が変わったとき（ブレーカー、停止スイッチ、Model Armor、保存先の確認） | `kind`、`state`、`rejectedCount`（任意） |
| `span` | 外部の呼び出しごと（Firestore は、遅いものと失敗だけ） | `name`（`gemini.generate`、`armor.sanitize`、`tts.synthesize`、`firestore.request`）、`durationMs`、`outcome` |
| `pdca` | 夜のすごし方の見直しで、規則が判定するごと | `rule`、`verdict`、`nights` |
| `auth`、`erase`、`setting` | 認証、消去、設定の変更ごと | 操作名、結果、項目名など（個人情報は入れない） |

種類ごとに入れてよい項目名は、`AuditEventSchema` で決めています。

Logs Explorer での検索の例です。

```
jsonPayload.traceId="<トレース ID>"                 # 1 回の夜（または 1 回のリクエスト）を、起きた順に追う
jsonPayload.event="agent" AND jsonPayload.detail.accepted=false
                                                      # 拒否された道具の呼び出し（detail.rejectReason で理由別）
jsonPayload.event="control" AND jsonPayload.detail.kind="llm_breaker"
                                                      # LLM のサーキットブレーカーの開閉の履歴
severity="WARNING" AND jsonPayload.event="llm"       # LLM 呼び出しの失敗
```

## 監視

定義は `deploy/monitoring/` にあります。

- メトリクス（`log-metrics.json`）: `agent_tool_rejected`、`llm_error`、`llm_latency`、`guard_blocked`、`breaker_open`、`request_5xx`、`request_latency`
- アラート（`alert-policies.json`）: 5xx の率、遅延（p95）、ブレーカーが開いたままの時間、LLM の失敗率、守りの作動の急増、`/ready` の死活。しきい値は、すべて仮置きです（根拠なし。実際の負荷を測ってから決める）。通知先は空欄です。
- ダッシュボード（`dashboard.json`）: 上のメトリクスを並べただけの、最小のものです。

定義が JSON として読めること、参照している event と項目名が実在すること、アラートが参照するメトリクスが定義されていることは、`MonitoringConfigPolicyTest` が確かめます。プロジェクトへの適用は `apply-monitoring.ps1` ですが、まだ実行していません。

## `/ready`

保存先（Firestore）に読み書きできるかだけで、`200 ready` か `503 not ready` を返します（ログインは要りません）。LLM や Model Armor が止まっているだけでは、503 にしません（規則だけで動いているときも、「動いている」と見ます）。結果を 10 秒ほど覚えておき、保存先を呼びすぎないようにしています。

Cloud Run は `/healthz` を自分で使うので、外からは 404 になります。稼働の確認には `/ready` を使ってください。

## 実行して直したところ

実際のプロジェクトで動かして、次の不具合を直しました。同じ環境で起きるので、残しておきます。

- API の一覧が、1 つの引数にまとまって渡されていた（`deploy.ps1`）。配列を、1 つずつの引数に広げるようにした。
- Windows PowerShell 5.1 では、`$ErrorActionPreference = 'Stop'` のとき、gcloud が標準エラーに出す文（進み具合や「見つからない」の答え）で、そのまま止まる。終了コードだけで判定するようにした（`deploy.ps1`、`verify.ps1`）。
- 作ったばかりのサービスアカウントには、数秒のあいだ、権限を付けられない（「存在しない」と返る）。待って、やり直すようにした（`deploy.ps1`）。
- JSON を既定の文字コードで読むと、文字化けして壊れる。UTF-8 を指定した（`verify.ps1`、`apply-monitoring.ps1`）。

## まだ確かめていないこと

- `apply-monitoring.ps1` は、実行していません。メトリクスとアラートは、プロジェクトにありません。`verify.ps1` の失敗 13 件は、これが理由です。`--log-filter` に二重引用符が入っているので、PowerShell 5.1 で壊れるかもしれません。
- `deploy.ps1`、`verify.ps1`（監視を除く 15 項目）、`create-demo-account.ps1`、`Dockerfile` は、実際のプロジェクトで実行して、通りました。
- 本物のブラウザでの通し（ログイン、会話の夜、医療の質問への決まった文、審査用アカウント）は、本物の Gemini と Model Armor で通りました。読み上げの声は、耳では確かめていません。
- Model Armor が、日本語の危ない文をどのくらい見つけるかは、測っていません。
- 審査用アカウントのデモデータ（14 日分の記録と、朝のひとこと待ちの 1 晩）は、開かれた日が変わると、その日の日付で入れ直します。すでにある日は書き換えません。日付を決めるのは、アプリの時計（Cloud Run の時刻）です。
- 最小インスタンスが 0 なので、最初のアクセスは遅くなります。夜の画面を開く前に、起動を促す呼び出しを入れる案は、まだ実装していません。
- Cloud Trace への書き出しは、見送りました。いまは、構造化ログの `span` の行だけです。
