package app.yurune.service;

import static app.yurune.service.ReviewFixtures.day;
import static app.yurune.service.ReviewFixtures.soundVariant;
import static app.yurune.service.ReviewFixtures.soundVariantsWithControls;
import static app.yurune.service.ReviewFixtures.standard;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.NoteSummaryConstants;
import app.yurune.constant.ReviewTextConstants;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 「いまのところ」の文。基準の間、標準の夜の中央値、比べ（方向と夜数）、様子見、受診のすすめ。
 * 事実の数字だけを言い、3 行を超えず、禁止の語を含まない。
 */
class NoteSummaryBuilderTest {

    private static final int STANDARD_ONSET = 40;
    private static final int BETTER_ONSET = 10;
    private static final int WORSE_ONSET = 70;

    private static final NoteSummaryBuilder ONSET_BUILDER = new NoteSummaryBuilder(NightGoal.ONSET);

    /** 基準の 7 夜（寝つき 40 分）。 */
    private List<NightRecord> sevenBaselineNights() {
        List<NightRecord> nights = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            nights.add(standard(day(offset), NightRole.BASELINE, STANDARD_ONSET, null));
        }
        return nights;
    }

    /** 新しい順に並べる（NightRepository が返す順）。 */
    private static List<NightRecord> newestFirst(List<NightRecord> nights) {
        List<NightRecord> sorted = new ArrayList<>(nights);
        sorted.sort(Comparator.comparing(NightRecord::getWakeDate).reversed());
        return sorted;
    }

    private static WakeDate todayAfter(int offset) {
        return day(offset);
    }

    @Test
    void withNoNightsOnlyTheBaselineSentenceIsShown() {
        NoteSummary summary = ONSET_BUILDER.build(day(0), List.of(), PdcaState.empty());

        assertEquals(List.of(NoteSummaryConstants.BASELINE_LINE), summary.lines());
        assertFalse(summary.referralShown());
    }

    @Test
    void whileTheBaselineIsStillGatheringTheStandardMedianIsShownWithItsNightCount() {
        List<NightRecord> nights = new ArrayList<>();
        for (int offset = 0; offset < 3; offset++) {
            nights.add(standard(day(offset), NightRole.BASELINE, STANDARD_ONSET, null));
        }

        NoteSummary summary = ONSET_BUILDER.build(todayAfter(3), newestFirst(nights), PdcaState.empty());

        assertEquals(List.of(NoteSummaryConstants.BASELINE_LINE,
                "標準の夜は、寝つきのめやすが、だいたい40分ごろだよ（3夜）。"), summary.lines());
    }

    @Test
    void theStandardMedianIsTheMiddleOfTheStandardNightsAndIsOnlyTheNumber() {
        NoteSummary summary = ONSET_BUILDER.build(todayAfter(7), newestFirst(sevenBaselineNights()), PdcaState.empty());

        assertEquals(List.of("標準の夜は、寝つきのめやすが、だいたい40分ごろだよ（7夜）。", NoteSummaryConstants.WATCHING_LINE),
                summary.lines());
    }

    /**
     * 比べの一行は、夜の見直し（NightReviewService）と同じ比べ方（近い標準の夜とのペアの差）を使う。
     * そのため、対照夜（標準のやり方の夜）を、変えた夜の近くに混ぜる（soundVariantsWithControls。R2 のペアが見つかるように）。
     */
    @Test
    void aBetterOptionIsNamedWithItsNightCountAndHedged() {
        List<NightRecord> nights = sevenBaselineNights();
        nights.addAll(soundVariantsWithControls(7, 6, "wave", BETTER_ONSET, STANDARD_ONSET));

        NoteSummary summary = ONSET_BUILDER.build(todayAfter(16), newestFirst(nights), PdcaState.empty());

        assertEquals(2, summary.lines().size());
        assertTrue(summary.lines().get(0).startsWith("標準の夜は、寝つきのめやすが、だいたい40分ごろだよ"), summary.lines().toString());
        assertTrue(summary.lines().get(1).startsWith("「波の音」の夜は、標準より寝つきが短めの夜が多かったよ"), summary.lines().toString());
        assertTrue(summary.lines().get(1).endsWith(NoteSummaryConstants.HEDGE_LINE));
    }

    @Test
    void aWorseOptionIsNamedInTheOtherDirectionAndStillHedged() {
        List<NightRecord> nights = sevenBaselineNights();
        nights.addAll(soundVariantsWithControls(7, 6, "wave", WORSE_ONSET, STANDARD_ONSET));

        NoteSummary summary = ONSET_BUILDER.build(todayAfter(16), newestFirst(nights), PdcaState.empty());

        assertTrue(summary.lines().get(1).startsWith("「波の音」の夜は、標準より寝つきが長めの夜が多かったよ"),
                summary.lines().toString());
        assertTrue(summary.lines().get(1).endsWith(NoteSummaryConstants.HEDGE_LINE));
    }

    @Test
    void aReferralIsShownWithTheFixedSentenceAndTheStagnantWordingWhenItIsRecent() {
        List<NightRecord> nights = sevenBaselineNights();
        for (int offset = 7; offset <= 11; offset++) {
            nights.add(soundVariant(day(offset), "wave", STANDARD_ONSET, MorningAnswer.NOT_FIT));
        }
        PdcaState referred = PdcaState.empty().withReferral(todayAfter(10));

        NoteSummary summary = ONSET_BUILDER.build(todayAfter(11), newestFirst(nights), referred);

        assertTrue(summary.referralShown());
        assertEquals(3, summary.lines().size());
        assertEquals(NoteSummaryConstants.REFER_STAGNANT_LINE + ReviewTextConstants.REFERRAL_SENTENCE,
                summary.lines().get(2));
        assertTrue(summary.lines().get(2).endsWith("つらさが続くときは、医師に相談してください。"));
    }

    @Test
    void theReferralNeverUsesAWorseningWordingEvenWhenNightsHaveGottenWorse() {
        // 悪化そのものを検出しての受診のすすめはしない。停滞の言い方だけを使う。悪化は、戻す（ROLLBACK）で扱う。
        List<NightRecord> nights = sevenBaselineNights();
        for (int offset = 7; offset <= 20; offset++) {
            nights.add(soundVariant(day(offset), "forest", WORSE_ONSET, null));
        }
        PdcaState referred = PdcaState.empty().withReferral(todayAfter(21));

        NoteSummary summary = ONSET_BUILDER.build(todayAfter(21), newestFirst(nights), referred);

        assertEquals(NoteSummaryConstants.REFER_STAGNANT_LINE + ReviewTextConstants.REFERRAL_SENTENCE,
                summary.lines().get(summary.lines().size() - 1));
    }

    @Test
    void theReferralIsHiddenWhileItsHideDayHasNotPassed() {
        PdcaState hidden = PdcaState.empty().withReferral(todayAfter(10)).withReferHiddenUntil(todayAfter(30));

        NoteSummary summary = ONSET_BUILDER.build(todayAfter(11), newestFirst(sevenBaselineNights()), hidden);

        assertFalse(summary.referralShown());
        assertFalse(summary.lines().stream().anyMatch(line -> line.contains("医師に相談")));
    }

    @Test
    void theReferralEndsAfterItsCooldownDays() {
        PdcaState old = PdcaState.empty().withReferral(todayAfter(0));

        assertTrue(NoteSummaryBuilder.isReferralShown(todayAfter(13), old), "すすめた日から、クールダウンの日数のあいだは出す");
        assertFalse(NoteSummaryBuilder.isReferralShown(todayAfter(14), old));
        assertFalse(NoteSummaryBuilder.isReferralShown(todayAfter(0).plusDays(-1), old), "すすめた日より前には出さない");
        assertFalse(NoteSummaryBuilder.isReferralShown(todayAfter(3), PdcaState.empty()), "すすめていないときは出さない");
    }

    @Test
    void hidingOutlastsALaterCooldownTriggeredReferral() {
        // 「今は表示しない」（30日）を押した後、見直しが、クールダウン（14日）を過ぎてから、すすめた日を新しくすることがある。
        // そのときも、非表示の期間（元の30日）が終わるまでは出さない。両方（クールダウンの中、かつ非表示でない）を満たしたときだけ出す。
        PdcaState hiddenThenRetriggered = PdcaState.empty()
                .withReferral(todayAfter(0))
                .withReferHiddenUntil(todayAfter(30))
                .withReferral(todayAfter(21));

        assertFalse(NoteSummaryBuilder.isReferralShown(todayAfter(22), hiddenThenRetriggered),
                "非表示の期間の中は、すすめた日が新しくなっても（クールダウンの中でも）出さない");

        PdcaState sameRetriggerWithoutHide = PdcaState.empty().withReferral(todayAfter(21));
        assertTrue(NoteSummaryBuilder.isReferralShown(todayAfter(22), sameRetriggerWithoutHide),
                "非表示にしていなければ、クールダウンの中は出す（比較のため）");

        PdcaState afterHideEnds = PdcaState.empty().withReferral(todayAfter(21)).withReferHiddenUntil(todayAfter(30));
        assertTrue(NoteSummaryBuilder.isReferralShown(todayAfter(31), afterHideEnds),
                "非表示の期間が終われば、まだクールダウンの中なら出す");
    }

    @Test
    void theSummaryNeverHasMoreThanThreeLinesAndNoForbiddenWord() {
        List<NightRecord> nights = sevenBaselineNights();
        for (int offset = 7; offset <= 11; offset++) {
            nights.add(soundVariant(day(offset), "wave", BETTER_ONSET, null));
        }
        PdcaState referred = PdcaState.empty().withReferral(todayAfter(10));

        NoteSummary summary = ONSET_BUILDER.build(todayAfter(11), newestFirst(nights), referred);

        assertEquals(3, summary.lines().size());
        for (String line : summary.lines()) {
            for (String word : List.of("目標", "基準", "実験", "対照", "スコア", "点", "改善", "効果", "悪化", "良くなった")) {
                assertFalse(line.contains(word), word + " in " + line);
            }
        }
    }

    @Test
    void theOtherGoalsUseTheirOwnNumbersAndNeverShowAMorningPoint() {
        List<NightRecord> nights = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            nights.add(standard(day(offset), NightRole.BASELINE, STANDARD_ONSET, null)
                    .withMorningResult(MorningAnswer.FIT, MorningMood.CALM));
        }

        NoteSummary morning = new NoteSummaryBuilder(NightGoal.MORNING).build(todayAfter(7), newestFirst(nights),
                PdcaState.empty());

        assertFalse(morning.lines().stream().anyMatch(line -> line.matches(".*[0-9]+点.*")), morning.lines().toString());
        assertTrue(morning.lines().stream().anyMatch(line -> line.startsWith("標準の夜の、朝のようすは、")), morning.lines().toString());
    }
}
