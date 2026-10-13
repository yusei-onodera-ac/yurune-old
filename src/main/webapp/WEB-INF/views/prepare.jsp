<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="夜の準備" tab="2" mood="smile" says="${says}" action="はじめる" actionForm="startForm">
  <form id="startForm" method="post" action="${base}/night" hidden><y:csrf/></form>
  <y:sect head="すごし方">
    <label class="row radio">
      <input type="radio" name="mode" value="sound" form="startForm" checked>
      <span class="k"></span>
      <div class="t">音だけ<small>音楽・環境音。画面は暗くなります</small></div>
    </label>
    <c:if test="${talkEnabled}">
      <label class="row radio">
        <input type="radio" name="mode" value="talk" form="startForm">
        <span class="k"></span>
        <div class="t">ぽてねと話す<small>話しかけなくても大丈夫です</small></div>
      </label>
    </c:if>
  </y:sect>
  <y:sect head="ぽてねの提案">
    <c:forEach var="item" items="${planItems}">
      <div class="row">
        <div class="t">
          <c:out value="${item.label}"/>
          <c:if test="${item.changed}"> <span class="tag">今夜変える</span></c:if>
        </div>
        <span class="v"><c:out value="${item.valueText}"/></span>
      </div>
    </c:forEach>
    <div class="row"><div class="t">理由<small><c:out value="${planReason}"/></small></div></div>
  </y:sect>
</y:page>
