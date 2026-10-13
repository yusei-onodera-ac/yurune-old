package app.yurune.constant;

import app.yurune.domain.value.Dimension;

import java.util.List;

/**
 * 設定の下に開く情報の画面（覚えたこと、音源のクレジット、ご利用にあたって）の経路と、画面へ渡す値の名前。
 */
public final class InfoConstants {

    public static final String PATH_MEMORY = "/settings/memory";
    public static final String PATH_CREDITS = "/settings/credits";
    public static final String PATH_GUIDE = "/settings/guide";

    public static final String VIEW_MEMORY = "/WEB-INF/views/memory.jsp";
    public static final String VIEW_CREDITS = "/WEB-INF/views/credits.jsp";
    public static final String VIEW_GUIDE = "/WEB-INF/views/guide.jsp";

    public static final String ATTR_MEMORY_GROUPS = "memoryGroups";
    public static final String ATTR_MEMORY_MIN_TRIALS = "memoryMinTrials";
    public static final String ATTR_SOUND_CREDITS = "soundCredits";

    /** 「覚えたこと」に出す、学習の対象の次元。夜の記録が、試した選択肢として数えるものと同じ。 */
    public static final List<Dimension> MEMORY_DIMENSIONS = List.of(Dimension.SOUND, Dimension.TALK, Dimension.FADE);

    /** 音源の取得日の、画面での書き方。 */
    public static final String CREDIT_DATE_PATTERN = "yyyy年M月d日";

    private InfoConstants() {
    }
}
