<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="status" value="${pageContext.errorData.statusCode}"/>
<y:page title="エラー" tab="-1">
  <y:sect head="">
    <div class="row"><div class="t">
      <c:choose>
        <c:when test="${status == 403}">この操作は、できませんでした。画面を開き直して、もう一度お試しください。</c:when>
        <c:when test="${status == 404}">ページが見つかりませんでした。</c:when>
        <c:when test="${status == 400 || status == 405}">送られた内容を、受け付けられませんでした。</c:when>
        <c:otherwise>いまは、表示できません。時間をおいて、お試しください。</c:otherwise>
      </c:choose>
    </div></div>
  </y:sect>
</y:page>
