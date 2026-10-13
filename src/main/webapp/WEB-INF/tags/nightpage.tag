<%@ tag body-content="scriptless" pageEncoding="UTF-8" description="夜の画面の骨組み。暗い専用の見た目" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<y:document title="夜" bodyClass="night">
<div class="app night">
  <jsp:doBody/>
</div>
<%-- 夜の音の合成。夜の画面にだけ読む（ほかの画面では、何もしない） --%>
<script src="${pageContext.request.contextPath}/js/nightsound.js" defer></script>
</y:document>
