package app.yurune.service;

import app.yurune.config.AppConfig;
import app.yurune.constant.NightConstants;
import app.yurune.constant.PlanConstants;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.FactSheet;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.ToolName;
import app.yurune.domain.value.ToolRejectReason;

/**
 * エージェントのツール呼び出しを、コードで検証する固定の枠。
 * 許可リスト、引数の範囲、音量の上限、モードの制約は、LLM に変えさせない。
 * エージェントとは独立しており、拒否した呼び出しも、呼び出し側が記録する。
 */
public final class ToolGuard {

    private static final String ARG_LEVEL_PCT = "levelPct";
    private static final String ARG_RAMP_SEC = "rampSec";
    private static final String ARG_MINUTES = "minutes";
    private static final String ARG_SOUND_KEY = "soundKey";
    private static final String ARG_LEVEL = "level";
    private static final String ARG_MODE = "mode";
    private static final String ARG_TEXT = "text";
    private static final String ARG_REASON = "reason";

    private final AppConfig config;
    private final OutputGuard outputGuard;

    public ToolGuard(AppConfig config, OutputGuard outputGuard) {
        this.config = config;
        this.outputGuard = outputGuard;
    }

    /** 運用の音量の上限（環境変数）だけで検証する。 */
    public GuardResult validate(ToolCall call, NightSession session, Observation observation) {
        return validate(call, session, observation, config.getVolumeCapPct());
    }

    /**
     * 利用者の設定の音量の上限も含めて検証する。渡された上限が運用の上限より大きくても、運用の上限を超えては通さない。
     */
    public GuardResult validate(ToolCall call, NightSession session, Observation observation, int volumeCapPct) {
        int effectiveCapPct = Math.min(volumeCapPct, config.getVolumeCapPct());
        ToolName tool;
        try {
            tool = ToolName.fromId(call.getToolId());
        } catch (IllegalArgumentException exception) {
            return GuardResult.reject(ToolRejectReason.NOT_IN_ALLOWLIST, "許可リストにないツールです。");
        }
        try {
            return validateKnownTool(tool, call, session, observation, effectiveCapPct);
        } catch (IllegalArgumentException exception) {
            return GuardResult.reject(ToolRejectReason.INVALID_ARGUMENT, "引数が正しくありません。");
        }
    }

    private GuardResult validateKnownTool(ToolName tool, ToolCall call, NightSession session,
            Observation observation, int volumeCapPct) {
        return switch (tool) {
            case SET_VOLUME -> validateSetVolume(call, session, volumeCapPct);
            case FADE_OUT -> validateRange(requireInt(call, ARG_MINUTES),
                    NightConstants.FADE_OUT_MIN_MINUTES, NightConstants.FADE_OUT_MAX_MINUTES, "下げる時間",
                    ToolRejectReason.OUT_OF_RANGE);
            case SWITCH_TRACK -> validateSwitchTrack(call);
            case SET_TALK_LEVEL -> validateRange(requireInt(call, ARG_LEVEL), 0, NightConstants.TALK_LEVEL_MAX, "話す量",
                    ToolRejectReason.OUT_OF_RANGE);
            case SWITCH_MODE -> validateSwitchMode(call, session);
            case SAY -> validateSay(call, session, observation);
            case END_SESSION -> validateEndSession(call);
        };
    }

    /** 音量は上限以下。上げるときは、急に大きくして起こさないよう、上げ幅と、かける時間にも制限をかける。 */
    private GuardResult validateSetVolume(ToolCall call, NightSession session, int volumeCapPct) {
        int levelPct = requireInt(call, ARG_LEVEL_PCT);
        int rampSec = requireInt(call, ARG_RAMP_SEC);
        GuardResult level = validateRange(levelPct, 0, volumeCapPct, "音量", ToolRejectReason.VOLUME_OVER_CAP);
        if (!level.isAccepted()) {
            return level;
        }
        GuardResult ramp = validateRange(rampSec, 0, NightConstants.SET_VOLUME_RAMP_MAX_SEC, "変える時間",
                ToolRejectReason.OUT_OF_RANGE);
        if (!ramp.isAccepted()) {
            return ramp;
        }
        int increase = levelPct - session.getVolume().asInt();
        if (increase > NightConstants.MAX_VOLUME_STEP_UP_PCT) {
            return GuardResult.reject(ToolRejectReason.TOO_FAST,
                    "一度に上げられるのは、" + NightConstants.MAX_VOLUME_STEP_UP_PCT + "% までです。");
        }
        if (increase > 0 && rampSec < NightConstants.MIN_RAMP_UP_SEC) {
            return GuardResult.reject(ToolRejectReason.TOO_FAST,
                    "音量を上げるときは、" + NightConstants.MIN_RAMP_UP_SEC + "秒以上かけてください。");
        }
        return GuardResult.accept();
    }

    private GuardResult validateSwitchTrack(ToolCall call) {
        String soundKey = call.findArgument(ARG_SOUND_KEY);
        if (soundKey == null || !PlanConstants.SOUND_CANDIDATES.contains(soundKey)) {
            return GuardResult.reject(ToolRejectReason.NOT_IN_ALLOWLIST, "音源の候補にありません。");
        }
        return GuardResult.accept();
    }

    /** 会話から環境音だけへの切り替えだけを許す。環境音から会話へは、利用者だけが切り替えられる。 */
    private GuardResult validateSwitchMode(ToolCall call, NightSession session) {
        if (!NightMode.SOUND.asId().equals(call.findArgument(ARG_MODE))) {
            return GuardResult.reject(ToolRejectReason.MODE_NOT_ALLOWED, "切り替えられるのは、環境音だけへです。");
        }
        if (session.getMode() != NightMode.TALK) {
            return GuardResult.reject(ToolRejectReason.MODE_NOT_ALLOWED, "会話のときだけ切り替えられます。");
        }
        return GuardResult.accept();
    }

    private GuardResult validateSay(ToolCall call, NightSession session, Observation observation) {
        String text = call.findArgument(ARG_TEXT);
        if (session.getMode() != NightMode.TALK) {
            return GuardResult.reject(ToolRejectReason.TALK_NOT_ALLOWED, "話せるのは、会話のときだけです。");
        }
        if (observation.getSilenceSec() >= NightConstants.NO_INITIATIVE_SILENCE_SEC) {
            return GuardResult.reject(ToolRejectReason.TALK_NOT_ALLOWED, "利用者が黙っているときは、こちらから話しかけません。");
        }
        if (text == null || text.isBlank() || text.length() > NightConstants.SAY_MAX_CHARS) {
            return GuardResult.reject(ToolRejectReason.OUT_OF_RANGE, "文の長さが範囲外です。");
        }
        OutputVerdict verdict = outputGuard.inspect(text, FactSheet.empty());
        if (!verdict.isAccepted()) {
            return GuardResult.reject(ToolRejectReason.FORBIDDEN_TEXT,
                    "出力の検査に通りません（" + verdict.getViolationKind().orElseThrow().asId() + "）。");
        }
        return GuardResult.accept();
    }

    private GuardResult validateEndSession(ToolCall call) {
        EndReason.fromId(call.findArgument(ARG_REASON));
        return GuardResult.accept();
    }

    private GuardResult validateRange(int value, int min, int max, String label, ToolRejectReason code) {
        if (value < min || value > max) {
            return GuardResult.reject(code, label + "が範囲外です（" + min + "〜" + max + "）。");
        }
        return GuardResult.accept();
    }

    /** 引数が整数でないとき（欠けている、文字など）は、IllegalArgumentException にする。 */
    private int requireInt(ToolCall call, String name) {
        String value = call.findArgument(name);
        if (value == null) {
            throw new IllegalArgumentException("missing argument: " + name);
        }
        return Integer.parseInt(value);
    }
}
