<%@ tag body-content="empty" pageEncoding="UTF-8" description="設定のスイッチ。押すと、その項目の on / off を保存する（スクリプトが動かなくても、フォームとして送れる）" %>
<%@ attribute name="item" required="true" description="設定の項目の名前（night_talk、learning）" %>
<%@ attribute name="label" required="true" description="スイッチの名前（読み上げ用）" %>
<%@ attribute name="checked" required="true" type="java.lang.Boolean" description="いま on か" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<form method="post" action="${base}/settings/update" data-switch><y:csrf/>
  <input type="hidden" name="item" value="<c:out value='${item}'/>">
  <input type="hidden" name="value" value="${checked ? 'off' : 'on'}">
  <button type="submit" class="sw" role="switch" aria-checked="${checked}" aria-label="<c:out value='${label}'/>"></button>
</form>
