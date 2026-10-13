package app.yurune.service;

/**
 * LLM への窓口。Service は、どの LLM をどう呼ぶかを知らず、この窓口だけを使う（差し替えと、テストのため）。
 */
public interface LlmGateway {

    /** リクエスト（JSON）を送り、応答（JSON）を返す。使えないときは LlmUnavailableException。 */
    String generate(String requestJson);

    /**
     * 運用の記録（llm ログ、A2）に残す、モデルの名前。既定は空文字（試験の擬似実装など、モデルを持たないもののため）。
     * 既定の実装にすることで、この窓口を関数式（ラムダ式）で実装している既存のテストを壊さない。
     */
    default String getModel() {
        return "";
    }
}
