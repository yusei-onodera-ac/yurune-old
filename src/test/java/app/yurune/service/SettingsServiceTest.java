package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.value.SettingItem;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.VoiceStyle;
import app.yurune.repository.DocumentUserStorage;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.repository.UserProfileRepository;
import app.yurune.storage.FlakyDocumentStore;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 設定の読み出しと変更。保存と読み直し、選べない値の拒否、利用者ごとの分離、保存先が止まったときの守り方。
 */
class SettingsServiceTest {

    private FlakyDocumentStore store;
    private DocumentUserStorage storage;

    @BeforeEach
    void setUp() {
        store = new FlakyDocumentStore(new InMemoryDocumentStore());
        storage = new DocumentUserStorage(store, new MutableClock("2026-10-07T01:00:00Z", "Asia/Tokyo"));
    }

    private SettingsService serviceFor(String uid) {
        return new SettingsService(storage.openFor(uid).getUserProfileRepository());
    }

    @Test
    void aNewUserStartsWithTheDefaults() {
        assertEquals(UserSettings.defaults(), serviceFor("u1").findSettings());
    }

    @Test
    void aChangeIsSavedAndSurvivesANewService() {
        serviceFor("u1").changeSetting(SettingItem.VOLUME_CAP, "30");
        serviceFor("u1").changeSetting(SettingItem.VOICE, "slow");
        serviceFor("u1").changeSetting(SettingItem.NIGHT_TALK, "off");
        serviceFor("u1").changeSetting(SettingItem.LEARNING, "off");
        serviceFor("u1").changeSetting(SettingItem.KEEP_SCREEN_ON, "on");

        UserSettings read = serviceFor("u1").findSettings();

        assertEquals(30, read.getVolumeCap().asInt());
        assertEquals(VoiceStyle.SLOW, read.getVoiceStyle());
        assertFalse(read.isNightTalkEnabled());
        assertFalse(read.isLearningEnabled());
        assertTrue(read.isKeepScreenOnEnabled());
    }

    @Test
    void settingTheSameValueTwiceChangesNothingTheSecondTime() {
        SettingsService service = serviceFor("u1");

        UserSettings first = service.changeSetting(SettingItem.NIGHT_TALK, "off");
        UserSettings second = service.changeSetting(SettingItem.NIGHT_TALK, "off");

        assertEquals(first, second);
        assertFalse(service.findSettings().isNightTalkEnabled());
    }

    @Test
    void aValueThatCannotBeChosenIsRefusedAndNothingIsSaved() {
        SettingsService service = serviceFor("u1");
        service.changeSetting(SettingItem.VOLUME_CAP, "40");
        int writes = store.countWrites();

        assertThrows(IllegalArgumentException.class, () -> service.changeSetting(SettingItem.VOLUME_CAP, "80"));
        assertThrows(IllegalArgumentException.class, () -> service.changeSetting(SettingItem.NIGHT_TALK, "maybe"));
        assertThrows(IllegalArgumentException.class, () -> service.changeSetting(SettingItem.VOICE, "robot"));
        assertThrows(IllegalArgumentException.class, () -> service.changeSetting(SettingItem.LEARNING, null));

        assertEquals(writes, store.countWrites(), "拒否したときは、何も書かない");
        assertEquals(40, service.findSettings().getVolumeCap().asInt());
    }

    @Test
    void changingOneItemKeepsTheConsentAndTheOtherItems() {
        UserProfileRepository repository = storage.openFor("u1").getUserProfileRepository();
        repository.saveProfile(repository.findProfile().withConsent("2026-10-07", Instant.parse("2026-10-07T01:00:00Z")));
        SettingsService service = serviceFor("u1");
        service.changeSetting(SettingItem.VOLUME_CAP, "20");

        service.changeSetting(SettingItem.VOICE, "normal");

        assertEquals(20, service.findSettings().getVolumeCap().asInt());
        assertEquals("2026-10-07", repository.findProfile().findConsentVersion().orElseThrow());
    }

    @Test
    void usersAreSeparated() {
        serviceFor("alice").changeSetting(SettingItem.VOLUME_CAP, "20");
        serviceFor("alice").changeSetting(SettingItem.LEARNING, "off");

        assertEquals(UserSettings.defaults(), serviceFor("bob").findSettings());
        assertEquals(20, serviceFor("alice").findSettings().getVolumeCap().asInt());
    }

    @Test
    void whenTheStoreIsDownTheScreenFailsButEnforcementUsesTheLastKnownValues() {
        SettingsService service = serviceFor("u1");
        service.changeSetting(SettingItem.VOLUME_CAP, "30");
        service.changeSetting(SettingItem.NIGHT_TALK, "off");
        store.setDown(true);

        assertThrows(StorageUnavailableException.class, service::findSettings, "画面は、仮の値を設定として見せない");
        assertThrows(StorageUnavailableException.class, () -> service.changeSetting(SettingItem.VOLUME_CAP, "50"));
        UserSettings enforced = service.findEnforcedSettings();

        assertEquals(30, enforced.getVolumeCap().asInt(), "直前に読めた値を守る");
        assertFalse(enforced.isNightTalkEnabled());
    }

    @Test
    void whenTheStoreIsDownAndNothingIsKnownTheSafeSideIsUsed() {
        SettingsService service = serviceFor("u1");
        store.setDown(true);

        UserSettings enforced = service.findEnforcedSettings();

        assertEquals(UserSettings.cautious(), enforced);
        assertFalse(enforced.isNightTalkEnabled());
        assertFalse(enforced.isLearningEnabled());
        assertTrue(enforced.getVolumeCap().asInt() <= 30);
    }

    @Test
    void enforcementRecoversWhenTheStoreComesBack() {
        SettingsService service = serviceFor("u1");
        service.changeSetting(SettingItem.VOLUME_CAP, "40");
        store.setDown(true);
        assertEquals(40, service.findEnforcedSettings().getVolumeCap().asInt());
        store.setDown(false);
        serviceFor("u1").changeSetting(SettingItem.VOLUME_CAP, "50");

        assertEquals(50, service.findEnforcedSettings().getVolumeCap().asInt());
    }

    @Test
    void aChangeMadeThroughAnotherServiceIsSeenAtTheNextRead() {
        SettingsService reader = serviceFor("u1");
        assertTrue(reader.findEnforcedSettings().isLearningEnabled());

        serviceFor("u1").changeSetting(SettingItem.LEARNING, "off");

        assertFalse(reader.findEnforcedSettings().isLearningEnabled(), "夜の間も、変えた値が次の読み出しから効く");
    }
}
