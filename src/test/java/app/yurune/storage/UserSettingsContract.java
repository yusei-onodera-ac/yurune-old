package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.UserProfile;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.VoiceStyle;
import app.yurune.repository.DocumentStore;
import app.yurune.repository.DocumentUserStorage;
import app.yurune.repository.UserPaths;
import app.yurune.repository.UserRepositories;
import app.yurune.service.MutableClock;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 設定（夜の会話、音量の上限、ぽてねの声、好みを覚える）の保存先の約束。メモリ上と Firestore（エミュレータ）で、同じ結果になること。
 * 設定の項目がない古い文書、読めない値、利用者ごとの分離、データの消去も確かめる。
 */
abstract class UserSettingsContract {

    private static final String PROFILE_COLLECTION = "profile";
    private static final String PROFILE_ID = "main";

    protected DocumentStore store;
    protected DocumentUserStorage storage;
    private final MutableClock clock = new MutableClock("2026-10-07T01:00:00Z", "Asia/Tokyo");

    protected abstract DocumentStore createStore() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        store = createStore();
        storage = new DocumentUserStorage(store, clock);
    }

    private UserSettings changedSettings() {
        return UserSettings.defaults().withNightTalkEnabled(false).withVolumeCapPct(30)
                .withVoiceStyle(VoiceStyle.SLOW).withLearningEnabled(false).withNightGoal(NightGoal.WAKE)
                .withKeepScreenOnEnabled(true);
    }

    private void putProfileDocument(String uid, Map<String, Object> fields) {
        store.set(new UserPaths(uid).document(PROFILE_COLLECTION, PROFILE_ID), fields);
    }

    @Test
    void aUserWithNothingSavedHasTheDefaultSettings() {
        assertEquals(UserSettings.defaults(), storage.openFor("u1").getUserProfileRepository().findProfile().getSettings());
    }

    @Test
    void settingsRoundTripTogetherWithTheRestOfTheProfile() {
        UserRepositories repositories = storage.openFor("u1");
        Instant at = Instant.parse("2026-10-07T01:00:00Z");

        repositories.getUserProfileRepository().saveProfile(
                UserProfile.empty().withConsent("2026-10-07", at).withSeeded().withSettings(changedSettings()));

        UserProfile read = repositories.getUserProfileRepository().findProfile();
        assertEquals(changedSettings(), read.getSettings());
        assertEquals("2026-10-07", read.findConsentVersion().orElseThrow());
        assertEquals(at, read.findConsentedAt().orElseThrow());
        assertTrue(read.isSeeded());
    }

    @Test
    void anOldDocumentWithoutSettingsIsReadWithTheDefaultsAndKeepsItsOtherFields() {
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("consentVersion", "2026-10-01");
        old.put("consentedAt", "2026-10-01T01:00:00Z");
        old.put("seeded", true);
        putProfileDocument("u1", old);

        UserProfile read = storage.openFor("u1").getUserProfileRepository().findProfile();

        assertEquals(UserSettings.defaults(), read.getSettings());
        assertEquals("2026-10-01", read.findConsentVersion().orElseThrow());
        assertTrue(read.isSeeded());
    }

    @Test
    void anOldDocumentGainsTheSettingsWhenOneItemIsChangedAndKeepsTheRest() {
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("consentVersion", "2026-10-01");
        old.put("consentedAt", "2026-10-01T01:00:00Z");
        old.put("seeded", true);
        putProfileDocument("u1", old);
        UserRepositories repositories = storage.openFor("u1");

        UserProfile profile = repositories.getUserProfileRepository().findProfile();
        repositories.getUserProfileRepository().saveProfile(profile.withSettings(profile.getSettings().withLearningEnabled(false)));

        UserProfile read = repositories.getUserProfileRepository().findProfile();
        assertFalse(read.getSettings().isLearningEnabled());
        assertTrue(read.getSettings().isNightTalkEnabled(), "触っていない項目は、初期値のまま");
        assertEquals("2026-10-01", read.findConsentVersion().orElseThrow());
        assertTrue(read.isSeeded());
    }

    @Test
    void unreadableStoredValuesFallBackItemByItem() {
        Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("seeded", false);
        broken.put("nightTalkEnabled", "yes");
        broken.put("volumeCapPct", 99L);
        broken.put("voiceStyle", "loud");
        broken.put("learningEnabled", false);
        broken.put("nightGoal", 7L);
        broken.put("keepScreenOn", "yes");
        putProfileDocument("u1", broken);

        UserSettings read = storage.openFor("u1").getUserProfileRepository().findProfile().getSettings();

        assertTrue(read.isNightTalkEnabled(), "型が違うときは、初期値");
        assertEquals(60, read.getVolumeCap().asInt(), "選べない値は、初期値");
        assertEquals(VoiceStyle.SOFT, read.getVoiceStyle());
        assertFalse(read.isLearningEnabled(), "読めた値は、そのまま");
        assertEquals(NightGoal.ONSET, read.getNightGoal(), "型が違うときは、寝つき");
        assertFalse(read.isKeepScreenOnEnabled(), "型が違うときは、オフ");
    }

    @Test
    void savingConsentOrSeededKeepsTheSettings() {
        UserRepositories repositories = storage.openFor("u1");
        repositories.getUserProfileRepository().saveProfile(UserProfile.empty().withSettings(changedSettings()));

        repositories.getUserProfileRepository().saveProfile(
                repositories.getUserProfileRepository().findProfile().withConsent("2026-10-07", Instant.parse("2026-10-07T01:00:00Z")));
        repositories.getUserProfileRepository().saveProfile(repositories.getUserProfileRepository().findProfile().withSeeded());

        assertEquals(changedSettings(), repositories.getUserProfileRepository().findProfile().getSettings());
    }

    @Test
    void oneUsersSettingsNeverReachAnotherUser() {
        UserRepositories alice = storage.openFor("alice");
        UserRepositories bob = storage.openFor("bob");

        alice.getUserProfileRepository().saveProfile(UserProfile.empty().withSettings(changedSettings()));

        assertEquals(changedSettings(), alice.getUserProfileRepository().findProfile().getSettings());
        assertEquals(UserSettings.defaults(), bob.getUserProfileRepository().findProfile().getSettings());
    }

    @Test
    void erasingTheUsersDataBringsTheSettingsBackToTheDefaultsAndLeavesOthersAlone() {
        UserRepositories alice = storage.openFor("alice");
        UserRepositories bob = storage.openFor("bob");
        alice.getUserProfileRepository().saveProfile(UserProfile.empty().withSettings(changedSettings()));
        bob.getUserProfileRepository().saveProfile(UserProfile.empty().withSettings(changedSettings()));

        storage.eraseAll("alice");

        assertEquals(UserSettings.defaults(), alice.getUserProfileRepository().findProfile().getSettings());
        assertEquals(changedSettings(), bob.getUserProfileRepository().findProfile().getSettings());
    }
}
