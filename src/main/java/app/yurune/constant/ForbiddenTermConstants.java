package app.yurune.constant;

import app.yurune.domain.value.ViolationKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 禁止領域の検査データ（仕様書 2 節 forbidden_domains の最初の種。網羅ではない）。
 * 疾病の断定、重症度、受診の要否、投薬の助言、効果の断定、評価、根拠のない知識の主張、役割の逸脱を、
 * 出さないため（出力）と、受け止めないため（入力）に照合する。
 * コードから分けて管理するのは、研究を続けて拡張するため。語はすべて、NFKC で正規化した小文字の文に照合する。
 */
public final class ForbiddenTermConstants {

    /** 出力に出してはいけない語。種類ごとに分けて、記録の種類に使う。 */
    public static final Map<ViolationKind, List<String>> OUTPUT_TERMS = createOutputTerms();

    /** 出力に出してはいけない言い回し（正規表現）。疾病の可能性を匂わせる、ぼかした断定。 */
    public static final List<String> MEDICAL_OUTPUT_PATTERNS = List.of(
            "可能性(が|は)(あります|ある|高い)", "疑(い|わ)(が|れ)", "の(おそれ|恐れ)があり");

    /**
     * 入力にあったら、LLM に渡さず、定型文 T-MED を返す語。利用者が自分から言った語にも、診断や評価は返さない。
     * 「飲んで」のような、日常の語は入れない（夜の会話が、頻繁に切れないように）。
     */
    public static final List<String> MEDICAL_INPUT_TERMS = List.of(
            "うつ", "鬱", "不眠", "睡眠障害", "睡眠時無呼吸", "無呼吸", "ナルコレプシー", "レム睡眠行動障害", "むずむず脚",
            "病気", "病院", "診断", "診察", "受診", "通院", "精神科", "心療内科", "医者", "医師",
            "薬", "睡眠薬", "サプリ", "メラトニン", "処方", "服用", "減薬", "増量", "副作用",
            "治る", "治り", "治って", "治せ", "治療", "症状", "障害", "パニック",
            "効果", "効く", "効き", "眠れるように",
            "死にたい", "消えたい", "自殺", "自傷");

    /**
     * 事実シートにない、過去や継続の主張につながる語。LLM は、過去の夜の中身を持たないので、事実のように言えない。
     * （例: 「昨日も雨の音でしたね」）
     */
    public static final List<String> HISTORY_CLAIM_TERMS = List.of(
            "昨日", "昨夜", "ゆうべ", "先週", "前回", "前の晩", "この前", "先日", "以前", "いつもの", "毎晩", "毎日", "何日", "何回");

    /** 指示の無視、システム指示の聞き出し、役割のすり替えなどの言い回し（正規表現。NFKC、小文字の文に照合する）。 */
    public static final List<String> INJECTION_PATTERNS = List.of(
            "(以前|これまで|上記|先ほど|さっき|今まで|前)の?(指示|命令|ルール|制約|設定|プロンプト).{0,8}(無視|忘れ|破棄|取り消|リセット)",
            "(指示|命令|ルール|制約|設定).{0,4}(無視|忘れて|守らなくて|従わなくて)",
            "(システム|初期|隠された?|内部)(の)?(プロンプト|指示|メッセージ|設定)",
            "(プロンプト|指示|設定|ルール|命令).{0,8}(教えて|見せて|表示|出力|開示|公開|繰り返|コピー)",
            "あなた(は|が)(今から|これから|もう|今日から).{0,24}(です|になって|として|になりきって)",
            "(になりきって|のふりをして|として振る舞って|ロールプレイ|なりすまし)",
            "(開発者|デベロッパー|管理者|運営者)(モード|権限|です|として)",
            "(ignore|disregard|forget|override).{0,24}(previous|prior|above|earlier|all|your).{0,24}(instruction|prompt|rule|direction)",
            "(system|hidden|initial)\\s*(prompt|instruction|message)",
            "(reveal|show|print|repeat|leak).{0,24}(prompt|instruction|rule)",
            "you\\s+are\\s+(now|no longer)", "jailbreak", "\\bdan\\b", "developer\\s*mode", "act\\s+as\\b", "pretend\\s+(to|you)");

    private static Map<ViolationKind, List<String>> createOutputTerms() {
        Map<ViolationKind, List<String>> terms = new EnumMap<>(ViolationKind.class);
        terms.put(ViolationKind.MEDICAL_TOPIC, List.of(
                // disease_name
                "うつ病", "不眠症", "睡眠時無呼吸", "ナルコレプシー", "レム睡眠行動障害", "睡眠障害", "自律神経",
                // severity
                "重症", "軽症", "危険な状態", "リスクが高い",
                // triage
                "受診したほうがいい", "受診が必要", "病院に行くべき", "放っておいていい",
                // medication
                "睡眠薬", "メラトニン", "飲んで", "服用", "減薬", "増量", "処方"));
        terms.put(ViolationKind.EFFICACY_CLAIM, List.of(
                "治る", "治ります", "改善します", "眠れるようになる", "効果があります", "これで大丈夫",
                "効きます", "効果的", "よく眠れ", "ぐっすり眠れ", "熟睡できま"));
        // 無反応は入眠を意味しない（C-14）。実際の Gemini の出力で見つけた表現から追加した
        terms.put(ViolationKind.SLEEP_STATE_CLAIM, List.of(
                "眠っている", "寝ている", "眠りを誘", "入眠", "寝息", "寝落ち", "眠りについ", "寝付い", "寝てる",
                "眠っていた", "眠ってた", "寝ていた", "寝てた", "眠ってい", "眠った"));
        terms.put(ViolationKind.EVALUATION_OR_CAUSALITY, List.of(
                "頑張りました", "頑張ったね", "よく頑張", "さぼ", "のおかげ", "おかげです", "良くなりました", "悪くなりました",
                "成功しました", "えらいね"));
        terms.put(ViolationKind.EVIDENCE_CLAIM, List.of(
                "研究で", "研究によ", "証明", "科学的", "医学的", "エビデンス", "統計的", "実証", "論文", "専門家によ"));
        terms.put(ViolationKind.ROLE_BREAK, List.of(
                "システムプロンプト", "システム指示", "プロンプト", "言語モデル", "gemini", "ジェミニ", "google", "グーグル",
                "chatgpt", "openai", "開発者モード", "人工知能"));
        return terms;
    }

    private ForbiddenTermConstants() {
    }
}
