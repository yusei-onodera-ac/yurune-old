<%@ tag body-content="scriptless" pageEncoding="UTF-8" description="昼の画面の骨組み。どの画面も、この順に並べる" %>
<%@ attribute name="title" required="true" description="大見出し" %>
<%@ attribute name="tab" required="true" type="java.lang.Integer" description="タブバーで選ぶ番号（0 から）" %>
<%@ attribute name="says" description="ぽてねの吹き出しの文。縦棒で区切る。空ならぽてねの行を出さない" %>
<%@ attribute name="mood" description="ぽてねの表情 open / smile / closed" %>
<%@ attribute name="action" description="下に 1 つだけ置く主操作の文字" %>
<%@ attribute name="actionHref" description="主操作の移動先" %>
<%@ attribute name="actionForm" description="主操作で送る form の id" %>
<%@ attribute name="actionHidden" type="java.lang.Boolean" description="主操作を最初は隠す" %>
<%@ attribute name="bare" type="java.lang.Boolean" description="タブバーを出さない（ログイン前の画面）" %>
<%@ attribute name="overlay" fragment="true" description="画面の上に重ねるもの（朝のひとこと）" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<c:set var="firstSay" value="${fn:contains(says, '|') ? fn:substringBefore(says, '|') : says}"/>
<y:document title="${title}" bodyClass="">
<div class="app">
  <div class="top"></div>
  <h1 class="title"><c:out value="${title}"/></h1>
  <div class="body">
    <c:if test="${not empty says}">
      <div class="pr">
        <y:pot width="56" mood="${empty mood ? 'open' : mood}"/>
        <div class="say" id="say" data-says="<c:out value='${says}'/>"><c:out value="${firstSay}"/></div>
      </div>
    </c:if>
    <jsp:doBody/>
    <c:if test="${demoAccount}"><p class="notice">審査員用のデモアカウントです。この画面の記録や夜は、デモ用のデータです。</p></c:if>
    <p class="notice">つらさが続くときは、医師に相談してください。<br>ゆるねは医療機器ではありません。</p>
  </div>
  <c:if test="${not empty action}">
    <div class="action" id="action" ${actionHidden ? 'hidden' : ''}>
      <c:choose>
        <c:when test="${not empty actionHref}"><a class="btn" href="${base}${actionHref}"><c:out value="${action}"/></a></c:when>
        <c:when test="${not empty actionForm}"><button type="submit" class="btn" form="${actionForm}"><c:out value="${action}"/></button></c:when>
        <c:otherwise><button type="button" class="btn"><c:out value="${action}"/></button></c:otherwise>
      </c:choose>
    </div>
  </c:if>
  <c:if test="${not bare}">
  <nav class="tabbar" aria-label="メニュー">
    <a href="${base}/home" ${tab == 0 ? 'data-on aria-current="page"' : ''}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 11l8-7 8 7v9H4z"/><path d="M10 20v-6h4v6"/></svg>ホーム</a>
    <a href="${base}/log" ${tab == 1 ? 'data-on aria-current="page"' : ''}><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 9h6M9 13h6M9 17h3"/></svg>記録</a>
    <a href="${base}/prepare" ${tab == 2 ? 'data-on aria-current="page"' : ''}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/></svg>夜</a>
    <a href="${base}/review" ${tab == 3 ? 'data-on aria-current="page"' : ''}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 20V10M12 20V4M19 20v-7"/></svg>ふりかえり</a>
    <a href="${base}/settings" ${tab == 4 ? 'data-on aria-current="page"' : ''}><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/></svg>設定</a>
  </nav>
  </c:if>
  <c:if test="${overlay != null}"><jsp:invoke fragment="overlay"/></c:if>
</div>
</y:document>
