<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="${choiceTitle}" tab="4">
  <form method="post" action="${base}/settings/update"><y:csrf/>
    <input type="hidden" name="item" value="<c:out value='${choiceItem}'/>">
    <y:sect head="" foot="${choiceFoot}">
      <c:forEach var="choice" items="${choices}">
        <button type="submit" class="row radio" name="value" value="<c:out value='${choice.id}'/>"${choice.checked ? ' aria-current="true"' : ''}>
          <span class="k"></span>
          <div class="t"><c:out value="${choice.label}"/><c:if test="${not empty choice.hint}"><small><c:out value="${choice.hint}"/></small></c:if></div>
        </button>
      </c:forEach>
    </y:sect>
  </form>
  <y:sect head="">
    <a class="row" href="${base}/settings"><div class="t">設定へもどる</div><span class="v ch"></span></a>
  </y:sect>
</y:page>
