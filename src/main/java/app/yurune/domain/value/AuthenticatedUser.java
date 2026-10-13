package app.yurune.domain.value;

import java.io.Serializable;
import java.util.Objects;

/**
 * ログインした利用者。uid が、データの置き場所（users/{uid}）を決める。
 * セッションに入れるので、Serializable にしてある。
 */
public final class AuthenticatedUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String uid;
    private final String email;
    private final boolean demo;

    public AuthenticatedUser(String uid, String email, boolean demo) {
        this.uid = Objects.requireNonNull(uid);
        this.email = Objects.requireNonNull(email);
        this.demo = demo;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    /** 審査員用のデモアカウント。14 日分の記録と、朝のひとこと待ちの 1 晩が入っている（画面に「デモ」と明記する）。 */
    public boolean isDemo() {
        return demo;
    }
}
