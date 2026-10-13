package app.yurune.service;

import app.yurune.constant.AuthConstants;
import app.yurune.domain.value.AuthenticatedUser;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 登録とログインの判断。入力の検査、試行の制限、メール確認の要求を、認証の仕組みに渡す前後で行う。
 * 失敗の理由は、画面に出す側が、メールアドレスの登録の有無を推測されない文にまとめる。
 */
public final class AuthService {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final String GLOBAL_KEY = "*";

    /** 登録の結果。確認のメールが必要なときは、ログインさせない。 */
    public static final class RegistrationResult {
        private final AuthenticatedUser user;
        private final boolean verificationRequired;

        RegistrationResult(AuthenticatedUser user, boolean verificationRequired) {
            this.user = user;
            this.verificationRequired = verificationRequired;
        }

        public AuthenticatedUser getUser() {
            return user;
        }

        public boolean isVerificationRequired() {
            return verificationRequired;
        }
    }

    private final AuthGateway gateway;
    private final AttemptLimiter emailLimiter;
    private final AttemptLimiter globalLimiter;
    private final AttemptLimiter registrationLimiter;
    private final String demoEmail;

    /** @param demoEmail 審査用アカウントのメールアドレス（なければ null） */
    public AuthService(AuthGateway gateway, AttemptLimiter emailLimiter, AttemptLimiter globalLimiter,
            AttemptLimiter registrationLimiter, String demoEmail) {
        this.gateway = gateway;
        this.emailLimiter = emailLimiter;
        this.globalLimiter = globalLimiter;
        this.registrationLimiter = registrationLimiter;
        this.demoEmail = demoEmail == null ? null : normalizeEmail(demoEmail);
    }

    public AuthenticatedUser login(String rawEmail, String password) {
        String email = requireValidEmail(rawEmail);
        requireUsablePassword(password);
        guardAgainstGuessing(email);
        AuthAccount account = signInRecordingFailures(email, password);
        if (!account.isEmailVerified()) {
            throw new AuthException(AuthException.Reason.EMAIL_NOT_VERIFIED);
        }
        return toUser(account);
    }

    public RegistrationResult register(String rawEmail, String password, boolean ageConfirmed, boolean termsAgreed) {
        String email = requireValidEmail(rawEmail);
        if (!ageConfirmed || !termsAgreed) {
            throw new AuthException(AuthException.Reason.CONSENT_MISSING);
        }
        requireUsablePassword(password);
        if (password.length() < AuthConstants.PASSWORD_MIN_CHARS) {
            throw new AuthException(AuthException.Reason.WEAK_PASSWORD);
        }
        if (registrationLimiter.isBlocked(GLOBAL_KEY)) {
            throw new AuthException(AuthException.Reason.TOO_MANY_ATTEMPTS);
        }
        registrationLimiter.record(GLOBAL_KEY);
        AuthAccount account = gateway.register(email, password);
        return new RegistrationResult(toUser(account), !account.isEmailVerified());
    }

    /** データの消去のとき、パスワードをもう一度確かめる（誤操作と、乗っ取りへの備え）。 */
    public void confirmPassword(String rawEmail, String password) {
        String email = requireValidEmail(rawEmail);
        requireUsablePassword(password);
        guardAgainstGuessing(email);
        signInRecordingFailures(email, password);
    }

    private AuthAccount signInRecordingFailures(String email, String password) {
        try {
            AuthAccount account = gateway.signIn(email, password);
            emailLimiter.reset(email);
            return account;
        } catch (AuthException exception) {
            if (exception.getReason() == AuthException.Reason.INVALID_CREDENTIALS) {
                emailLimiter.record(email);
                globalLimiter.record(GLOBAL_KEY);
            }
            throw exception;
        }
    }

    private void guardAgainstGuessing(String email) {
        if (emailLimiter.isBlocked(email) || globalLimiter.isBlocked(GLOBAL_KEY)) {
            throw new AuthException(AuthException.Reason.TOO_MANY_ATTEMPTS);
        }
    }

    private AuthenticatedUser toUser(AuthAccount account) {
        boolean demo = demoEmail != null && demoEmail.equals(normalizeEmail(account.getEmail()));
        return new AuthenticatedUser(account.getUid(), account.getEmail(), demo);
    }

    private String requireValidEmail(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (email.isEmpty() || email.length() > AuthConstants.EMAIL_MAX_CHARS || !EMAIL.matcher(email).matches()) {
            throw new AuthException(AuthException.Reason.INVALID_INPUT);
        }
        return email;
    }

    private void requireUsablePassword(String password) {
        if (password == null || password.isEmpty() || password.length() > AuthConstants.PASSWORD_MAX_CHARS) {
            throw new AuthException(AuthException.Reason.INVALID_INPUT);
        }
    }

    private String normalizeEmail(String rawEmail) {
        return TextNormalizer.normalize(rawEmail).toLowerCase(Locale.ROOT);
    }
}
