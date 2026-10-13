<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="登録" tab="-1" bare="true" says="はじめまして。" mood="smile" action="登録する" actionForm="registerForm">
  <c:if test="${not empty authMessage}"><p class="authmsg" role="alert"><c:out value="${authMessage}"/></p></c:if>
  <form id="registerForm" method="post" action="${base}/register"><y:csrf/>
    <y:sect head="アカウント" foot="パスワードは、8文字以上にしてください。確認のメールが届きます。">
      <label class="row"><span class="t">メールアドレス</span><input type="email" name="email" value="<c:out value='${authEmail}'/>" required maxlength="254" autocomplete="username" inputmode="email"></label>
      <label class="row"><span class="t">パスワード</span><input type="password" name="password" required minlength="8" maxlength="128" autocomplete="new-password"></label>
    </y:sect>
    <y:sect head="確認" foot="同意した版は、${termsVersion} です。">
      <label class="row radio"><input type="checkbox" name="age" value="on"><span class="k"></span><div class="t">18歳以上です</div></label>
      <label class="row radio"><input type="checkbox" name="terms" value="on"><span class="k"></span><div class="t"><a class="inline" href="${base}/terms">利用規約</a>と<a class="inline" href="${base}/privacy">プライバシー</a>に同意します</div></label>
    </y:sect>
  </form>
  <y:sect head="">
    <a class="row" href="${base}/login"><div class="t">登録済みの方は、ログイン</div><span class="v ch"></span></a>
  </y:sect>
</y:page>
