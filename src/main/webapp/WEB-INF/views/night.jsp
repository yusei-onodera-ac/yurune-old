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
      <%-- 通話の画面の並び（相手の絵、名前、経過時間、操作、通話を終えるボタン）に寄せた。通話の「終える」は、会話をやめて音だけにもどす --%>
      <div class="call">
        <div class="callmain">
          <div class="callface"><y:pot width="96" mood="open" talk="true"/></div>
          <h1 class="callname">ぽてね</h1>
          <div class="calltime" id="callTime">00:00</div>
          <div class="calldots" id="callDots" aria-hidden="true">
            <i style="--i:0"></i><i style="--i:1"></i><i style="--i:2"></i><i style="--i:3"></i><i style="--i:4"></i><i style="--i:5"></i><i style="--i:6"></i><i style="--i:7"></i><i style="--i:8"></i>
          </div>
          <%-- 会話は、声を文字にした発言を、この URL へ送って進める --%>
          <div id="nightTalk" data-url="${base}/night/talk" data-speech-rate="${nightSpeechRate}" data-speech-pitch="${nightSpeechPitch}" hidden></div>
          <%-- 画面に出すのは、ぽてねの最新の返事 1 つだけ（次の返事で入れ替わる）。利用者の発言は、画面に出さない --%>
          <div class="chat" aria-live="polite">
            <div class="msg"><div class="bub" id="talkReply" data-n="0">話したくなったら、話してね。</div></div>
          </div>
        </div>
        <div class="callstate">
          <div class="callhint" id="micHint">話しかけてね</div>
          <div class="soundstate"><span id="nightSoundState" data-sound-label="<c:out value='${nightSound}'/>">音は、まだ流れていません。</span></div>
        </div>
        <div class="callctrl">
          <button type="button" class="ctl" id="micButton" aria-pressed="true" aria-label="聞くのをお休みする">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/><path class="slash" d="M4 4l16 16"/></svg>
            <span id="micLabel">マイクをオフ</span>
          </button>
          <%-- 端末が音の再生を許さなかったときだけ出る（音の端末が、出し入れする） --%>
          <button type="button" class="ctl" id="soundStart" hidden>
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 9.5v5h3.5L12 18.5v-13L7.5 9.5H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/></svg>
            <span>音を流す</span>
          </button>
        </div>
        <form class="callend" method="post" action="${base}/night/mode"><y:csrf/>
          <input type="hidden" name="mode" value="sound">
          <button type="submit" class="hangup" aria-label="会話をやめて、音だけにもどる"><svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
          <span class="callendlabel">音だけにもどる</span>
        </form>
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
