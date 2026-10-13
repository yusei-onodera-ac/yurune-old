<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="データを消す" tab="4" action="消す" actionForm="eraseForm">
  <c:if test="${not empty authMessage}"><p class="authmsg" role="alert"><c:out value="${authMessage}"/></p></c:if>
  <form id="eraseForm" method="post" action="${base}/settings/erase"><y:csrf/>
    <y:sect head="確認" foot="記録と、覚えたことを、すべて消します。元に戻せません。ログインのためのアカウントは、消えません。">
      <label class="row"><span class="t">パスワード</span><input type="password" name="password" required maxlength="128" autocomplete="current-password"></label>
    </y:sect>
  </form>
  <y:sect head="">
    <a class="row" href="${base}/settings"><div class="t">やめる</div><span class="v ch"></span></a>
  </y:sect>
</y:page>
