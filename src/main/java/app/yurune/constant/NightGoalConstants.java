package app.yurune.constant;

import app.yurune.domain.value.NightGoal;

/**
 * 設定の「夜のめやす」の文言（行の名前、選択肢の名前と説明、説明文）と、ふりかえりで数えたものの言い方。
 * 画面に出す語は、やわらかい言葉にする（「目標」「基準」「スコア」は使わない）。
 * 文言の検査（GentleLanguageLintTest）の対象。
 */
public final class NightGoalConstants {

    public static final String TITLE = "夜のめやす";
    public static final String FOOT = "夜のめやすを、寝つき、夜中、朝のようすから選べます。ふりかえりの「いまのところ」の見方が変わります。";
    public static final String HINT_DEFAULT = "初期値";

    private NightGoalConstants() {
    }

    public static String labelOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> "寝つき";
            case WAKE -> "夜中に目が覚める";
            case MORNING -> "朝のようす";
        };
    }

    public static String hintOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> "夜を始めて、反応がなくなるまでの時間で見ます。";
            case WAKE -> "夜のなかで、また始めた回数で見ます。";
            case MORNING -> "朝のひとことの、ようすで見ます。";
        };
    }

    /** 標準の夜より、めやすが短め（小さめ）の夜が多かったときの言い方（「標準より」の後ろに続く）。 */
    public static String betterPhraseOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> "寝つきが短めの夜が多かったよ";
            case WAKE -> "夜中の目覚めが少なめの夜が多かったよ";
            case MORNING -> "朝のようすが穏やかな夜が多かったよ";
        };
    }

    /** 標準の夜より、めやすが長め（重め）の夜が多かったときの言い方（「標準より」の後ろに続く）。 */
    public static String worsePhraseOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> "寝つきが長めの夜が多かったよ";
            case WAKE -> "夜中の目覚めが多めの夜が多かったよ";
            case MORNING -> "朝のようすが重めの夜が多かったよ";
        };
    }

    /** ふりかえりの件数（標準の夜と比べて、めやすがよかった夜の数）の言い方。 */
    public static String countPhraseOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> "標準より寝つきが短かった夜";
            case WAKE -> "標準より夜中の目覚めが少なかった夜";
            case MORNING -> "標準より朝のようすが穏やかだった夜";
        };
    }
}
