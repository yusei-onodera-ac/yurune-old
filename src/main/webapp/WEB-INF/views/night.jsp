<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="y" tagdir="/WEB-INF/tags" %>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<y:nightpage>
  <%-- 端末が観測を送る先と間隔。画面には出さない --%>
  <%-- 端末の音が、読み込みのときから、同じ曲と音量で鳴り始められるよう、いまの音の状態を渡す。音は、端末が合成する --%>
  <div id="nightObserve" data-url="${base}/night/observe" data-end-url="${base}/night/end" data-interval="${nightIntervalSec}" data-no-reaction-sec="${nightNoReactionSec}" data-home="${base}/home" data-started-at="${nightStartedAt}" data-volume-cap="${nightVolumeCap}" data-sound-key="${nightSoundKey}" data-sound-pct="${nightSoundVolumePct}" data-fade-sec="${nightFadeRemainingSec}" data-keep-screen-on="${nightKeepScreenOn}" hidden></div>
  <c:choose>
    <c:when test="${nightMode == 'talk'}">
      <div class="chead">
        <y:pot width="84" mood="open" talk="true"/>
        <small>ぽてね ・ 話しています</small>
        <div class="soundstate">
          <span id="nightSoundState" data-sound-label="<c:out value='${nightSound}'/>">音は、まだ流れていません。</span>
          <button type="button" class="nightlink" id="soundStart" hidden>音を流す</button>
        </div>
      </div>
      <%-- 会話は、声を文字にした発言を、この URL へ送って進める。吹き出しは、画面の中にだけ置く --%>
      <div id="nightTalk" data-url="${base}/night/talk" data-speech-rate="${nightSpeechRate}" data-speech-pitch="${nightSpeechPitch}" hidden></div>
      <div class="body">
        <%-- 画面に出すのは、ぽてねの最新の返事 1 つだけ（次の返事で入れ替わる）。利用者の発言は、画面に出さない --%>
        <div class="chat" aria-live="polite">
          <div class="msg"><div class="bub" id="talkReply" data-n="0">話したくなったら、話してね。</div></div>
        </div>
      </div>
      <form class="nightlinkrow" method="post" action="${base}/night/mode"><y:csrf/>
        <input type="hidden" name="mode" value="sound">
        <button type="submit" class="nightlink">音だけにもどる</button>
      </form>
      <div class="mic">
        <div class="field" id="micHint">話しかけてね</div>
        <button type="button" class="m" id="micButton" aria-pressed="true" aria-label="聞くのをお休みする"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#BFE5E1" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg></button>
      </div>
    </c:when>
    <c:otherwise>
      <div class="nightmid">
        <div class="potdim"><y:pot width="86" mood="closed"/></div>
        <div class="clock" id="nightClock" data-total-minutes="${nightTotalMinutes}">--:--</div>
        <div class="nightsub"><c:out value="${nightSound}"/> ・ <span id="nightRemaining">あと <c:out value="${nightTotalMinutes}"/>分</span></div>
        <div class="soundstate">
          <span id="nightSoundState" data-sound-label="<c:out value='${nightSound}'/>">音は、まだ流れていません。</span>
          <button type="button" class="nightlink" id="soundStart" hidden>音を流す</button>
        </div>
        <form method="post" action="${base}/night/end"><y:csrf/><button type="submit" class="endbtn">終わる</button></form>
      </div>
      <c:if test="${talkEnabled}">
        <form class="nightlinkrow" method="post" action="${base}/night/mode"><y:csrf/>
          <input type="hidden" name="mode" value="talk">
          <button type="submit" class="nightlink">ぽてねと話す</button>
        </form>
      </c:if>
      <p class="notice">眠っても大丈夫です。反応がなくなったら、静かに終わります。</p>
    </c:otherwise>
  </c:choose>
</y:nightpage>
