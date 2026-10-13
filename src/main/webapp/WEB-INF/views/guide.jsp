<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:page title="ご利用にあたって" tab="4">
  <y:sect head="">
    <a class="row" href="${base}/settings"><div class="t">設定にもどる</div><span class="v ch"></span></a>
  </y:sect>
  <y:sect head="ゆるねについて">
    <div class="row"><div class="t">ゆるねは、睡眠の記録と、夜のすごし方の組み立てを助ける、睡眠のサポートです。</div></div>
  </y:sect>
  <y:sect head="医療ではありません">
    <div class="row"><div class="t">病気の診断や、薬についての助言は、行いません。</div></div>
    <div class="row"><div class="t">夜の会話で、病気や薬のことをたずねたときは、次のようにお返事します。<small>「つらさが続くときは、医師に相談してください。ここでは、病気や薬のことには答えられません。」</small></div></div>
  </y:sect>
  <y:sect head="夜のすごし方">
    <div class="row"><div class="t">眠っても大丈夫です。反応がなくなったら、静かに終わります。</div></div>
    <div class="row"><div class="t">会話は、いつでも「音だけにもどる」で、やめられます。</div></div>
    <div class="row"><div class="t">朝のひとことは、答えずに閉じることもできます。</div></div>
  </y:sect>
  <y:sect head="数字の見方">
    <div class="row"><div class="t">7晩の履歴の「合っていた」などは、朝のひとことの答えです。ふりかえりと「覚えたこと」の数字は、標準の夜と比べて、めやすがよかった夜の数です。数字は、記録の事実を示すだけで、何かの結果を保証するものではありません。</div></div>
  </y:sect>
  <y:sect head="くわしくは">
    <a class="row" href="${base}/terms"><div class="t">利用規約</div><span class="v ch"></span></a>
    <a class="row" href="${base}/privacy"><div class="t">プライバシー</div><span class="v ch"></span></a>
  </y:sect>
</y:page>
