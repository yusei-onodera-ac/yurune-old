package app.yurune.service;

/**
 * 入出力の検査サービス（Model Armor）の窓口。自前の検査層（InputGuard、OutputGuard）に重ねる、もう 1 つの層。
 * 設定がないときは、使わない（自前の層だけで動く）。使えないときは、ArmorUnavailableException。
 */
public interface ArmorGateway {

    /** 設定されていて、使う状態か。 */
    boolean isEnabled();

    /** 利用者の発言を検査する。 */
    ArmorVerdict screenPrompt(String text);

    /** LLM の返事を検査する。 */
    ArmorVerdict screenResponse(String text);
}
