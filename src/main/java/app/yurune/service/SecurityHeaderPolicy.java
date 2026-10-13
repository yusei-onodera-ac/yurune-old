package app.yurune.service;

import app.yurune.constant.SecurityConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * すべての応答に付けるセキュリティヘッダーの一覧。何を付けるかだけを決め、付けるのは Filter が行う。
 */
public final class SecurityHeaderPolicy {

    /** @param secureRequest 元の通信が HTTPS のときだけ、HSTS を付ける（HTTP の開発環境で、ブラウザに覚えさせないため） */
    public Map<String, String> createHeaders(boolean secureRequest) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(SecurityConstants.HEADER_CSP, SecurityConstants.CSP_VALUE);
        headers.put(SecurityConstants.HEADER_CONTENT_TYPE_OPTIONS, SecurityConstants.CONTENT_TYPE_OPTIONS_VALUE);
        headers.put(SecurityConstants.HEADER_FRAME_OPTIONS, SecurityConstants.FRAME_OPTIONS_VALUE);
        headers.put(SecurityConstants.HEADER_REFERRER_POLICY, SecurityConstants.REFERRER_POLICY_VALUE);
        headers.put(SecurityConstants.HEADER_PERMISSIONS_POLICY, SecurityConstants.PERMISSIONS_POLICY_VALUE);
        headers.put(SecurityConstants.HEADER_OPENER_POLICY, SecurityConstants.OPENER_POLICY_VALUE);
        if (secureRequest) {
            headers.put(SecurityConstants.HEADER_HSTS, SecurityConstants.HSTS_VALUE);
        }
        return headers;
    }
}
