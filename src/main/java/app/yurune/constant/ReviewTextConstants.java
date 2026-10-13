package app.yurune.constant;

import app.yurune.domain.value.NightGoal;

/**
 * 見直しの判断の記録に書く、ぽてねの口調の文（〜だよ、〜ね）。
 * 判断の記録は、既存の「昨夜の動き」にも出るので、禁止表現の検査（ProhibitedExpressionTest）の語を使わない。
 */
public final class ReviewTextConstants {

    /** 受診のすすめの、仕様の固定文（N-01 と T-MED の共通部分）。画面に出すのは後の段階。ここでは言い換えない。 */
    public static final String REFERRAL_SENTENCE = "つらさが続くときは、医師に相談してください。";

    /** 結論をまだ言い切らないときの文。 */
    public static final String HEDGE_SENTENCE = "まだ、はっきりとは言えないよ。";

    public static final String SIMILAR_SENTENCE = "標準の夜と、あまり差がなかったよ。";

    public static final String FEW_NIGHTS_SENTENCE = "夜数がそろうまで、結論は出さないね。";

    public static final String WORSE_STREAK_SENTENCE = "標準の夜より、わるい夜が続いたよ。";

    public static final String NOT_FIT_MAJORITY_SENTENCE = "答えのあった夜のうち、「合わなかった」が多かったよ。";

    /** 受診のすすめ（R6）の理由文。悪くなっていることそのものは言わない（ROLLBACK と役割を分けるため）。 */
    public static final String STAGNANT_SENTENCE = "最近のめやすは、標準の夜から、あまり変わっていないよ。";

    private ReviewTextConstants() {
    }

    /** 比べる項目の名前（判断の記録の見出しに使う）。 */
    public static String subjectOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> "寝つき";
            case WAKE -> "夜中の目覚め";
            case MORNING -> "朝のようす";
        };
    }

    /** よい方向に寄った夜が多いときの文。 */
    public static String betterSentenceOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> "寝つきが短めの夜が多かったよ。";
            case WAKE -> "夜中に目が覚める回数が少なめの夜が多かったよ。";
            case MORNING -> "朝のようすが穏やかな夜が多かったよ。";
        };
    }

    /** わるい方向に寄った夜が多いときの文。 */
    public static String worseSentenceOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> "寝つきが長めの夜が多かったよ。";
            case WAKE -> "夜中に目が覚める回数が多めの夜が多かったよ。";
            case MORNING -> "朝のようすが重めの夜が多かったよ。";
        };
    }
}
