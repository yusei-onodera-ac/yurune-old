<%@ tag body-content="scriptless" pageEncoding="UTF-8" description="見出し・グループ・補足の 1 かたまり" %>
<%@ attribute name="head" description="見出し" %>
<%@ attribute name="foot" description="補足" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<div class="sect">
  <c:if test="${not empty head}"><h3><c:out value="${head}"/></h3></c:if>
  <div class="group"><jsp:doBody/></div>
  <c:if test="${not empty foot}"><p><c:out value="${foot}"/></p></c:if>
</div>
