package app.yurune.gateway;

/**
 * 決まった値のトークンを返す。エミュレータ（認証を見ない）と、手元の開発（gcloud で得たトークンを環境変数に入れる）で使う。
 */
public final class StaticTokenProvider implements AccessTokenProvider {

    /** Firestore のエミュレータが、ルールを通さず全権限として扱う値。 */
    public static final String EMULATOR_OWNER = "owner";

    private final String token;

    public StaticTokenProvider(String token) {
        this.token = token;
    }

    @Override
    public String getToken() {
        return token;
    }
}
