<%@ tag body-content="empty" pageEncoding="UTF-8" description="状態を変える form に置く、CSRF トークンの隠し欄" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>">
