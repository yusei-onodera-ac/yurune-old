<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="利用規約" tab="-1" bare="true">
  <y:sect head="このサービスについて">
    <div class="row"><div class="t">「ゆるね」は、睡眠の記録と、夜の音や会話の組み立てを助ける、個人開発の試作です。第5回 Agentic AI Hackathon with Google Cloud への提出のために、公開しています。</div></div>
  </y:sect>
  <y:sect head="医療機器ではありません">
    <div class="row"><div class="t">病気の診断や、薬についての助言は、行いません。つらさが続くときは、医師に相談してください。</div></div>
  </y:sect>
  <y:sect head="ご利用いただける方">
    <div class="row"><div class="t">18歳以上の方が、ご利用いただけます。</div></div>
  </y:sect>
  <y:sect head="してはいけないこと">
    <div class="row"><div class="t">他の方の個人情報の入力、他の方になりすますこと、サービスに過度な負荷をかけること、不正にアクセスすることは、できません。</div></div>
  </y:sect>
  <y:sect head="お断り">
    <div class="row"><div class="t">試作のため、予告なく、内容の変更、停止、データの消去を行うことがあります。結果の正確さや、特定の目的への適合は、保証できません。</div></div>
  </y:sect>
  <y:sect head="データの削除">
    <div class="row"><div class="t">設定の「データを消す」から、記録と、覚えたことを、いつでも消せます。</div></div>
  </y:sect>
  <y:sect head="規約の変更" foot="版: ${termsVersion}">
    <div class="row"><div class="t">この規約は、変更することがあります。変更したときは、版を更新します。</div></div>
  </y:sect>
  <y:sect head="">
    <a class="row" href="${base}/privacy"><div class="t">プライバシー</div><span class="v ch"></span></a>
    <a class="row" href="${base}/login"><div class="t">もどる</div><span class="v ch"></span></a>
  </y:sect>
</y:page>
