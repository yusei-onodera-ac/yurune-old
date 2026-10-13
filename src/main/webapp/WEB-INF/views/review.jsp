<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<c:set var="openGoal" value="${param.panel == 'goal'}"/>
<y:page title="ふりかえり" tab="3" mood="smile" says="${openGoal ? goalSay : weekSay}">
  <div class="group">
    <div class="seg c2" data-seg="review">
      <button type="button" aria-pressed="${openGoal ? 'false' : 'true'}" data-panel="week" data-say="<c:out value='${weekSay}'/>">週</button>
      <button type="button" aria-pressed="${openGoal ? 'true' : 'false'}" data-panel="goal" data-say="<c:out value='${goalSay}'/>">目標</button>
    </div>
  </div>

  <%-- 週 --%>
  <div class="fbody" data-seg-panel="review:week" ${openGoal ? 'hidden' : ''}>
    <c:choose>
      <c:when test="${week.shown}">
        <y:sect head="先週との比較">
          <div class="row"><div class="t">平均睡眠時間</div><span class="v"><c:out value="${week.thisAverageText}"/></span></div>
          <div class="row"><div class="t">先週</div><span class="v"><c:out value="${week.previousAverageText}"/></span></div>
          <div class="row"><div class="t">差</div><span class="v gain"><c:out value="${week.gainText}"/></span></div>
        </y:sect>
        <y:sect head="曜日ごと">
          <div style="padding:12px 14px 8px">
            <svg viewBox="0 0 300 130" width="100%" role="img" aria-label="曜日ごとの睡眠時間。先週と今週">
              <c:forEach var="line" items="${week.gridLines}">
                <line x1="26" x2="296" y1="${line.lineY}" y2="${line.lineY}" style="stroke:var(--line)"/>
                <text x="0" y="${line.textY}" font-size="9" style="fill:var(--sub)"><c:out value="${line.label}"/></text>
              </c:forEach>
              <c:forEach var="bar" items="${week.barGroups}">
                <c:if test="${bar.previousRecorded}"><rect x="${bar.previousX}" y="${bar.previousY}" width="11" height="${bar.previousHeight}" rx="3" style="fill:var(--line)"/></c:if>
                <c:if test="${bar.thisRecorded}"><rect x="${bar.thisX}" y="${bar.thisY}" width="11" height="${bar.thisHeight}" rx="3" style="fill:var(--accent)"/></c:if>
                <text x="${bar.labelX}" y="120" font-size="10" text-anchor="middle" style="fill:var(--sub)"><c:out value="${bar.label}"/></text>
              </c:forEach>
            </svg>
            <div style="display:flex;gap:14px;font-size:.75rem;color:var(--sub)"><span>■ 先週</span><span style="color:var(--accent)">■ 今週</span></div>
          </div>
        </y:sect>
      </c:when>
      <c:otherwise>
        <y:sect head="先週との比較" foot="比較は、記録がそろい、変化があるときだけ出ます。">
          <div class="row"><div class="t">いまは、比べるものがありません</div></div>
        </y:sect>
      </c:otherwise>
    </c:choose>
  </div>

  <%-- 目標 --%>
  <div class="fbody" data-seg-panel="review:goal" ${openGoal ? '' : 'hidden'}>
    <y:sect head="いまの目標">
      <div class="row"><div class="t"><c:out value="${goalLabel}"/></div></div>
      <div class="row"><div class="t"><c:out value="${goalDescription}"/></div></div>
      <a class="row" href="${base}/settings/goal"><div class="t">目標を変える<small>変えると、次の見直しで、比べ直すよ。</small></div><span class="v ch"></span></a>
    </y:sect>
    <%-- LINT:BEGIN 「いまのところ」（文言の検査の対象。受診のすすめも、ここだけに出る） --%>
    <y:sect head="いまのところ">
      <c:forEach var="line" items="${noteSummary}">
        <div class="row"><div class="t"><c:out value="${line}"/></div></div>
      </c:forEach>
      <c:if test="${noteReferShown}">
        <form method="post" action="${base}/review/refer/hide"><y:csrf/>
          <button type="submit" class="row"><div class="t">今は表示しない</div></button>
        </form>
      </c:if>
    </y:sect>
    <%-- LINT:END --%>
  </div>
</y:page>
