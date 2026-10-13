<%@ tag body-content="scriptless" pageEncoding="UTF-8" description="全画面共通の html の骨組み" %>
<%@ attribute name="title" required="true" description="画面の名前" %>
<%@ attribute name="bodyClass" description="body に付ける class" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <meta name="csrf-token" content="<c:out value='${csrfToken}'/>">
  <title><c:out value="${title}"/> ・ ゆるね</title>
  <%-- ホーム画面に追加するためのもの。色は css/yurune.css の --bg と、夜の --n-bg に合わせる（manifest.webmanifest も同じ） --%>
  <link rel="manifest" href="${base}/manifest.webmanifest">
  <c:choose>
    <c:when test="${bodyClass == 'night'}"><meta name="theme-color" content="#0A0E11"></c:when>
    <c:otherwise>
      <meta name="theme-color" content="#F2F4F4" media="(prefers-color-scheme: light)">
      <meta name="theme-color" content="#12171B" media="(prefers-color-scheme: dark)">
    </c:otherwise>
  </c:choose>
  <meta name="mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-title" content="ゆるね">
  <link rel="icon" type="image/png" sizes="192x192" href="${base}/images/icons/icon-192.png">
  <link rel="apple-touch-icon" href="${base}/images/icons/apple-touch-icon.png">
  <meta name="service-worker" content="${base}/sw.js">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Noto+Sans+JP:wght@400;500;700&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="${base}/css/yurune.css">
  <script src="${base}/js/yurune.js" defer></script>
</head>
<body class="<c:out value='${bodyClass}'/>">
<jsp:doBody/>
</body>
</html>
