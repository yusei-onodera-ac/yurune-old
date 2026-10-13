package app.yurune.domain.value;

/**
 * 外部呼び出しの区間の名前（運用の記録 span ログ用）。
 */
public enum SpanName {

    GEMINI_GENERATE("gemini.generate"),
    ARMOR_SANITIZE("armor.sanitize"),
    TTS_SYNTHESIZE("tts.synthesize"),
    FIRESTORE_REQUEST("firestore.request");

    private final String id;

    SpanName(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
