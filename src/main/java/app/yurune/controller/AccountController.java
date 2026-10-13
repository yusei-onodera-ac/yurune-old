package app.yurune.controller;

import app.yurune.constant.AuthConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.service.AuditLogger;
import app.yurune.service.AuthException;
import app.yurune.service.AuthService;
import app.yurune.service.ConsentService;
import app.yurune.service.CsrfTokenService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 登録、ログイン、ログアウト、規約の画面の入出力を受け持つ。ログインしていなくても使える画面。
 * 失敗の文は、メールアドレスが登録済みかを推測できないよう、まとめて同じ書き方にする。
 */
public final class AccountController {

    private static final String CHECKED = "on";

    private static final String MESSAGE_LOGIN_FAILED = "メールアドレスまたはパスワードが違います。";
    private static final String MESSAGE_NOT_VERIFIED =
            "メールアドレスの確認が、まだ済んでいません。届いたメールの案内から確認してください。";
    private static final String MESSAGE_TOO_MANY = "試行が多すぎます。しばらくしてから、もう一度お試しください。";
    private static final String MESSAGE_UNAVAILABLE = "いまは、利用できません。時間をおいて、お試しください。";
    private static final String MESSAGE_INVALID_EMAIL = "メールアドレスの形式を確認してください。";
    private static final String MESSAGE_WEAK_PASSWORD = "パスワードは、" + AuthConstants.PASSWORD_MIN_CHARS + "文字以上にしてください。";
    private static final String MESSAGE_CONSENT = "18歳以上であることと、規約・プライバシーへの同意を、確認してください。";
    private static final String MESSAGE_REGISTER_FAILED = "登録できませんでした。入力を確認するか、ログインをお試しください。";
    private static final String MESSAGE_VERIFICATION_SENT =
            "確認のメールを送りました。メールの案内から確認したあとで、ログインしてください。";

    private final AuthService authService;
    private final ConsentService consentService;
    private final CsrfTokenService csrfTokenService;
    private final AuditLogger auditLogger;

    public AccountController(AuthService authService, ConsentService consentService, CsrfTokenService csrfTokenService,
            AuditLogger auditLogger) {
        this.authService = authService;
        this.consentService = consentService;
        this.csrfTokenService = csrfTokenService;
        this.auditLogger = auditLogger;
    }

    public Navigation showLogin(HttpServletRequest request) {
        return Navigation.forward(AuthConstants.VIEW_LOGIN);
    }

    public Navigation showRegister(HttpServletRequest request) {
        request.setAttribute(AuthConstants.ATTR_TERMS_VERSION, AuthConstants.TERMS_VERSION);
        return Navigation.forward(AuthConstants.VIEW_REGISTER);
    }

    public Navigation showTerms(HttpServletRequest request) {
        request.setAttribute(AuthConstants.ATTR_TERMS_VERSION, AuthConstants.TERMS_VERSION);
        return Navigation.forward(AuthConstants.VIEW_TERMS);
    }

    public Navigation showPrivacy(HttpServletRequest request) {
        request.setAttribute(AuthConstants.ATTR_TERMS_VERSION, AuthConstants.TERMS_VERSION);
        return Navigation.forward(AuthConstants.VIEW_PRIVACY);
    }

    public Navigation login(HttpServletRequest request) {
        String email = request.getParameter(AuthConstants.PARAM_EMAIL);
        try {
            AuthenticatedUser user = authService.login(email, request.getParameter(AuthConstants.PARAM_PASSWORD));
            SessionUsers.signIn(request, user, csrfTokenService);
            auditLogger.auth(AuditLogger.AuthAction.LOGIN, null);
            return Navigation.redirect(ViewConstants.PATH_HOME);
        } catch (AuthException exception) {
            auditLogger.auth(AuditLogger.AuthAction.LOGIN, exception.getReason());
            request.setAttribute(AuthConstants.ATTR_EMAIL, email);
            request.setAttribute(AuthConstants.ATTR_MESSAGE, describeLoginFailure(exception.getReason()));
            return Navigation.forward(AuthConstants.VIEW_LOGIN);
        }
    }

    public Navigation register(HttpServletRequest request) {
        String email = request.getParameter(AuthConstants.PARAM_EMAIL);
        try {
            AuthService.RegistrationResult result = authService.register(email,
                    request.getParameter(AuthConstants.PARAM_PASSWORD),
                    CHECKED.equals(request.getParameter(AuthConstants.PARAM_AGE)),
                    CHECKED.equals(request.getParameter(AuthConstants.PARAM_TERMS)));
            consentService.recordConsent(result.getUser().getUid());
            auditLogger.auth(AuditLogger.AuthAction.REGISTER, null);
            if (result.isVerificationRequired()) {
                request.setAttribute(AuthConstants.ATTR_MESSAGE, MESSAGE_VERIFICATION_SENT);
                return Navigation.forward(AuthConstants.VIEW_LOGIN);
            }
            SessionUsers.signIn(request, result.getUser(), csrfTokenService);
            return Navigation.redirect(ViewConstants.PATH_HOME);
        } catch (AuthException exception) {
            auditLogger.auth(AuditLogger.AuthAction.REGISTER, exception.getReason());
            request.setAttribute(AuthConstants.ATTR_EMAIL, email);
            request.setAttribute(AuthConstants.ATTR_MESSAGE, describeRegistrationFailure(exception.getReason()));
            request.setAttribute(AuthConstants.ATTR_TERMS_VERSION, AuthConstants.TERMS_VERSION);
            return Navigation.forward(AuthConstants.VIEW_REGISTER);
        }
    }

    public Navigation logout(HttpServletRequest request) {
        auditLogger.auth(AuditLogger.AuthAction.LOGOUT, null);
        SessionUsers.signOut(request);
        return Navigation.redirect(AuthConstants.PATH_LOGIN);
    }

    private String describeLoginFailure(AuthException.Reason reason) {
        return switch (reason) {
            case EMAIL_NOT_VERIFIED -> MESSAGE_NOT_VERIFIED;
            case TOO_MANY_ATTEMPTS -> MESSAGE_TOO_MANY;
            case UNAVAILABLE -> MESSAGE_UNAVAILABLE;
            default -> MESSAGE_LOGIN_FAILED;
        };
    }

    private String describeRegistrationFailure(AuthException.Reason reason) {
        return switch (reason) {
            case INVALID_INPUT -> MESSAGE_INVALID_EMAIL;
            case WEAK_PASSWORD -> MESSAGE_WEAK_PASSWORD;
            case CONSENT_MISSING -> MESSAGE_CONSENT;
            case TOO_MANY_ATTEMPTS -> MESSAGE_TOO_MANY;
            case UNAVAILABLE -> MESSAGE_UNAVAILABLE;
            default -> MESSAGE_REGISTER_FAILED;
        };
    }
}
