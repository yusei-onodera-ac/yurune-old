package app.yurune.constant;

import java.util.List;

/**
 * 今夜の計画の候補と、前の計画がないときの初期値（仮置き）。
 * 候補は、音源の収集が済むまでの暫定。
 */
public final class PlanConstants {

    public static final List<String> SOUND_CANDIDATES = List.of("rain", "wave", "forest");

    public static final List<String> FADE_CANDIDATES = List.of("15", "30", "45");

    public static final String DEFAULT_SOUND = "rain";

    public static final String DEFAULT_DURATION = "45";

    public static final String DEFAULT_TALK = "none";

    public static final String DEFAULT_FADE = "30";

    private PlanConstants() {
    }
}
