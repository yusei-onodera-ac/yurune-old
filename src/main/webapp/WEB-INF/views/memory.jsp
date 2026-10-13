<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="覚えたこと" tab="4">
  <y:sect head="">
    <a class="row" href="${base}/settings"><div class="t">設定にもどる</div><span class="v ch"></span></a>
  </y:sect>
  <c:choose>
    <c:when test="${empty memoryGroups}">
      <y:sect head="覚えていること" foot="夜をすごして、朝のひとことに答えると、ここに、試した回数が並びます。">
        <div class="row"><div class="t">まだ、覚えていることはありません。</div></div>
      </y:sect>
    </c:when>
    <c:otherwise>
      <c:forEach var="group" items="${memoryGroups}">
        <y:sect head="${group.heading}">
          <c:forEach var="row" items="${group.rows}">
            <div class="row">
              <div class="t">
                <c:out value="${row.optionText}"/>
                <c:if test="${not empty row.labelText}"> <span class="tag ${row.labelMuted ? 'mute' : ''}"><c:out value="${row.labelText}"/></span></c:if>
                <small>試した <c:out value="${row.trialCount}"/>回 ・ <c:out value="${memoryCountPhrase}"/> <c:out value="${row.winCount}"/>回</small>
              </div>
            </div>
          </c:forEach>
        </y:sect>
      </c:forEach>
      <y:sect head="数え方">
        <div class="row"><div class="t">夜を終えたあとで、標準の夜と比べて数えています。朝の答えは、ここでは数えません。</div></div>
        <div class="row"><div class="t">「いまの第一候補」は、<c:out value="${memoryMinTrials}"/>回以上試したもののうち、標準の夜より、めやすがよかった割合が最も高いものに付く目印です。回数が少ないうちは、「もう少し試す」と出ます。</div></div>
      </y:sect>
    </c:otherwise>
  </c:choose>
  <y:sect head="保存していないもの" foot="会話の全文は保存しません。">
    <div class="row"><div class="t">会話の本文、声、自由に書いた文章は、覚えません。</div></div>
  </y:sect>
  <y:sect head="">
    <a class="row" href="${base}/privacy"><div class="t">保存しているものの一覧<small>プライバシー</small></div><span class="v ch"></span></a>
    <a class="row" href="${base}/settings/erase"><div class="t">データを消す<small>記録と、覚えたことをすべて削除</small></div><span class="v ch"></span></a>
  </y:sect>
</y:page>
