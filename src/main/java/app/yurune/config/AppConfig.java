package app.yurune.config;

import app.yurune.constant.ConversationConstants;
import app.yurune.constant.LlmConstants;
import app.yurune.constant.NightConstants;

/**
 * 外に出す設定値（変更しやすくするため、コードから分ける）。
 * 環境変数で上書きできる。値がないか、数でないときは、初期値を使う。
 */
public final class AppConfig {

    private static final String ENV_NO_REACTION_MIN = "YURUNE_NO_REACTION_MIN";
    private static final String ENV_VOLUME_CAP_PCT = "YURUNE_VOLUME_CAP_PCT";
    private static final String ENV_OBSERVATION_INTERVAL_SEC = "YURUNE_OBSERVATION_INTERVAL_SEC";
    private static final String ENV_AGENT_ENABLED = "YURUNE_AGENT_ENABLED";
    private static final String ENV_LLM_MIN_INTERVAL_SEC = "YURUNE_LLM_MIN_INTERVAL_SEC";
    private static final String ENV_LLM_DAILY_CAP = "YURUNE_LLM_DAILY_CAP";
    private static final String ENV_TALK_MIN_INTERVAL_SEC = "YURUNE_TALK_MIN_INTERVAL_SEC";

    private final int noReactionMin;
    private final int volumeCapPct;
    private final int observationIntervalSec;
    private final boolean agentEnabled;
    private final int llmMinIntervalSec;
    private final int llmDailyCap;
    private final int talkMinIntervalSec;

    public AppConfig(int noReactionMin, int volumeCapPct, int observationIntervalSec, boolean agentEnabled,
            int llmMinIntervalSec, int llmDailyCap) {
        this(noReactionMin, volumeCapPct, observationIntervalSec, agentEnabled, llmMinIntervalSec, llmDailyCap,
                ConversationConstants.DEFAULT_TALK_MIN_INTERVAL_SEC);
    }

    public AppConfig(int noReactionMin, int volumeCapPct, int observationIntervalSec, boolean agentEnabled,
            int llmMinIntervalSec, int llmDailyCap, int talkMinIntervalSec) {
        if (noReactionMin <= 0 || observationIntervalSec <= 0 || llmMinIntervalSec < 0 || llmDailyCap < 0
                || talkMinIntervalSec < 0) {
            throw new IllegalArgumentException("minutes, seconds and caps must be valid");
        }
        if (volumeCapPct < 0 || volumeCapPct > NightConstants.PERCENT_MAX) {
            throw new IllegalArgumentException("volume cap must be 0..100");
        }
        this.noReactionMin = noReactionMin;
        this.volumeCapPct = volumeCapPct;
        this.observationIntervalSec = observationIntervalSec;
        this.agentEnabled = agentEnabled;
        this.llmMinIntervalSec = llmMinIntervalSec;
        this.llmDailyCap = llmDailyCap;
        this.talkMinIntervalSec = talkMinIntervalSec;
    }

    /** LLM の呼び出し間隔と日次の上限は、初期値にする。 */
    public AppConfig(int noReactionMin, int volumeCapPct, int observationIntervalSec, boolean agentEnabled) {
        this(noReactionMin, volumeCapPct, observationIntervalSec, agentEnabled,
                LlmConstants.DEFAULT_MIN_CALL_INTERVAL_SEC, LlmConstants.DEFAULT_DAILY_CALL_CAP);
    }

    /** エージェント（LLM）を使う設定で作る。 */
    public AppConfig(int noReactionMin, int volumeCapPct, int observationIntervalSec) {
        this(noReactionMin, volumeCapPct, observationIntervalSec, true);
    }

    public static AppConfig defaults() {
        return new AppConfig(NightConstants.DEFAULT_NO_REACTION_MIN, NightConstants.DEFAULT_VOLUME_CAP_PCT,
                NightConstants.DEFAULT_OBSERVATION_INTERVAL_SEC);
    }

    public static AppConfig fromEnvironment() {
        return new AppConfig(
                readInt(ENV_NO_REACTION_MIN, NightConstants.DEFAULT_NO_REACTION_MIN),
                readInt(ENV_VOLUME_CAP_PCT, NightConstants.DEFAULT_VOLUME_CAP_PCT),
                readInt(ENV_OBSERVATION_INTERVAL_SEC, NightConstants.DEFAULT_OBSERVATION_INTERVAL_SEC),
                !"false".equalsIgnoreCase(System.getenv(ENV_AGENT_ENABLED)),
                readInt(ENV_LLM_MIN_INTERVAL_SEC, LlmConstants.DEFAULT_MIN_CALL_INTERVAL_SEC),
                readInt(ENV_LLM_DAILY_CAP, LlmConstants.DEFAULT_DAILY_CALL_CAP),
                readInt(ENV_TALK_MIN_INTERVAL_SEC, ConversationConstants.DEFAULT_TALK_MIN_INTERVAL_SEC));
    }

    private static int readInt(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public int getNoReactionMin() {
        return noReactionMin;
    }

    public int getVolumeCapPct() {
        return volumeCapPct;
    }

    public int getObservationIntervalSec() {
        return observationIntervalSec;
    }

    /** false のとき、LLM を呼ばず、規則だけで動く（停止手段）。 */
    public boolean isAgentEnabled() {
        return agentEnabled;
    }

    public int getLlmMinIntervalSec() {
        return llmMinIntervalSec;
    }

    public int getLlmDailyCap() {
        return llmDailyCap;
    }

    /** 会話の LLM 呼び出しの、最小の間隔（秒）。夜の制御より短い。 */
    public int getTalkMinIntervalSec() {
        return talkMinIntervalSec;
    }
}
