<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="プライバシー" tab="-1" bare="true">
  <y:sect head="保存するもの">
    <div class="row"><div class="t">メールアドレス（ログインのため。認証の仕組みが保管します）</div></div>
    <div class="row"><div class="t">就寝と起床の時刻、朝の答え（気分、昨夜のすごし方）</div></div>
    <div class="row"><div class="t">夜の計画と、エージェントの判断の記録（見たこと、選んだこと、理由）</div></div>
    <div class="row"><div class="t">選択肢ごとの回数（学習のための集計）</div></div>
    <div class="row"><div class="t">同意した規約の版と、時刻</div></div>
  </y:sect>
  <y:sect head="保存しないもの">
    <div class="row"><div class="t">会話の全文、音声、その要約は、保存しません。ログにも、出しません。</div></div>
  </y:sect>
  <y:sect head="会話のときに、外へ送られるもの">
    <div class="row"><div class="t">話した内容は、文字になり、電話番号やメールアドレスなどの形をした部分を伏せ字にしたうえで、Google の Gemini API に送られ、返事が作られます。送った内容は、ゆるねには残しません。Google 側での扱いは、Google の規約に従います。</div></div>
    <div class="row"><div class="t">話した内容（伏せ字にしたあと）と、ぽてねの返事は、安全のための検査として、Google Cloud の検査サービス（Model Armor。米国のリージョン）にも送られます。検査の結果だけを使い、ゆるねには残しません。</div></div>
    <div class="row"><div class="t">声を文字にする部分は、お使いのブラウザの機能です。ブラウザの提供元に、音声が送られることがあります。</div></div>
    <div class="row"><div class="t">夜の会話の画面では、ぽてねが話していないあいだ、マイクが開いています。ボタンで、いつでもお休みにできます。声がないまま10分ほど続くと、自分で止まります。</div></div>
  </y:sect>
  <y:sect head="保管する場所">
    <div class="row"><div class="t">Google Cloud のデータベース（Firestore）に、利用者ごとに分けて、保管します。</div></div>
  </y:sect>
  <y:sect head="第三者への提供">
    <div class="row"><div class="t">法令に基づく場合を除き、第三者に提供しません。</div></div>
  </y:sect>
  <y:sect head="データの削除" foot="版: ${termsVersion}">
    <div class="row"><div class="t">設定の「データを消す」から、記録と、覚えたことを、いつでも消せます。</div></div>
  </y:sect>
  <y:sect head="">
    <a class="row" href="${base}/terms"><div class="t">利用規約</div><span class="v ch"></span></a>
    <a class="row" href="${base}/login"><div class="t">もどる</div><span class="v ch"></span></a>
  </y:sect>
</y:page>
