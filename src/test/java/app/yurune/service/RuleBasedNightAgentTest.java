package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.constant.NightConstants;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 規則のエージェントの FADE 要因（夜のあいだ、音量を少しずつ下げる）。
 */
class RuleBasedNightAgentTest {

    private static final WakeDate WAKE_DATE = WakeDate.parse("2026-10-07");
    private static final Instant STARTED_AT = Instant.parse("2026-10-06T22:10:00Z");

    private final RuleBasedNightAgent agent = new RuleBasedNightAgent(new AppConfig(15, 60, 30));

    private NightSession sessionOf(int volumePct, int fadeMinutes) {
        return new NightSession(WAKE_DATE, NightMode.SOUND, new VolumePercent(volumePct), "rain", STARTED_AT,
                STARTED_AT, null, false, fadeMinutes);
    }

    private Observation observationOf(int elapsedMin) {
        return new Observation(elapsedMin, NightMode.SOUND, new VolumePercent(NightConstants.START_VOLUME_PCT), 0, 0);
    }

    // ---- 経過時間に応じて下がる ----

    @Test
    void theTargetVolumeGoesDownAsTimePasses() {
        NightSession session = sessionOf(NightConstants.START_VOLUME_PCT, 30);

        List<ToolCall> atStart = agent.decideFade(observationOf(0), session);
        List<ToolCall> later = agent.decideFade(observationOf(15), session);

        assertTrue(atStart.isEmpty(), "始まってすぐは、まだ下げる目標に届いていない");
        assertEquals("set_volume", later.get(0).getToolId());
        int loweredPct = Integer.parseInt(later.get(0).findArgument("levelPct"));
        assertTrue(loweredPct < NightConstants.START_VOLUME_PCT, "時間が経てば、目標は下がっている");
    }

    @Test
    void theTargetKeepsGoingDownTheFurtherElapsedMinutesAdvance() {
        // 1 回の呼び出しでの下げ幅の上限（MAX_VOLUME_STEP_DOWN_PCT）に当たらないよう、毎回いまの音量から判断し直す。
        NightSession session = sessionOf(NightConstants.START_VOLUME_PCT, 30);
        int afterFirstStep = Integer.parseInt(agent.decideFade(observationOf(5), session).get(0).findArgument("levelPct"));
        session = session.withVolume(new VolumePercent(afterFirstStep));

        int afterSecondStep = Integer.parseInt(agent.decideFade(observationOf(15), session).get(0).findArgument("levelPct"));

        assertTrue(afterSecondStep < afterFirstStep, "経過時間が進むほど、目標の音量はさらに下がる");
    }

    // ---- 下限で止まる ----

    @Test
    void theTargetNeverGoesBelowTheFloor() {
        NightSession session = sessionOf(NightConstants.FADE_FLOOR_VOLUME_PCT, 30);

        List<ToolCall> afterFadeMinutesHavePassed = agent.decideFade(observationOf(100), session);

        assertTrue(afterFadeMinutesHavePassed.isEmpty(), "下限に既に届いているときは、もう下げない");
    }

    @Test
    void volumeSettlesExactlyAtTheFloorOnceFadeMinutesHaveElapsed() {
        NightSession session = sessionOf(NightConstants.START_VOLUME_PCT, 15);
        // 下げ幅の上限（1 回 10%）に区切られながら、何度か呼べば、下限に落ち着く
        for (int call = 0; call < 10; call++) {
            List<ToolCall> calls = agent.decideFade(observationOf(100), session);
            if (calls.isEmpty()) {
                break;
            }
            session = session.withVolume(new VolumePercent(Integer.parseInt(calls.get(0).findArgument("levelPct"))));
        }

        assertEquals(NightConstants.FADE_FLOOR_VOLUME_PCT, session.getVolume().asInt());
    }

    // ---- 下げる方向だけで、1 回の下げ幅は上限以内 ----

    @Test
    void theCallAlwaysLowersTheVolumeAndNeverBySkipMoreThanTheStepLimit() {
        NightSession session = sessionOf(NightConstants.START_VOLUME_PCT, 15);

        List<ToolCall> calls = agent.decideFade(observationOf(100), session);

        int loweredPct = Integer.parseInt(calls.get(0).findArgument("levelPct"));
        assertTrue(loweredPct < session.getVolume().asInt(), "下げる方向だけ");
        assertTrue(session.getVolume().asInt() - loweredPct <= NightConstants.MAX_VOLUME_STEP_DOWN_PCT,
                "1 回の下げ幅は、上限以内");
    }

    // ---- FADE の値で、速さが変わる ----

    @Test
    void aShorterFadeReachesTheFloorFasterThanALongerOne() {
        NightSession shortFade = sessionOf(NightConstants.START_VOLUME_PCT, 15);
        NightSession longFade = sessionOf(NightConstants.START_VOLUME_PCT, 45);
        Observation sameElapsed = observationOf(10);

        int shortFadeTarget = Integer.parseInt(agent.decideFade(sameElapsed, shortFade).get(0).findArgument("levelPct"));
        List<ToolCall> longFadeCalls = agent.decideFade(sameElapsed, longFade);

        // 同じ経過時間でも、FADE が短い（15分）方が、下げる目標がより低い（速く下がる）
        assertTrue(longFadeCalls.isEmpty() || Integer.parseInt(longFadeCalls.get(0).findArgument("levelPct")) > shortFadeTarget,
                "FADE が長い（45分）方が、同じ経過時間での下げ幅は小さい（緩やか）");
    }

    // ---- 既に目標に届いている、または下げ幅が無いときは、空 ----

    @Test
    void returnsNothingWhenAlreadyAtOrBelowTheTarget() {
        NightSession session = sessionOf(NightConstants.FADE_FLOOR_VOLUME_PCT, 30);

        List<ToolCall> calls = agent.decideFade(observationOf(0), session);

        assertTrue(calls.isEmpty());
    }

    @Test
    void returnsNothingAtTheVeryStartOfTheNight() {
        NightSession session = sessionOf(NightConstants.START_VOLUME_PCT, 30);

        List<ToolCall> calls = agent.decideFade(observationOf(0), session);

        assertTrue(calls.isEmpty(), "経過 0 分では、まだ下げる目標に届いていない");
    }
}
