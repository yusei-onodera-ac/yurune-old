package app.yurune.service;

import app.yurune.constant.SecurityConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * CSRF トークンを作り、送られてきた値と照合する。
 * 照合は、値の違いから一致した桁数を推測されないよう、一定の時間で行う。
 */
public final class CsrfTokenService {

    private final SecureRandom random = new SecureRandom();

    public String issueToken() {
        byte[] bytes = new byte[SecurityConstants.CSRF_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean matches(String expected, String provided) {
        if (expected == null || expected.isEmpty() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
