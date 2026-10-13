package app.yurune.service;

import app.yurune.config.AppConfig;
import app.yurune.constant.NightConstants;
import app.yurune.constant.TimeConstants;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.ToolName;

import java.util.List;
import java.util.Map;

/**
 * 規則で判断する夜のエージェント。LLM が使えないとき、または使う前の段階の代わり。
 * 判断のたびに、人が読める理由を 1 行つける。
 */
public final class RuleBasedNightAgent implements NightAgent {

    private final AppConfig config;

    public RuleBasedNightAgent(AppConfig config) {
        this.config = config;
    }

    /** 無反応が基準を超えたときの終了。LLM を使うときも、この判断はコードが行う。基準内なら空。 */
    public List<ToolCall> decideEnd(Observation observation) {
        if (observation.getSilenceSec() >= config.getNoReactionMin() * TimeConstants.SECONDS_PER_MINUTE) {
            return createQuietEndCalls();
        }
        return List.of();
    }

    @Override
    public List<ToolCall> decide(Observation observation, NightSession session) {
        List<ToolCall> endCalls = decideEnd(observation);
        if (!endCalls.isEmpty()) {
            return endCalls;
        }
        if (session.getMode() == NightMode.TALK
                && observation.getResponseGapSec() >= NightConstants.TALK_GAP_LIMIT_SEC) {
            return List.of(new ToolCall(ToolName.SWITCH_MODE.asId(), Map.of("mode", NightMode.SOUND.asId()),
                    new DisplayText("応答の間隔が長くなったため、反応を求めない状態にします。")));
        }
        return List.of();
    }

    /**
     * 夜のあいだ、経過時間に応じて、音量を少しずつ下げる規則（PDCA の FADE 要因）。
     * 開始音量（NightConstants.START_VOLUME_PCT）から、下限（NightConstants.FADE_FLOOR_VOLUME_PCT）まで、
     * 計画の FADE の分数（session.getFadeMinutes()）をかけて、直線に下げる。1 回の呼び出しでの下げ幅は、
     * 最大 NightConstants.MAX_VOLUME_STEP_DOWN_PCT%（急に下げて起こさないため。ToolGuard は下げる方向を
     * 制限しないので、ここで自分に課す）。目標に既に届いているとき、または下限より下げる必要がないときは、空。
     * 「おやすみ」の終わりのフェード（無反応のあとの FADE_OUT ツール）とは別で、こちらは変えない。
     */
    public List<ToolCall> decideFade(Observation observation, NightSession session) {
        int targetPct = targetVolumePctOf(observation.getElapsedMin(), session.getFadeMinutes());
        int currentPct = session.getVolume().asInt();
        if (targetPct >= currentPct) {
            return List.of();
        }
        int nextPct = Math.max(targetPct, currentPct - NightConstants.MAX_VOLUME_STEP_DOWN_PCT);
        if (nextPct == currentPct) {
            return List.of();
        }
        return List.of(new ToolCall(ToolName.SET_VOLUME.asId(),
                Map.of("levelPct", String.valueOf(nextPct), "rampSec", String.valueOf(NightConstants.MIN_RAMP_UP_SEC)),
                new DisplayText("眠りやすいよう、音量を少しずつ下げています。")));
    }

    /** 開始音量から下限まで、fadeMinutes をかけて直線に下げた、いまの経過時間での目標音量（%）。 */
    private static int targetVolumePctOf(int elapsedMin, int fadeMinutes) {
        if (elapsedMin >= fadeMinutes) {
            return NightConstants.FADE_FLOOR_VOLUME_PCT;
        }
        int range = NightConstants.START_VOLUME_PCT - NightConstants.FADE_FLOOR_VOLUME_PCT;
        int dropped = range * elapsedMin / fadeMinutes;
        return NightConstants.START_VOLUME_PCT - dropped;
    }

    /** 無反応が続いたとき: 声や画面には出さず、音を下げて、静かに終了する（この順）。 */
    private List<ToolCall> createQuietEndCalls() {
        String minutes = config.getNoReactionMin() + "分間、反応がなかったため、静かに終わります。";
        return List.of(
                new ToolCall(ToolName.FADE_OUT.asId(),
                        Map.of("minutes", String.valueOf(NightConstants.GOODNIGHT_FADE_MINUTES)),
                        new DisplayText("画面と音で起こさないよう、ゆっくり下げます。")),
                new ToolCall(ToolName.END_SESSION.asId(), Map.of("reason", EndReason.NO_REACTION.asId()),
                        new DisplayText(minutes)));
    }
}
