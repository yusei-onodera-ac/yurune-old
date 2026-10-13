<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="ホーム" tab="0" mood="smile" says="${says}" action="今夜の準備をする" actionHref="/prepare">
  <jsp:attribute name="overlay">
    <c:if test="${not empty morningSheet}">
      <div class="sheetbg">
        <form class="sheet" method="post" action="${base}/morning" role="dialog" aria-label="朝のひとこと"><y:csrf/>
          <div class="sh-top">
            <y:pot width="52" mood="smile"/>
            <div class="say2">
              <b>おはよう。</b><br>
              <c:choose>
                <c:when test="${morningSheet.bedKnown}">昨夜は <c:out value="${morningSheet.bedText}"/> ごろに寝て、<c:out value="${morningSheet.wakeText}"/> に起きたよ。</c:when>
                <c:otherwise>昨夜の時刻が、まだ分からないよ。教えてね。</c:otherwise>
              </c:choose>
            </div>
          </div>
          <div class="sh-fix">
            <label class="chip"><input type="radio" name="timeMode" value="ok" ${morningSheet.bedKnown ? 'checked' : ''}><span>合ってる</span></label>
            <label class="chip"><input type="radio" name="timeMode" value="fix" ${morningSheet.bedKnown ? '' : 'checked'}><span>直す</span></label>
          </div>
          <div class="sh-times" id="sheetTimes" ${morningSheet.bedKnown ? 'hidden' : ''}>
            <input type="time" name="bed" value="<c:out value='${morningSheet.bedText}'/>" aria-label="寝た時刻">
            <span>→</span>
            <input type="time" name="wake" value="<c:out value='${morningSheet.wakeText}'/>" aria-label="起きた時刻">
          </div>
          <div class="sh-q">いまの気分</div>
          <div class="seg c5">
            <c:forEach var="mood" items="${moods}">
              <label><input type="radio" name="mood" value="<c:out value='${mood.id}'/>"><span><c:out value="${mood.label}"/></span></label>
            </c:forEach>
          </div>
          <div class="sh-q">昨夜のすごし方</div>
          <div class="seg c3">
            <c:forEach var="answer" items="${answers}">
              <label><input type="radio" name="answer" value="<c:out value='${answer.id}'/>"><span><c:out value="${answer.label}"/></span></label>
            </c:forEach>
          </div>
          <button class="btn" type="submit">保存する</button>
          <button class="linkq" type="submit" name="skip" value="1">答えずに閉じる</button>
          <p class="sh-note">気分とすごし方は、答えなくても保存できます。</p>
        </form>
      </div>
    </c:if>
  </jsp:attribute>
  <jsp:body>
    <y:sect head="昨夜">
      <div class="stats">
        <div><b><c:out value="${lastNight.durationClockText}"/></b><span>睡眠時間</span></div>
        <div><b><c:out value="${empty lastNight.bedText ? '—' : lastNight.bedText}"/></b><span>就寝</span></div>
        <div><b><c:out value="${empty lastNight.wakeText ? '—' : lastNight.wakeText}"/></b><span>起床</span></div>
      </div>
    </y:sect>
  </jsp:body>
</y:page>
