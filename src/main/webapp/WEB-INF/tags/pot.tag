<%@ tag body-content="empty" pageEncoding="UTF-8" description="ぽてね（動くキャラクター）" %>
<%@ attribute name="width" required="true" type="java.lang.Integer" description="幅(px)" %>
<%@ attribute name="mood" description="open / smile / closed" %>
<%@ attribute name="talk" type="java.lang.Boolean" description="話している間、口を動かす" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="moodName" value="${empty mood ? 'open' : mood}"/>
<button type="button" class="potwrap" aria-label="ぽてねをなでる">
  <svg class="pot m-${moodName}${talk ? ' talk' : ''}" width="${width}" height="${width * 0.92}" viewBox="0 0 100 92" role="img" aria-label="ぽてね">
    <ellipse class="sh" cx="50" cy="86" rx="30" ry="4.5" style="fill:var(--shadow)"/>
    <g class="hopg"><g class="bodyg">
      <ellipse class="arm l" cx="11" cy="68" rx="5.5" ry="7" style="fill:#fff;stroke:#AEBBB6" stroke-width="2"/>
      <ellipse class="arm r" cx="89" cy="68" rx="5.5" ry="7" style="fill:#fff;stroke:#AEBBB6" stroke-width="2"/>
      <path d="M12 52C10 28 28 10 50 10s40 18 38 42c-2 20-17 32-38 32S14 72 12 52z" style="fill:#fff;stroke:#AEBBB6" stroke-width="2"/>
      <path d="M33 17q17-6 34 0" style="stroke:#AEBBB6" fill="none" stroke-width="1.8" stroke-dasharray="3 3" stroke-linecap="round"/>
      <g class="eyes"><g class="e-open"><circle cx="37" cy="50" r="3.6" fill="#2B3A3A"/><circle cx="63" cy="50" r="3.6" fill="#2B3A3A"/></g></g>
      <g class="e-smile" fill="none" stroke="#2B3A3A" stroke-width="2.4" stroke-linecap="round"><path d="M32 51q5-6 10 0M58 51q5-6 10 0"/></g>
      <g class="e-closed" fill="none" stroke="#2B3A3A" stroke-width="2.4" stroke-linecap="round"><path d="M32 50q5 5 10 0M58 50q5 5 10 0"/></g>
      <ellipse cx="27" cy="61" rx="6.5" ry="3.8" fill="#F0A9B6" opacity=".8"/><ellipse cx="73" cy="61" rx="6.5" ry="3.8" fill="#F0A9B6" opacity=".8"/>
      <ellipse class="mouth" cx="50" cy="59" rx="3.2" ry="1.4" fill="#2B3A3A"/>
    </g></g>
    <text class="zz a" x="76" y="20">z</text><text class="zz b" x="82" y="12">z</text><text class="zz c" x="70" y="24">Z</text>
  </svg>
</button>
