package app.yurune.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.SettingsConstants;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.SettingItem;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.VoiceStyle;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 設定の 4 つの値（夜の会話、音量の上限、ぽてねの声、好みを覚える）。初期値、選べる値、古い保存データの読み方。
 */
class UserSettingsTest {

    @Test
    void defaultsFollowTheSpecification() {
        UserSettings settings = UserSettings.defaults();

        assertTrue(settings.isNightTalkEnabled(), "夜の会話の初期値はオン");
        assertEquals(60, settings.getVolumeCap().asInt(), "音量の上限の初期値は 60%");
        assertEquals(VoiceStyle.SOFT, settings.getVoiceStyle());
        assertEquals("やわらかい", settings.getVoiceStyle().asLabel());
        assertTrue(settings.isLearningEnabled(), "好みを覚えるの初期値はオン");
        assertFalse(settings.isKeepScreenOnEnabled(), "画面をつけたままにするの初期値はオフ（仮置き）");
    }

    @Test
    void changingReturnsANewValueAndLeavesTheOldOneAlone() {
        UserSettings before = UserSettings.defaults();

        UserSettings after = before.withNightTalkEnabled(false).withVolumeCapPct(30)
                .withVoiceStyle(VoiceStyle.SLOW).withLearningEnabled(false);

        assertTrue(before.isNightTalkEnabled());
        assertEquals(60, before.getVolumeCap().asInt());
        assertFalse(after.isNightTalkEnabled());
        assertEquals(30, after.getVolumeCap().asInt());
        assertEquals(VoiceStyle.SLOW, after.getVoiceStyle());
        assertFalse(after.isLearningEnabled());
        assertNotEquals(before, after);
        assertEquals(UserSettings.defaults(), before);
    }

    @Test
    void everyItemChangesOnlyItself() {
        UserSettings base = UserSettings.defaults();

        assertEquals(base.withNightTalkEnabled(false), base.change(SettingItem.NIGHT_TALK, "off"));
        assertEquals(base.withVolumeCapPct(40), base.change(SettingItem.VOLUME_CAP, "40"));
        assertEquals(base.withVoiceStyle(VoiceStyle.NORMAL), base.change(SettingItem.VOICE, "normal"));
        assertEquals(base.withLearningEnabled(false), base.change(SettingItem.LEARNING, "off"));
        assertEquals(base.withKeepScreenOnEnabled(true), base.change(SettingItem.KEEP_SCREEN_ON, "on"));
        assertEquals(base, base.change(SettingItem.NIGHT_TALK, "on"));
    }

    @Test
    void onlyTheListedVolumeStepsAreAccepted() {
        UserSettings base = UserSettings.defaults();

        for (int percent : SettingsConstants.VOLUME_CAP_CHOICES) {
            assertEquals(percent, base.withVolumeCapPct(percent).getVolumeCap().asInt());
        }
        for (int percent : List.of(-1, 0, 19, 25, 61, 70, 100, 101, Integer.MAX_VALUE)) {
            assertThrows(IllegalArgumentException.class, () -> base.withVolumeCapPct(percent), "音量 " + percent);
        }
        assertTrue(SettingsConstants.VOLUME_CAP_CHOICES.stream().allMatch(percent -> percent <= 60),
                "初期値より大きい上限は、選べない");
    }

    @Test
    void valuesFromTheScreenAreCheckedStrictly() {
        UserSettings base = UserSettings.defaults();

        for (String bad : new String[] {null, "", "ON", "true", "1", "yes", " on", "on "}) {
            assertThrows(IllegalArgumentException.class, () -> base.change(SettingItem.NIGHT_TALK, bad), "switch " + bad);
            assertThrows(IllegalArgumentException.class, () -> base.change(SettingItem.LEARNING, bad), "switch " + bad);
            assertThrows(IllegalArgumentException.class, () -> base.change(SettingItem.KEEP_SCREEN_ON, bad), "switch " + bad);
        }
        for (String bad : new String[] {null, "", "abc", "45", "60.0", "-30", "99999999999", "30 "}) {
            assertThrows(IllegalArgumentException.class, () -> base.change(SettingItem.VOLUME_CAP, bad), "volume " + bad);
        }
        for (String bad : new String[] {null, "", "loud", "SOFT", "やわらかい", "soft "}) {
            assertThrows(IllegalArgumentException.class, () -> base.change(SettingItem.VOICE, bad), "voice " + bad);
        }
    }

    @Test
    void unknownItemsAreRefused() {
        for (String bad : new String[] {null, "", "volume", "NIGHT_TALK", "night_talk ", "consent", "seeded"}) {
            assertThrows(IllegalArgumentException.class, () -> SettingItem.fromId(bad), "item " + bad);
        }
        assertEquals(SettingItem.VOLUME_CAP, SettingItem.fromId("volume_cap"));
    }

    @Test
    void oldStoredDataWithoutSettingsReadsAsDefaults() {
        assertEquals(UserSettings.defaults(), UserSettings.restore(null, null, null, null));
    }

    @Test
    void eachMissingOrUnreadableStoredItemFallsBackAloneAndTheOthersAreKept() {
        UserSettings settings = UserSettings.restore(false, 99L, "loud", null);

        assertFalse(settings.isNightTalkEnabled(), "読めた値は、そのまま");
        assertEquals(60, settings.getVolumeCap().asInt(), "選べない値は、初期値");
        assertEquals(VoiceStyle.SOFT, settings.getVoiceStyle());
        assertTrue(settings.isLearningEnabled(), "ない項目は、初期値");
        assertEquals(60, UserSettings.restore(null, 4_294_967_316L, null, null).getVolumeCap().asInt(),
                "大きな数を 32 ビットに丸めて（20 になって）、段階の値にしてはいけない");
        assertEquals(30, UserSettings.restore(null, 30L, "slow", false).getVolumeCap().asInt());
    }

    @Test
    void theNightGoalDefaultsToOnsetAndRoundTripsThroughChangeAndDescribe() {
        assertEquals(NightGoal.ONSET, UserSettings.defaults().getNightGoal());

        UserSettings changed = UserSettings.defaults().change(SettingItem.GOAL, "wake");

        assertEquals(NightGoal.WAKE, changed.getNightGoal());
        assertEquals("wake", changed.describeValue(SettingItem.GOAL));
    }

    @Test
    void anOldDocumentOrAnUnknownNightGoalFallsBackToOnset() {
        assertEquals(NightGoal.ONSET, UserSettings.restore(null, null, null, null, null).getNightGoal(),
                "項目がない古い文書は、寝つき");
        assertEquals(NightGoal.ONSET, UserSettings.restore(null, null, null, null, "unknown").getNightGoal());
        assertEquals(NightGoal.MORNING, UserSettings.restore(null, null, null, null, "morning").getNightGoal());
    }

    @Test
    void anUnknownNightGoalValueIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> UserSettings.defaults().change(SettingItem.GOAL, "mood"));
    }

    @Test
    void keepScreenOnRoundTripsThroughChangeAndDescribe() {
        UserSettings on = UserSettings.defaults().change(SettingItem.KEEP_SCREEN_ON, "on");

        assertTrue(on.isKeepScreenOnEnabled());
        assertEquals("on", on.describeValue(SettingItem.KEEP_SCREEN_ON));
        assertFalse(UserSettings.defaults().isKeepScreenOnEnabled());
        assertEquals(on, UserSettings.defaults().withKeepScreenOnEnabled(true));
    }

    @Test
    void anOldDocumentWithoutKeepScreenOnReadsAsOff() {
        assertFalse(UserSettings.restore(null, null, null, null, null, null).isKeepScreenOnEnabled());
        assertTrue(UserSettings.restore(null, null, null, null, null, true).isKeepScreenOnEnabled());
        assertFalse(UserSettings.restore(null, null, null, null, null, false).isKeepScreenOnEnabled());
    }

    @Test
    void theCautiousValuesLeanToTheSafeSide() {
        UserSettings cautious = UserSettings.cautious();

        assertFalse(cautious.isNightTalkEnabled());
        assertFalse(cautious.isLearningEnabled());
        assertEquals(SettingsConstants.VOLUME_CAP_CHOICES.get(0), cautious.getVolumeCap().asInt());
        assertTrue(cautious.getVolumeCap().asInt() <= UserSettings.defaults().getVolumeCap().asInt());
    }

    @Test
    void theEffectiveCapIsTheLowerOfTheUsersAndTheOperators() {
        UserSettings thirty = UserSettings.defaults().withVolumeCapPct(30);

        assertEquals(30, thirty.getVolumeCapPctWithin(60));
        assertEquals(20, thirty.getVolumeCapPctWithin(20), "運用の上限が低いときは、それを超えない");
        assertEquals(0, thirty.getVolumeCapPctWithin(0));
    }

    @Test
    void theLoggedValueComesFromTheTypedValueOnly() {
        UserSettings settings = UserSettings.defaults().withNightTalkEnabled(false).withVolumeCapPct(40)
                .withVoiceStyle(VoiceStyle.SLOW);

        assertEquals("off", settings.describeValue(SettingItem.NIGHT_TALK));
        assertEquals("40", settings.describeValue(SettingItem.VOLUME_CAP));
        assertEquals("slow", settings.describeValue(SettingItem.VOICE));
        assertEquals("on", settings.describeValue(SettingItem.LEARNING));
    }

    @Test
    void theDefaultVoiceIsSlowAndLowAndNoVoiceIsFasterOrHigherThanNormal() {
        VoiceStyle soft = VoiceStyle.findDefault();

        assertEquals(VoiceStyle.SOFT, soft);
        assertTrue(soft.getSpeakingRate() < 1.0, "遅め");
        assertTrue(soft.getPitch() < 0.0, "低め");
        for (VoiceStyle style : VoiceStyle.values()) {
            assertTrue(style.getSpeakingRate() <= VoiceStyle.NORMAL.getSpeakingRate(), style.asId());
            assertTrue(style.getPitch() <= VoiceStyle.NORMAL.getPitch(), style.asId());
            assertTrue(style.getBrowserRate() <= VoiceStyle.NORMAL.getBrowserRate(), style.asId());
            assertEquals(style, VoiceStyle.fromId(style.asId()));
        }
    }
}
