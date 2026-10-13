<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="設定" tab="4">
  <c:if test="${param.erased == '1'}"><p class="authmsg" role="status">記録と、覚えたことを、消しました。</p></c:if>
  <p class="authmsg" role="alert" id="settingsError" hidden>保存できませんでした。もう一度お試しください。</p>
  <y:sect head="夜">
    <div class="row"><div class="t">夜の会話<small>初期値はオン</small></div><y:toggle item="night_talk" label="夜の会話" checked="${settings.nightTalkEnabled}"/></div>
    <a class="row" href="${base}/settings/volume"><div class="t">音量の上限</div><span class="v ch"><c:out value="${settings.volumeCapText}"/></span></a>
    <a class="row" href="${base}/settings/voice"><div class="t">ぽてねの声</div><span class="v ch"><c:out value="${settings.voiceLabel}"/></span></a>
    <div class="row"><div class="t">夜のあいだ、画面をつけたままにする<small>電池を、いつもより少し使います。</small></div><y:toggle item="keep_screen_on" label="夜のあいだ、画面をつけたままにする" checked="${settings.keepScreenOnEnabled}"/></div>
  </y:sect>
  <y:sect head="学習" foot="会話の全文は保存しません。">
    <div class="row"><div class="t">好みを覚える<small>夜のやり方に反映します</small></div><y:toggle item="learning" label="好みを覚える" checked="${settings.learningEnabled}"/></div>
    <a class="row" href="${base}/settings/goal"><div class="t">夜のめやす</div><span class="v ch"><c:out value="${settings.goalLabel}"/></span></a>
    <a class="row" href="${base}/settings/memory"><div class="t">覚えたことを見る<small>構造化した項目だけ</small></div><span class="v ch"></span></a>
  </y:sect>
  <y:sect head="データ">
    <a class="row" href="${base}/settings/erase"><div class="t">データを消す<small>記録と、覚えたことをすべて削除</small></div><span class="v ch"></span></a>
  </y:sect>
  <y:sect head="情報">
    <a class="row" href="${base}/settings/credits"><div class="t">音源のクレジット</div><span class="v ch"></span></a>
    <a class="row" href="${base}/terms"><div class="t">利用規約</div><span class="v ch"></span></a>
    <a class="row" href="${base}/privacy"><div class="t">プライバシー</div><span class="v ch"></span></a>
    <a class="row" href="${base}/settings/guide"><div class="t">ご利用にあたって</div><span class="v ch"></span></a>
  </y:sect>
  <y:sect head="アカウント">
    <form method="post" action="${base}/logout"><y:csrf/><button type="submit" class="row"><div class="t">ログアウト</div></button></form>
  </y:sect>
</y:page>
