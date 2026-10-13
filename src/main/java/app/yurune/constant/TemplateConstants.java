package app.yurune.constant;

/**
 * 定型文（仕様書 8 節）。医療に近い文面は、利用者が確認した文面だけを使う。
 * [PROPOSED] の文面は仮の文言で、確認が取れるまで変わりうる。
 */
public final class TemplateConstants {

    /** T-MED: 入力に医療領域の語が入ったときの、ぽてねの返答。 */
    public static final String MEDICAL_REPLY =
            "つらさが続くときは、医師に相談してください。ここでは、病気や薬のことには答えられません。";

    /** T-FALLBACK: 出力検査に違反した出力の差し替え。 */
    public static final String FALLBACK_REPLY =
            "うまくお答えできませんでした。つらさが続くときは、医師に相談してください。";

    /** T-DOWN: LLM・音声が使えないときの返答。 */
    public static final String DOWN_REPLY = "いまはお話しできません。";

    private TemplateConstants() {
    }
}
