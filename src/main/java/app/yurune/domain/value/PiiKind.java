package app.yurune.domain.value;

/**
 * 伏せ字にした個人情報・認証情報の種類。氏名など、形で見つけにくいものは、ここでは扱わない（別の層で扱う）。
 */
public enum PiiKind {
    EMAIL("email"),
    URL("url"),
    SECRET_KEY("secret_key"),
    POSTAL_CODE("postal_code"),
    CARD_NUMBER("card_number"),
    PHONE("phone"),
    ID_NUMBER("id_number"),
    ADDRESS("address");

    private final String id;

    PiiKind(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
