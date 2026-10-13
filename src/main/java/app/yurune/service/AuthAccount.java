package app.yurune.service;

/**
 * 認証の仕組み（Firebase Authentication など）が返す、アカウントの情報。
 */
public final class AuthAccount {

    private final String uid;
    private final String email;
    private final boolean emailVerified;

    public AuthAccount(String uid, String email, boolean emailVerified) {
        this.uid = uid;
        this.email = email;
        this.emailVerified = emailVerified;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }
}
