package app.yurune.service;

/**
 * 認証の仕組み（メールとパスワード）の窓口。Service は、Firebase Authentication の通信を知らない。
 * 失敗は、{@link AuthException} で返す。
 */
public interface AuthGateway {

    /** アカウントを作る。メール確認が必要な仕組みでは、確認のメールも送る。 */
    AuthAccount register(String email, String password);

    /** メールとパスワードを確かめる。メール確認が済んでいるかも、一緒に返す。 */
    AuthAccount signIn(String email, String password);
}
