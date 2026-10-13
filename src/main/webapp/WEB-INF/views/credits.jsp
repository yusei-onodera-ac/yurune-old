<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="音源のクレジット" tab="4">
  <y:sect head="">
    <a class="row" href="${base}/settings"><div class="t">設定にもどる</div><span class="v ch"></span></a>
  </y:sect>
  <y:sect head="いまの音の出し方">
    <div class="row"><div class="t">夜の音<small>雨・波・森の音は、アプリの中で合成しています。外部の音源のファイルは使っていません。</small></div></div>
    <div class="row"><div class="t">ぽてねの声<small>お使いの端末（ブラウザ）の読み上げ機能を使います。サーバーでの読み上げを設定したときは、Google Cloud Text-to-Speech の声を使います。</small></div></div>
  </y:sect>
  <c:choose>
    <c:when test="${empty soundCredits}">
      <y:sect head="使っている音源" foot="外部の音源を入れたときは、ここに、名前・ライセンス・出典・取得日を載せます。">
        <div class="row"><div class="t">いまは、載せるものがありません。</div></div>
      </y:sect>
    </c:when>
    <c:otherwise>
      <y:sect head="使っている音源" foot="ライセンスを確認できたものだけを載せています。">
        <c:forEach var="credit" items="${soundCredits}">
          <a class="row" href="<c:out value='${credit.sourceUrl}'/>" target="_blank" rel="noopener noreferrer">
            <div class="t"><c:out value="${credit.nameText}"/><small>ライセンス: <c:out value="${credit.licenseText}"/> ・ 取得日: <c:out value="${credit.acquiredText}"/> ・ キー: <c:out value="${credit.keyText}"/></small></div>
            <span class="v ch">出典</span>
          </a>
        </c:forEach>
      </y:sect>
    </c:otherwise>
  </c:choose>
</y:page>
