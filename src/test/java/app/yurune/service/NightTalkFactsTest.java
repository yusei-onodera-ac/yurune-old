package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.constant.GuardConstants;
import app.yurune.domain.value.FactSheet;
import app.yurune.domain.value.NightMode;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryNightSessionRepository;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 会話に渡してよい事実（今夜の音、残りの分、いまの時刻）を、コードが、夜の状態から作ること。
 * 利用者が話したら、最後の反応が新しくなること。
 */
class NightTalkFactsTest {

    private final MutableClock clock = new MutableClock("2026-10-06T14:00:00Z", "Asia/Tokyo");
    private final AppConfig config = new AppConfig(15, 60, 30);
    private NightSessionService service;

    @BeforeEach
    void setUp() {
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        service = new NightSessionService(new InMemoryNightSessionRepository(), new InMemoryNightRepository(),
                new InMemoryDecisionRepository(), new RuleBasedNightAgent(config),
                new ToolGuard(config, NightFixtures.createOutputGuard()), config, clock);
        assertTrue(filter != null);
    }

    @Test
    void factsExistOnlyWhileTalking() {
        assertTrue(service.findTalkFacts().isEmpty(), "夜が始まっていない");

        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        assertTrue(service.findTalkFacts().isEmpty(), "環境音だけのときは、会話がない");

        service.changeMode(NightMode.TALK);
        assertTrue(service.findTalkFacts().isPresent());
    }

    @Test
    void factsCarryTheSoundTheRemainingMinutesAndTheClock() {
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.TALK);
        clock.advance(Duration.ofMinutes(12));

        FactSheet facts = service.findTalkFacts().orElseThrow();

        assertEquals("雨の音", facts.findSoundLabel().orElseThrow());
        assertEquals("33", facts.findMarkerValue(GuardConstants.MARKER_MINUTES_LEFT).orElseThrow(), "45 分から 12 分たった");
        assertEquals("23:12", facts.findMarkerValue(GuardConstants.MARKER_CLOCK_TIME).orElseThrow(), "日本時間");
    }

    @Test
    void remainingMinutesNeverGoBelowZero() {
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.TALK);
        clock.advance(Duration.ofMinutes(500));

        assertEquals("0", service.findTalkFacts().orElseThrow().findMarkerValue(GuardConstants.MARKER_MINUTES_LEFT).orElseThrow());
    }

    @Test
    void speakingResetsTheTimeWithoutReaction() {
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.TALK);
        clock.advance(Duration.ofSeconds(50));
        service.recordUserReaction();
        clock.advance(Duration.ofSeconds(20));

        NightObservationResult result = service.observe(86_000);

        assertTrue(result.getAppliedCalls().isEmpty(), "話してから 20 秒なので、何もしない");
    }

    @Test
    void aNightThatNeverReportedItsEndIsNotKeptAliveForever() {
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.TALK);

        clock.advance(Duration.ofHours(11));
        assertTrue(service.findActiveSession().isPresent());

        clock.advance(Duration.ofHours(2));
        assertTrue(service.findActiveSession().isEmpty(), "12 時間をすぎた夜は、終わったものとして扱う");
        assertTrue(service.findTalkFacts().isEmpty());
    }

    @Test
    void speakingWithoutANightDoesNothing() {
        service.recordUserReaction();

        assertTrue(service.findActiveSession().isEmpty());
    }
}
