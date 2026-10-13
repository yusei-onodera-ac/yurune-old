package app.yurune.filter;

import app.yurune.AppComponents;
import app.yurune.constant.HttpStatusConstants;
import app.yurune.constant.LogConstants;
import app.yurune.constant.ObservabilityConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.SessionUsers;
import app.yurune.service.AgentDecisionContext;
import app.yurune.service.AuditLogger;
import app.yurune.service.TraceContext;
import app.yurune.service.WebRequestPolicy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * リクエストごとに、トレース ID を決め、処理の間、記録（入力の検査、LLM、ツール、出力の検査）が同じ ID を持つようにする。
 * 終わったら、経路と状態と時間だけの 1 行を残す（リクエストの内容、パラメーター、本文は、残さない）。
 * 静的なファイルと、稼働・準備の確認は、通常は記録しない（A5。5xx のときと、遅いときは記録する）。
 */
public final class TraceFilter implements Filter {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private AuditLogger auditLogger;
    private WebRequestPolicy requestPolicy;

    @Override
    public void init(FilterConfig filterConfig) {
        AppComponents components = (AppComponents) filterConfig.getServletContext()
                .getAttribute(ViewConstants.ATTR_COMPONENTS);
        this.auditLogger = components.getAuditLogger();
        this.requestPolicy = components.getWebRequestPolicy();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        boolean quiet = requestPolicy.isQuietPath(httpRequest.getServletPath());
        String traceId = TraceContext.resolve(httpRequest.getHeader(LogConstants.TRACE_HEADER));
        TraceContext.begin(traceId);
        httpRequest.setAttribute(LogConstants.ATTR_TRACE_ID, traceId);
        httpResponse.setHeader(LogConstants.RESPONSE_TRACE_HEADER, traceId);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long latencyMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLI;
            if (shouldLog(quiet, httpResponse.getStatus(), latencyMillis)) {
                auditLogger.request(httpRequest.getMethod(), httpRequest.getServletPath(), httpResponse.getStatus(),
                        latencyMillis,
                        SessionUsers.find(httpRequest).map(user -> AuditLogger.hashUid(user.getUid())).orElse(null));
            }
            AgentDecisionContext.clear();
            TraceContext.end();
        }
    }

    /** 静かな経路は、5xx のときと、遅いときだけ記録する（雑音と費用を避ける。A5）。 */
    private boolean shouldLog(boolean quiet, int status, long latencyMillis) {
        return !quiet || status >= HttpStatusConstants.SERVER_ERROR || latencyMillis >= ObservabilityConstants.SLOW_REQUEST_MS;
    }
}
