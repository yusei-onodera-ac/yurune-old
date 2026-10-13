package app.yurune.domain.value;

import java.util.regex.Pattern;

/**
 * 運用の記録（llm ログ）に出す、LLM のモデルの名前。GEMINI_MODEL（環境変数）で変わるため、列挙型にはできない。
 * 形（英数字・ドット・アンダースコア・ハイフン、1〜64 文字）に合わないものは、固定の "unknown" にする。
 * 本文や個人情報を、うっかりログに渡せないようにするための、値の型（コンストラクタだけで決まる）。
 */
public final class ModelName {

    private static final Pattern VALID_FORM = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final String UNKNOWN = "unknown";

    private final String id;

    public ModelName(String raw) {
        this.id = raw != null && VALID_FORM.matcher(raw).matches() ? raw : UNKNOWN;
    }

    public String asId() {
        return id;
    }
}
