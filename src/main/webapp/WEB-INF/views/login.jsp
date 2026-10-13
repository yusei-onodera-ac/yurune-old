<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="ログイン" tab="-1" bare="true" says="おかえり。" mood="smile" action="ログイン" actionForm="loginForm">
  <c:if test="${not empty authMessage}"><p class="authmsg" role="alert"><c:out value="${authMessage}"/></p></c:if>
  <form id="loginForm" method="post" action="${base}/login"><y:csrf/>
    <y:sect head="アカウント">
      <label class="row"><span class="t">メールアドレス</span><input type="email" name="email" value="<c:out value='${authEmail}'/>" required maxlength="254" autocomplete="username" inputmode="email"></label>
      <label class="row"><span class="t">パスワード</span><input type="password" name="password" required maxlength="128" autocomplete="current-password"></label>
    </y:sect>
  </form>
  <y:sect head="">
    <a class="row" href="${base}/register"><div class="t">はじめての方は、登録<small>18歳以上の方がご利用いただけます。</small></div><span class="v ch"></span></a>
  </y:sect>
</y:page>
