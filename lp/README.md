# 紹介ページ

ゆるねの紹介ページです。HTML が 2 枚（紹介のページと、制作者についてのページ）と、画面の画像だけで、サーバー側の仕組みは要りません。静的なファイルを置ける場所なら、どこにでも公開できます。置くのは `dist` の中身です。

## 作り直す

文章と形の元は、紹介のページが `template.html`、制作者のページが `about.template.html` です。ぽてねの絵は `pot-parts.html` に 1 つだけあり、ビルドのときに、各所へ入ります。次を実行すると、`dist/index.html`、`dist/about.html`、`dist/images` ができます。

```
node build.js
```

アプリの URL、GitHub、プライバシーポリシー、利用規約、ご意見の送り先は、`build.js` の `LINKS` にまとめてあります。アプリの URL が変わったときは、`APP_URL` を書き換えてから、ビルドし直します。ご意見の送り先は、いまは GitHub の Issues です。公開される送り先なので、別のものにするときは、`template.html` のボタンの下の注意書きも直してください。

画像を埋め込んだ 1 枚のファイル `artifact.html` も、同じ場所にできます（確認用で、リポジトリには入れていません）。

文字の確認は、次のとおりです。ページの文字を一覧にして、使わないと決めた言葉が入っていないかを調べます。制作者のページは、`node text_check.js about.html` です。

```
node text_check.js
```

## 動き

ぽてねは、呼吸や、まばたき、腕の揺れで動き、触れると目を開けて跳ねます。夜の入口には星がまたたき、ページを下へ読み進めると、文や画面が順に現れます。どれも CSS だけで、JavaScript は使っていません。端末の設定で「視差効果を減らす」（動きを減らす）がオンのときは、すべて止まります。スクロールに合わせて現れる動きは、対応していないブラウザでは、最初から全部見えます。

スクロールで現れる動きがあるので、ページ全体を一度に書き出す確認（`node render_check.js`）では、下のほうが空白に写ります。ふつうに読み進める画面で確かめてください。

## 公開する

Firebase Hosting に載せます。Firebase の CLI は要らず、gcloud でログインしたアカウントの権限で、API から送ります。`dist` の中身を送り、直前の版は履歴に残ります。

```
gcloud auth login
node build.js
powershell -ExecutionPolicy Bypass -File publish.ps1 -ProjectId <プロジェクト ID>
```

公開した URL は、プロジェクト ID を使った `https://<プロジェクト ID>.web.app` です。セキュリティのヘッダー（読み込めるものを自分の画像と Google Fonts だけに絞る CSP など）と、キャッシュの指示は、`publish.ps1` の中で付けています。

## 画面の画像を撮り直す

画像は `shots` にあります。アプリの見た目を変えたときは、アプリを動かして、撮り直します。アプリは、メモリ上で、審査用アカウントつきで動かします（[../README.md](../README.md)）。審査用アカウントは、`demo@example.com` と `demo-pass-123` を想定しています。

```
set APP_BASE_URL=http://localhost:18080
set OUTPUT_DIR=（撮った画像を置く、すでにあるフォルダ）
node take_screens.js
```

撮った画像を `shots` に移して、`node build.js` をやり直します。Chrome と Node.js 24 が要ります。

画像は、審査用アカウントで撮るので、画面の数字は、どれもデモ用のデータです。ページにも、そう書いています。
