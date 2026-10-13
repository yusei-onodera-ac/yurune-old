package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.ToolRejectReason;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 受け入れ条件 AT-08: 許可リスト外のツールと、範囲外の引数を拒否する。音量は上限を超えない。
 * 「起こさない」ための制限（急に上げない、黙っているときに話しかけない）も確かめる。
 */
class ToolGuardTest {

    private static final int VOLUME_CAP = 60;
    private static final int CURRENT_VOLUME = 40;
    private static final int QUIET_LIMIT_SEC = 60;

    private final ToolGuard guard = new ToolGuard(new AppConfig(15, VOLUME_CAP, 30), NightFixtures.createOutputGuard());

    private NightSession session(NightMode mode, int volume) {
        return new NightSession(WakeDate.parse("2026-10-07"), mode, new VolumePercent(volume),
                Instant.parse("2026-10-06T13:10:00Z"), false);
    }

    private ToolCall call(String tool, Map<String, String> arguments) {
        return new ToolCall(tool, arguments, new DisplayText("テスト"));
    }

    private boolean accepted(ToolCall call, NightMode mode, int volume, int silenceSec) {
        Observation observation = new Observation(10, mode, new VolumePercent(volume), silenceSec, silenceSec);
        return guard.validate(call, session(mode, volume), observation).isAccepted();
    }

    private boolean accepted(ToolCall call, NightMode mode) {
        return accepted(call, mode, CURRENT_VOLUME, 0);
    }

    @Test
    void toolOutsideAllowlistIsRejected() {
        assertFalse(accepted(call("delete_data", Map.of()), NightMode.SOUND));
    }

    @Test
    void volumeNeverExceedsCap() {
        // 上限の 60 は、現在の音量が 55 のときの、5 の上げ幅なので受け入れられる
        assertTrue(accepted(call("set_volume", Map.of("levelPct", "60", "rampSec", "10")), NightMode.SOUND, 55, 0));
        assertFalse(accepted(call("set_volume", Map.of("levelPct", "61", "rampSec", "10")), NightMode.SOUND, 55, 0));
        assertFalse(accepted(call("set_volume", Map.of("levelPct", "-1", "rampSec", "10")), NightMode.SOUND));
        assertFalse(accepted(call("set_volume", Map.of("levelPct", "30", "rampSec", "121")), NightMode.SOUND));
    }

    @Test
    void volumeMayNotBeRaisedAbruptly() {
        assertTrue(accepted(call("set_volume", Map.of("levelPct", "50", "rampSec", "10")), NightMode.SOUND));
        assertFalse(accepted(call("set_volume", Map.of("levelPct", "51", "rampSec", "10")), NightMode.SOUND),
                "上げ幅が 10% を超える");
        assertFalse(accepted(call("set_volume", Map.of("levelPct", "50", "rampSec", "9")), NightMode.SOUND),
                "上げるのに 10 秒未満");
    }

    @Test
    void loweringTheVolumeIsAlwaysAllowedEvenQuickly() {
        assertTrue(accepted(call("set_volume", Map.of("levelPct", "0", "rampSec", "0")), NightMode.SOUND));
    }

    @Test
    void fadeOutMinutesAreRangeChecked() {
        assertFalse(accepted(call("fade_out", Map.of("minutes", "4")), NightMode.SOUND));
        assertTrue(accepted(call("fade_out", Map.of("minutes", "5")), NightMode.SOUND));
        assertTrue(accepted(call("fade_out", Map.of("minutes", "60")), NightMode.SOUND));
        assertFalse(accepted(call("fade_out", Map.of("minutes", "61")), NightMode.SOUND));
    }

    @Test
    void missingOrBrokenArgumentsAreRejected() {
        assertFalse(accepted(call("fade_out", Map.of()), NightMode.SOUND));
        assertFalse(accepted(call("fade_out", Map.of("minutes", "abc")), NightMode.SOUND));
    }

    @Test
    void agentCanOnlySwitchFromTalkToSound() {
        assertTrue(accepted(call("switch_mode", Map.of("mode", "sound")), NightMode.TALK));
        assertFalse(accepted(call("switch_mode", Map.of("mode", "talk")), NightMode.SOUND));
        assertFalse(accepted(call("switch_mode", Map.of("mode", "sound")), NightMode.SOUND));
    }

    @Test
    void sayIsAllowedOnlyInTalkModeAndChecked() {
        assertTrue(accepted(call("say", Map.of("text", "ゆっくりで大丈夫だよ。")), NightMode.TALK));
        assertFalse(accepted(call("say", Map.of("text", "ゆっくりで大丈夫だよ。")), NightMode.SOUND));
        assertFalse(accepted(call("say", Map.of("text", "睡眠薬を飲んでみて")), NightMode.TALK));
        assertFalse(accepted(call("say", Map.of("text", "あ".repeat(61))), NightMode.TALK));
        assertFalse(accepted(call("say", Map.of()), NightMode.TALK));
    }

    @Test
    void doesNotSpeakFirstWhileTheUserIsSilent() {
        ToolCall say = call("say", Map.of("text", "ゆっくりで大丈夫だよ。"));

        assertTrue(accepted(say, NightMode.TALK, CURRENT_VOLUME, QUIET_LIMIT_SEC - 1));
        assertFalse(accepted(say, NightMode.TALK, CURRENT_VOLUME, QUIET_LIMIT_SEC));
    }

    @Test
    void endSessionAcceptsOnlyKnownReasons() {
        assertTrue(accepted(call("end_session", Map.of("reason", "no_reaction")), NightMode.SOUND));
        assertTrue(accepted(call("end_session", Map.of("reason", "user")), NightMode.SOUND));
        assertFalse(accepted(call("end_session", Map.of("reason", "bored")), NightMode.SOUND));
    }

    @Test
    void switchTrackAcceptsOnlyCatalogKeys() {
        assertTrue(accepted(call("switch_track", Map.of("soundKey", "rain")), NightMode.SOUND));
        assertFalse(accepted(call("switch_track", Map.of("soundKey", "../etc")), NightMode.SOUND));
    }

    // ---- 拒否の理由が、自由な文でなく、固定のコードで分かること ----

    private ToolRejectReason rejectCode(ToolCall call, NightMode mode) {
        Observation observation = new Observation(10, mode, new VolumePercent(CURRENT_VOLUME), 0, 0);
        GuardResult result = guard.validate(call, session(mode, CURRENT_VOLUME), observation);
        assertFalse(result.isAccepted(), "このケースは拒否されるはず");
        return result.getRejectReasonCode().orElseThrow();
    }

    @Test
    void acceptedCallsHaveNoRejectReasonCode() {
        Observation observation = new Observation(10, NightMode.SOUND, new VolumePercent(CURRENT_VOLUME), 0, 0);
        GuardResult result = guard.validate(call("end_session", Map.of("reason", "user")), session(NightMode.SOUND, CURRENT_VOLUME), observation);

        assertTrue(result.isAccepted());
        assertTrue(result.getRejectReasonCode().isEmpty());
    }

    @Test
    void unknownToolIsRejectedAsNotInAllowlist() {
        assertEquals(ToolRejectReason.NOT_IN_ALLOWLIST, rejectCode(call("delete_data", Map.of()), NightMode.SOUND));
    }

    @Test
    void unknownSoundKeyIsRejectedAsNotInAllowlist() {
        assertEquals(ToolRejectReason.NOT_IN_ALLOWLIST,
                rejectCode(call("switch_track", Map.of("soundKey", "../etc")), NightMode.SOUND));
    }

    @Test
    void missingArgumentIsRejectedAsInvalidArgument() {
        assertEquals(ToolRejectReason.INVALID_ARGUMENT, rejectCode(call("fade_out", Map.of()), NightMode.SOUND));
        assertEquals(ToolRejectReason.INVALID_ARGUMENT,
                rejectCode(call("fade_out", Map.of("minutes", "abc")), NightMode.SOUND));
        assertEquals(ToolRejectReason.INVALID_ARGUMENT,
                rejectCode(call("end_session", Map.of("reason", "bored")), NightMode.SOUND));
    }

    @Test
    void outOfRangeValuesAreRejectedAsOutOfRange() {
        assertEquals(ToolRejectReason.OUT_OF_RANGE, rejectCode(call("fade_out", Map.of("minutes", "4")), NightMode.SOUND));
        assertEquals(ToolRejectReason.OUT_OF_RANGE,
                rejectCode(call("set_talk_level", Map.of("level", "99")), NightMode.SOUND));
        assertEquals(ToolRejectReason.OUT_OF_RANGE,
                rejectCode(call("set_volume", Map.of("levelPct", "30", "rampSec", "121")), NightMode.SOUND));
        assertEquals(ToolRejectReason.OUT_OF_RANGE, rejectCode(call("say", Map.of()), NightMode.TALK));
    }

    @Test
    void volumeOverTheCapIsRejectedWithItsOwnCode() {
        assertEquals(ToolRejectReason.VOLUME_OVER_CAP,
                rejectCode(call("set_volume", Map.of("levelPct", "61", "rampSec", "10")), NightMode.SOUND));
    }

    @Test
    void abruptVolumeChangesAreRejectedAsTooFast() {
        assertEquals(ToolRejectReason.TOO_FAST,
                rejectCode(call("set_volume", Map.of("levelPct", "51", "rampSec", "10")), NightMode.SOUND));
        assertEquals(ToolRejectReason.TOO_FAST,
                rejectCode(call("set_volume", Map.of("levelPct", "50", "rampSec", "9")), NightMode.SOUND));
    }

    @Test
    void modeSwitchesNotAllowedHaveTheirOwnCode() {
        assertEquals(ToolRejectReason.MODE_NOT_ALLOWED,
                rejectCode(call("switch_mode", Map.of("mode", "talk")), NightMode.SOUND));
        assertEquals(ToolRejectReason.MODE_NOT_ALLOWED,
                rejectCode(call("switch_mode", Map.of("mode", "sound")), NightMode.SOUND));
    }

    @Test
    void speakingWhenNotAllowedIsRejectedAsTalkNotAllowed() {
        assertEquals(ToolRejectReason.TALK_NOT_ALLOWED,
                rejectCode(call("say", Map.of("text", "ゆっくりで大丈夫だよ。")), NightMode.SOUND));
    }

    @Test
    void speakingWhileTheUserIsSilentIsRejectedAsTalkNotAllowed() {
        ToolCall say = call("say", Map.of("text", "ゆっくりで大丈夫だよ。"));
        Observation observation = new Observation(10, NightMode.TALK, new VolumePercent(CURRENT_VOLUME),
                QUIET_LIMIT_SEC, QUIET_LIMIT_SEC);
        GuardResult result = guard.validate(say, session(NightMode.TALK, CURRENT_VOLUME), observation);

        assertEquals(ToolRejectReason.TALK_NOT_ALLOWED, result.getRejectReasonCode().orElseThrow());
    }

    @Test
    void forbiddenOutputIsRejectedWithItsOwnCode() {
        assertEquals(ToolRejectReason.FORBIDDEN_TEXT, rejectCode(call("say", Map.of("text", "睡眠薬を飲んでみて")), NightMode.TALK));
    }
}
