package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.GentlePolicy;
import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.Arm;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.entity.UserProfile;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.Minutes;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.DocumentStore;
import app.yurune.repository.DocumentUserStorage;
import app.yurune.repository.UserPaths;
import app.yurune.repository.UserRepositories;
import app.yurune.service.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 保存先の約束（Repository）。メモリ上と Firestore（エミュレータ）で、同じ結果になること。
 * 利用者ごとに分かれていることと、データの消去（受け入れ条件 AT-15）も確かめる。
 */
abstract class RepositoryContract {

    protected DocumentStore store;
    protected DocumentUserStorage storage;
    protected final MutableClock clock = new MutableClock("2026-10-07T01:00:00Z", "Asia/Tokyo");

    protected abstract DocumentStore createStore() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        store = createStore();
        storage = new DocumentUserStorage(store, clock);
    }

    private NightPlan plan(String sound, String talk, String fade, Dimension... changed) {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName(sound));
        options.put(Dimension.TALK, new OptionName(talk));
        options.put(Dimension.FADE, new OptionName(fade));
        options.put(Dimension.DURATION, new OptionName("45"));
        EnumSet<Dimension> changedSet = EnumSet.noneOf(Dimension.class);
        changedSet.addAll(List.of(changed));
        return new NightPlan(options, changedSet, new DisplayText("理由の文"), 42L);
    }

    @Test
    void armsRoundTripAndAreFoundByDimension() {
        UserRepositories repositories = storage.openFor("u1");
        ArmKey rain = new ArmKey(Dimension.SOUND, new OptionName("rain"));
        repositories.getArmRepository().saveArm(Arm.untried(rain).withResult(MorningAnswer.FIT).withResult(MorningAnswer.NOT_FIT));
        repositories.getArmRepository().saveArm(Arm.untried(new ArmKey(Dimension.TALK, new OptionName("none"))));

        Arm read = repositories.getArmRepository().findArm(rain).orElseThrow();
        assertEquals(2, read.getTrialCount().asInt());
        assertEquals(1, read.getWinCount().asInt());
        assertEquals(1, repositories.getArmRepository().findArmsByDimension(Dimension.SOUND).size());
        assertTrue(repositories.getArmRepository().findArm(new ArmKey(Dimension.SOUND, new OptionName("wave"))).isEmpty());
    }

    @Test
    void sleepRecordsRoundTripIncludingEmptyDays() {
        UserRepositories repositories = storage.openFor("u1");
        WakeDate day = WakeDate.parse("2026-10-06");
        repositories.getSleepRecordRepository().saveRecord(SleepRecord.recorded(day, ClockTime.parse("23:40"), ClockTime.parse("06:20")));
        repositories.getSleepRecordRepository().saveRecord(SleepRecord.unrecorded(day.minusDays(1)));

        SleepRecord read = repositories.getSleepRecordRepository().findRecord(day).orElseThrow();
        assertEquals("23:40", read.getBedTime().asText());
        assertEquals(400, read.getDuration().asInt());
        assertFalse(repositories.getSleepRecordRepository().findRecord(day.minusDays(1)).orElseThrow().isRecorded());
        assertTrue(repositories.getSleepRecordRepository().findRecord(day.minusDays(5)).isEmpty());
    }

    @Test
    void nightsKeepThePlanTheEndAndTheMorningResult() {
        UserRepositories repositories = storage.openFor("u1");
        WakeDate day = WakeDate.parse("2026-10-07");
        NightRecord started = NightRecord.started(day, plan("rain", "none", "30", Dimension.SOUND));
        repositories.getNightRepository().saveNight(started);

        NightRecord first = repositories.getNightRepository().findNight(day).orElseThrow();
        assertEquals("rain", first.getPlan().getOption(Dimension.SOUND).asText());
        assertTrue(first.getPlan().isChanged(Dimension.SOUND));
        assertFalse(first.getPlan().isChanged(Dimension.TALK));
        assertEquals(42L, first.getPlan().getSeed());
        assertFalse(first.isMorningChecked());

        repositories.getNightRepository().saveNight(started
                .withEnd(EndReason.NO_REACTION, new Minutes(95), ClockTime.parse("01:15"), new Minutes(80))
                .withMorningResult(MorningAnswer.FIT, MorningMood.CALM));
        NightRecord done = repositories.getNightRepository().findNight(day).orElseThrow();
        assertTrue(done.isMorningChecked());
        assertEquals(MorningAnswer.FIT, done.findAnswer().orElseThrow());
        assertEquals(MorningMood.CALM, done.findMood().orElseThrow());
        assertEquals(EndReason.NO_REACTION, done.findEndReason().orElseThrow());
        assertEquals(95, done.findEndedAfter().orElseThrow().asInt());
        assertEquals("01:15", done.findLastReactionTime().orElseThrow().asText());
        assertEquals(80, done.findOnsetProxyMin().orElseThrow().asInt());
    }

    @Test
    void theLearningFieldsRoundTrip() {
        UserRepositories repositories = storage.openFor("u1");
        WakeDate day = WakeDate.parse("2026-10-07");
        NightRecord variant = NightRecord.started(day, plan("rain", "none", "30", Dimension.SOUND),
                        NightRole.VARIANT, Dimension.FADE)
                .withUserChanged()
                .withRestart()
                .withEnd(EndReason.NO_REACTION, new Minutes(95), ClockTime.parse("01:15"), new Minutes(80));
        repositories.getNightRepository().saveNight(variant);

        NightRecord read = repositories.getNightRepository().findNight(day).orElseThrow();
        assertEquals(NightRole.VARIANT, read.findRole().orElseThrow());
        assertEquals(Dimension.FADE, read.findChangedFactor().orElseThrow());
        assertTrue(read.isUserChanged());
        assertEquals(1, read.getRestartCount());
        assertEquals(GentlePolicy.POLICY_VERSION, read.getPolicyVersion());
        assertEquals(80, read.findOnsetProxyMin().orElseThrow().asInt());
    }

    @Test
    void theReviewStateRoundTripsAndIsEmptyForAnotherUser() {
        UserRepositories repositories = storage.openFor("u1");
        assertTrue(repositories.getPdcaStateRepository().findState().getQuarantine().isEmpty());
        ArmKey wave = new ArmKey(Dimension.SOUND, new OptionName("wave"));
        ArmKey forest = new ArmKey(Dimension.SOUND, new OptionName("forest"));
        ArmKey fifteen = new ArmKey(Dimension.FADE, new OptionName("15"));
        PdcaState state = PdcaState.empty().withQuarantine(wave, WakeDate.parse("2026-10-20"))
                .withParked(fifteen, WakeDate.parse("2026-11-03"))
                .withPreferred(forest).withRollback(wave, WakeDate.parse("2026-10-09"))
                .withReferral(WakeDate.parse("2026-10-07")).withReferHiddenUntil(WakeDate.parse("2026-10-30"));

        repositories.getPdcaStateRepository().saveState(state);

        PdcaState read = repositories.getPdcaStateRepository().findState();
        assertEquals(WakeDate.parse("2026-10-20"), read.getQuarantine().get(wave));
        assertTrue(read.isParked(fifteen));
        assertTrue(read.isParkRetryDue(fifteen, WakeDate.parse("2026-11-03")), "見送りの再挑戦の日も、往復で保たれる");
        assertTrue(read.isPreferred(forest));
        assertEquals(wave, read.findRolledBackKey().orElseThrow());
        assertEquals(WakeDate.parse("2026-10-09"), read.findRollbackHoldUntil().orElseThrow());
        assertEquals(WakeDate.parse("2026-10-07"), read.findReferredOn().orElseThrow());
        assertEquals(WakeDate.parse("2026-10-30"), read.findReferHiddenUntil().orElseThrow());
        assertTrue(storage.openFor("u2").getPdcaStateRepository().findState().getQuarantine().isEmpty(), "別の利用者には出ない");
    }

    /** 後から足した項目がない文書（夜の指標を足す前に書かれた記録）も、既定の値で読めること。 */
    @Test
    void documentsWrittenBeforeTheLearningFieldsReadWithDefaults() {
        UserRepositories repositories = storage.openFor("u1");
        Map<String, Object> options = Map.of("sound", "rain", "talk", "none", "fade", "30", "duration", "45");
        Map<String, Object> oldFields = new LinkedHashMap<>();
        oldFields.put("wakeDate", "2026-10-06");
        oldFields.put("planOptions", options);
        oldFields.put("planChanged", List.of());
        oldFields.put("planReason", "理由の文");
        oldFields.put("planSeed", 42L);
        oldFields.put("morningChecked", true);
        oldFields.put("answer", null);
        oldFields.put("mood", null);
        oldFields.put("endReason", "no_reaction");
        oldFields.put("endedAfterMin", 95L);
        oldFields.put("lastReactionTime", "01:15");
        store.set(new UserPaths("u1").document(StorageConstants.NIGHTS, "2026-10-06"), oldFields);

        NightRecord read = repositories.getNightRepository().findNight(WakeDate.parse("2026-10-06")).orElseThrow();
        assertEquals(EndReason.NO_REACTION, read.findEndReason().orElseThrow());
        assertEquals(95, read.findEndedAfter().orElseThrow().asInt());
        assertTrue(read.findRole().isEmpty());
        assertTrue(read.findChangedFactor().isEmpty());
        assertFalse(read.isUserChanged());
        assertTrue(read.findOnsetProxyMin().isEmpty());
        assertEquals(0, read.getRestartCount());
        assertEquals(0, read.getPolicyVersion());
    }

    @Test
    void unansweredMorningsAreStoredWithoutAnAnswer() {
        UserRepositories repositories = storage.openFor("u1");
        WakeDate day = WakeDate.parse("2026-10-07");
        repositories.getNightRepository().saveNight(NightRecord.started(day, plan("rain", "none", "30"))
                .withMorningResult(null, null));

        NightRecord read = repositories.getNightRepository().findNight(day).orElseThrow();
        assertTrue(read.isMorningChecked());
        assertTrue(read.findAnswer().isEmpty());
        assertTrue(read.findMood().isEmpty());
    }

    @Test
    void latestNightsAreFoundByDate() {
        UserRepositories repositories = storage.openFor("u1");
        for (String date : List.of("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08")) {
            repositories.getNightRepository().saveNight(NightRecord.started(WakeDate.parse(date), plan("rain", "none", "30")));
        }
        repositories.getNightRepository().saveNight(NightRecord.started(WakeDate.parse("2026-10-06"), plan("rain", "none", "30"))
                .withMorningResult(MorningAnswer.FIT, null));

        assertEquals("2026-10-08", repositories.getNightRepository().findLatestNight().orElseThrow().getWakeDate().asIsoText());
        assertEquals("2026-10-07",
                repositories.getNightRepository().findLatestUncheckedNight(WakeDate.parse("2026-10-07")).orElseThrow().getWakeDate().asIsoText());
        assertEquals("2026-10-05",
                repositories.getNightRepository().findLatestUncheckedNight(WakeDate.parse("2026-10-05")).orElseThrow().getWakeDate().asIsoText());
        assertTrue(repositories.getNightRepository().findLatestUncheckedNight(WakeDate.parse("2026-10-04")).isEmpty());
    }

    @Test
    void recentNightsComeNewestFirstUpToTheLimitAndOnlyForTheOwner() {
        UserRepositories repositories = storage.openFor("u1");
        for (String date : List.of("2026-10-05", "2026-10-07", "2026-10-06", "2026-10-08")) {
            repositories.getNightRepository().saveNight(NightRecord.started(WakeDate.parse(date), plan("rain", "none", "30")));
        }
        storage.openFor("u2").getNightRepository()
                .saveNight(NightRecord.started(WakeDate.parse("2026-10-09"), plan("wave", "on", "45")));

        List<NightRecord> recent = repositories.getNightRepository().findRecentNights(3);

        assertEquals(List.of("2026-10-08", "2026-10-07", "2026-10-06"),
                recent.stream().map(night -> night.getWakeDate().asIsoText()).toList());
        assertEquals(4, repositories.getNightRepository().findRecentNights(10).size(), "他の利用者の晩は含まれない");
        assertTrue(storage.openFor("u3").getNightRepository().findRecentNights(7).isEmpty());
    }

    @Test
    void theCurrentSessionRoundTrips() {
        UserRepositories repositories = storage.openFor("u1");
        assertTrue(repositories.getNightSessionRepository().findCurrentSession().isEmpty());
        Instant started = Instant.parse("2026-10-06T14:00:00Z");
        repositories.getNightSessionRepository().saveSession(new NightSession(WakeDate.parse("2026-10-07"), NightMode.TALK,
                new VolumePercent(40), started, started.plusSeconds(90), false));

        NightSession read = repositories.getNightSessionRepository().findCurrentSession().orElseThrow();
        assertEquals(NightMode.TALK, read.getMode());
        assertEquals(40, read.getVolume().asInt());
        assertEquals(started.plusSeconds(90), read.getLastReactionAt());
        assertFalse(read.isEnded());
    }

    @Test
    void decisionsAreKeptInTheOrderTheyWereRecorded() {
        UserRepositories repositories = storage.openFor("u1");
        WakeDate night = WakeDate.parse("2026-10-07");
        for (int index = 0; index < 5; index++) {
            clock.advance(Duration.ofSeconds(1));
            repositories.getDecisionRepository().saveDecision(new Decision(night, ClockTime.parse("23:4" + index),
                    DecisionPhase.ACT, Map.of("silenceSec", "6" + index), new DisplayText("選んだこと" + index),
                    new DisplayText("理由" + index), index != 3));
        }
        repositories.getDecisionRepository().saveDecision(new Decision(WakeDate.parse("2026-10-06"), ClockTime.parse("23:00"),
                DecisionPhase.PLAN, Map.of(), new DisplayText("前の晩"), new DisplayText("理由"), true));

        List<Decision> read = repositories.getDecisionRepository().findDecisions(night);
        assertEquals(List.of("選んだこと0", "選んだこと1", "選んだこと2", "選んだこと3", "選んだこと4"),
                read.stream().map(decision -> decision.getChosen().asText()).toList());
        assertFalse(read.get(3).isAccepted());
        assertEquals("63", read.get(3).getObserved().get("silenceSec"));
        assertEquals(5, repositories.getDecisionRepository().findLatestDecisions().size());
        assertTrue(repositories.getDecisionRepository().findDecisions(WakeDate.parse("2026-01-01")).isEmpty());
    }

    @Test
    void theProfileRoundTrips() {
        UserRepositories repositories = storage.openFor("u1");
        assertFalse(repositories.getUserProfileRepository().findProfile().isSeeded());

        Instant at = Instant.parse("2026-10-07T01:00:00Z");
        repositories.getUserProfileRepository().saveProfile(UserProfile.empty().withConsent("2026-10-07", at).withSeeded());

        UserProfile profile = repositories.getUserProfileRepository().findProfile();
        assertEquals("2026-10-07", profile.findConsentVersion().orElseThrow());
        assertEquals(at, profile.findConsentedAt().orElseThrow());
        assertTrue(profile.isSeeded());
    }

    // ---- 利用者ごとの分離と、データの消去 ----

    @Test
    void oneUserNeverSeesAnotherUsersData() {
        UserRepositories alice = storage.openFor("alice");
        UserRepositories bob = storage.openFor("bob");
        alice.getArmRepository().saveArm(Arm.untried(new ArmKey(Dimension.SOUND, new OptionName("rain"))));
        alice.getNightRepository().saveNight(NightRecord.started(WakeDate.parse("2026-10-07"), plan("rain", "none", "30")));
        alice.getSleepRecordRepository().saveRecord(SleepRecord.unrecorded(WakeDate.parse("2026-10-07")));

        assertTrue(bob.getArmRepository().findArmsByDimension(Dimension.SOUND).isEmpty());
        assertTrue(bob.getNightRepository().findLatestNight().isEmpty());
        assertTrue(bob.getNightRepository().findNight(WakeDate.parse("2026-10-07")).isEmpty());
        assertTrue(bob.getSleepRecordRepository().findRecord(WakeDate.parse("2026-10-07")).isEmpty());
    }

    @Test
    void erasingRemovesEverythingUnderTheUserAndNothingOfOthers() {
        UserRepositories alice = storage.openFor("alice");
        UserRepositories bob = storage.openFor("bob");
        for (UserRepositories repositories : List.of(alice, bob)) {
            repositories.getArmRepository().saveArm(Arm.untried(new ArmKey(Dimension.SOUND, new OptionName("rain"))));
            repositories.getNightRepository().saveNight(NightRecord.started(WakeDate.parse("2026-10-07"), plan("rain", "none", "30")));
            repositories.getSleepRecordRepository().saveRecord(SleepRecord.unrecorded(WakeDate.parse("2026-10-07")));
            repositories.getDecisionRepository().saveDecision(new Decision(WakeDate.parse("2026-10-07"), ClockTime.parse("23:00"),
                    DecisionPhase.PLAN, Map.of(), new DisplayText("計画"), new DisplayText("理由"), true));
            repositories.getNightSessionRepository().saveSession(new NightSession(WakeDate.parse("2026-10-07"), NightMode.SOUND,
                    new VolumePercent(30), Instant.parse("2026-10-06T14:00:00Z"), false));
            repositories.getUserProfileRepository().saveProfile(UserProfile.empty().withSeeded());
            repositories.getPdcaStateRepository().saveState(PdcaState.empty().withReferral(WakeDate.parse("2026-10-07")));
        }

        storage.eraseAll("alice");

        UserPaths alicePaths = new UserPaths("alice");
        for (String collection : List.of("arms", "sleepRecords", "nights", "decisions", "sessions", "profile",
                "learning")) {
            assertTrue(store.list(alicePaths.collection(collection)).isEmpty(), collection + " に、データが残っている");
        }
        assertTrue(store.get(alicePaths.userDocument()).isEmpty());
        assertEquals(1, bob.getArmRepository().findArmsByDimension(Dimension.SOUND).size());
        assertTrue(bob.getNightRepository().findLatestNight().isPresent());
        assertEquals(1, bob.getDecisionRepository().findLatestDecisions().size());
        assertTrue(bob.getUserProfileRepository().findProfile().isSeeded());
        assertTrue(bob.getPdcaStateRepository().findState().findReferredOn().isPresent(), "見直しの状態も、本人だけ消える");
    }

    @Test
    void pathsRefuseCharactersThatCouldEscapeTheUsersFolder() {
        for (String unsafe : List.of("", "../bob", "a/b", "alice/arms", "a b", "x%2F", "日本語")) {
            assertThrows(IllegalArgumentException.class, () -> new UserPaths(unsafe), unsafe);
        }
        assertEquals("users/abc_DEF-123", new UserPaths("abc_DEF-123").userDocument());
    }
}
