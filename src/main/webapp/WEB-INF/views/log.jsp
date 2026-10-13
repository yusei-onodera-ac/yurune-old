<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="記録" tab="1" mood="open" says="${says}">
  <y:sect head="過去7日" foot="日付を押すと、時刻を直せます。通院・診断・処方に関する項目は、ありません。">
    <c:forEach var="day" items="${days}">
      <div class="dayitem">
        <button type="button" class="row dayhead" aria-expanded="false">
          <div class="t">
            <c:out value="${day.dateLabel}"/>
            <small>
              <c:choose>
                <c:when test="${day.recorded}"><c:out value="${day.bedText}"/> → <c:out value="${day.wakeText}"/> ・ <c:out value="${day.durationText}"/></c:when>
                <c:otherwise>まだ記録がありません</c:otherwise>
              </c:choose>
            </small>
          </div>
          <span class="v ch"><c:out value="${day.recorded ? '' : '入力'}"/></span>
        </button>
        <form class="dayedit" method="post" action="${base}/log" hidden><y:csrf/>
          <input type="hidden" name="date" value="<c:out value='${day.isoDate}'/>">
          <div class="row"><div class="t">寝た時刻</div><input type="time" name="bed" value="<c:out value='${day.bedText}'/>" aria-label="寝た時刻" required></div>
          <div class="row"><div class="t">起きた時刻</div><input type="time" name="wake" value="<c:out value='${day.wakeText}'/>" aria-label="起きた時刻" required></div>
        </form>
      </div>
    </c:forEach>
  </y:sect>
</y:page>
