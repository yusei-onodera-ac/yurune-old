package app.yurune.domain.value;

/**
 * ツール呼び出しを拒否した理由の種類（ToolGuard。運用の記録 agent ログ用）。
 * 自由な文でなく、固定の理由のコードだけを記録するために使う。
 */
public enum ToolRejectReason {

    /** 許可リストにないツール、または候補にない値（音源など）。 */
    NOT_IN_ALLOWLIST("not_in_allowlist"),
    /** 引数が欠けている、数でない、形が違う。 */
    INVALID_ARGUMENT("invalid_argument"),
    /** 値が決められた範囲の外。 */
    OUT_OF_RANGE("out_of_range"),
    /** 音量が、運用または利用者の設定の上限を超える。 */
    VOLUME_OVER_CAP("volume_over_cap"),
    /** 一度に上げられる幅、ランプの秒数が足りないなど、急な変化。 */
    TOO_FAST("too_fast"),
    /** いまの夜のモードでは許されない切り替え。 */
    MODE_NOT_ALLOWED("mode_not_allowed"),
    /** 会話のときでない、または利用者が黙っているときに、話しかけようとした。 */
    TALK_NOT_ALLOWED("talk_not_allowed"),
    /** 出力の検査（禁止領域、個人情報、指示の漏えいなど）に違反した文。 */
    FORBIDDEN_TEXT("forbidden_text"),
    /** 上のどれにも当たらない拒否（無くすのが理想の安全弁）。 */
    OTHER("other");

    private final String id;

    ToolRejectReason(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
