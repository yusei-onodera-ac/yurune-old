package app.yurune.config;

import java.util.function.UnaryOperator;

/**
 * 動かす環境で決まる設定（Cookie、保存先、認証、外部サービスの接続先）。
 * 夜の調整値（{@link AppConfig}）とは分ける。値は環境変数から読み、鍵はコードにもリポジトリにも置かない。
 * 初期値は、メモリ上の保存先と認証（開発・テスト用）で、外部の検査と読み上げは使わない。
 * 本番は、環境変数で Firestore、Firebase、Model Armor、Text-to-Speech を指定する。
 */
public final class DeploymentConfig {

    private static final String ENV_COOKIE_SECURE = "YURUNE_COOKIE_SECURE";
    private static final String ENV_STORAGE = "YURUNE_STORAGE";
    private static final String ENV_AUTH = "YURUNE_AUTH";
    private static final String ENV_PROJECT_ID = "GOOGLE_CLOUD_PROJECT";
    private static final String ENV_FIREBASE_API_KEY = "FIREBASE_API_KEY";
    private static final String ENV_FIRESTORE_EMULATOR = "FIRESTORE_EMULATOR_HOST";
    private static final String ENV_AUTH_EMULATOR = "FIREBASE_AUTH_EMULATOR_HOST";
    private static final String ENV_ACCESS_TOKEN = "YURUNE_GCP_ACCESS_TOKEN";
    private static final String ENV_DEMO_EMAIL = "YURUNE_DEMO_EMAIL";
    private static final String ENV_DEMO_PASSWORD = "YURUNE_DEMO_PASSWORD";
    private static final String ENV_ARMOR_LOCATION = "YURUNE_ARMOR_LOCATION";
    private static final String ENV_ARMOR_TEMPLATE = "YURUNE_ARMOR_TEMPLATE";
    private static final String ENV_ARMOR_ENDPOINT = "YURUNE_ARMOR_ENDPOINT";
    private static final String ENV_TTS_ENABLED = "YURUNE_TTS_ENABLED";
    private static final String ENV_TTS_ENDPOINT = "YURUNE_TTS_ENDPOINT";
    private static final String MODE_FIRESTORE = "firestore";
    private static final String MODE_FIREBASE = "firebase";

    private final boolean cookieSecure;
    private final boolean firestoreStorage;
    private final boolean firebaseAuth;
    private final String projectId;
    private final String firebaseApiKey;
    private final String firestoreEmulatorHost;
    private final String authEmulatorHost;
    private final String accessToken;
    private final String demoEmail;
    private final String demoPassword;
    private final String armorLocation;
    private final String armorTemplate;
    private final String armorEndpoint;
    private final boolean ttsEnabled;
    private final String ttsEndpoint;

    /** 環境変数（の読み取り方）から作る。足りない設定があるときは、起動を止める（誤った設定のまま動かさない）。 */
    public DeploymentConfig(UnaryOperator<String> environment) {
        this.cookieSecure = !"false".equalsIgnoreCase(environment.apply(ENV_COOKIE_SECURE));
        this.firestoreStorage = MODE_FIRESTORE.equalsIgnoreCase(environment.apply(ENV_STORAGE));
        this.firebaseAuth = MODE_FIREBASE.equalsIgnoreCase(environment.apply(ENV_AUTH));
        this.projectId = environment.apply(ENV_PROJECT_ID);
        this.firebaseApiKey = environment.apply(ENV_FIREBASE_API_KEY);
        this.firestoreEmulatorHost = environment.apply(ENV_FIRESTORE_EMULATOR);
        this.authEmulatorHost = environment.apply(ENV_AUTH_EMULATOR);
        this.accessToken = environment.apply(ENV_ACCESS_TOKEN);
        this.demoEmail = environment.apply(ENV_DEMO_EMAIL);
        this.demoPassword = environment.apply(ENV_DEMO_PASSWORD);
        this.armorLocation = environment.apply(ENV_ARMOR_LOCATION);
        this.armorTemplate = environment.apply(ENV_ARMOR_TEMPLATE);
        this.armorEndpoint = environment.apply(ENV_ARMOR_ENDPOINT);
        this.ttsEnabled = "true".equalsIgnoreCase(environment.apply(ENV_TTS_ENABLED));
        this.ttsEndpoint = environment.apply(ENV_TTS_ENDPOINT);
        requireConsistent();
    }

    /** メモリ上の保存先と認証で動かす（開発・テスト用）。 */
    public DeploymentConfig(boolean cookieSecure) {
        this(name -> ENV_COOKIE_SECURE.equals(name) ? String.valueOf(cookieSecure) : null);
    }

    public static DeploymentConfig fromEnvironment(UnaryOperator<String> environment) {
        return new DeploymentConfig(environment);
    }

    private void requireConsistent() {
        if ((firestoreStorage || usesModelArmor() || ttsEnabled) && isBlank(projectId)) {
            throw new IllegalStateException("GOOGLE_CLOUD_PROJECT is required for Firestore, Model Armor and Text-to-Speech");
        }
        if (firebaseAuth && isBlank(firebaseApiKey)) {
            throw new IllegalStateException("FIREBASE_API_KEY is required when YURUNE_AUTH=firebase");
        }
        if (!isBlank(armorTemplate) && isBlank(armorLocation)) {
            throw new IllegalStateException("YURUNE_ARMOR_LOCATION is required when YURUNE_ARMOR_TEMPLATE is set");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** HTTP の開発環境でだけ、false にする。初期値は true（Cookie は HTTPS でしか送られない）。 */
    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public boolean usesFirestore() {
        return firestoreStorage;
    }

    public boolean usesFirebaseAuth() {
        return firebaseAuth;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getFirebaseApiKey() {
        return firebaseApiKey;
    }

    /** 設定されていれば、Firestore のエミュレータに接続する（ホスト:ポート）。 */
    public String getFirestoreEmulatorHost() {
        return firestoreEmulatorHost;
    }

    public String getAuthEmulatorHost() {
        return authEmulatorHost;
    }

    /** 手元の開発で使う、固定のアクセストークン。なければ、メタデータサーバーから得る。 */
    public String getAccessToken() {
        return accessToken;
    }

    /** 審査用アカウントのメールアドレス。なければ、審査用の扱いはない。 */
    public String getDemoEmail() {
        return demoEmail;
    }

    /** メモリ上の認証で、審査用アカウントを作るときのパスワード（Firebase では、アカウントを別に作る）。 */
    public String getDemoPassword() {
        return demoPassword;
    }

    /** 入出力の検査サービス（Model Armor）を、自前の検査に重ねて使うか。テンプレートの名前があるときだけ。 */
    public boolean usesModelArmor() {
        return !isBlank(armorTemplate);
    }

    public String getArmorLocation() {
        return armorLocation;
    }

    public String getArmorTemplate() {
        return armorTemplate;
    }

    /** 検査サービスの接続先を、地域の既定とは別にしたいとき（試験用）。なければ null。 */
    public String getArmorEndpoint() {
        return armorEndpoint;
    }

    /** サーバーでの読み上げ（Cloud Text-to-Speech）を使うか。使わないときは、端末（ブラウザ）の読み上げ。 */
    public boolean usesServerSpeech() {
        return ttsEnabled;
    }

    public String getTtsEndpoint() {
        return ttsEndpoint;
    }
}
