package app.yurune.pdca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.PdcaState;
import app.yurune.pdca.Scenarios.Scenario;
import app.yurune.pdca.SimulationRun.KeyAction;
import app.yurune.pdca.SimulationRun.KeyCheck;
import app.yurune.pdca.SimulationRun.PersonOutcome;
import app.yurune.service.FactorSequencerProbe;
import app.yurune.service.NightMetricReader;
import app.yurune.service.PairedNightDiffs;
import app.yurune.service.PairedSignFlipTest;
import app.yurune.service.VerdictJudge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 取りこぼした原因の診断。S1 で見つけられない 38.5% の人を、原因ごとに分けて数える（S2・S4・S7・S5 も同様）。
 * ここは、計測だけを足す（本体のロジックは、1 行も変えていない）。実際のサービスが残した決定の記録（Decision。
 * SimulationRun が読み取り専用で集めたもの）を読み、最後の 1 点（Bonferroni の近さ）だけ、既存の公開ユーティリティ
 * （PairedNightDiffs、VerdictJudge、PairedSignFlipTest）に、そのまま渡して確かめる。判定をやり直す場所ではない。
 *
 * 「直す」担当が、修正後にもう一度この診断を測れるよう、再利用できる形にしてある（Scenario を渡すだけで、
 * 同じ分類を、別のシナリオにも使える）。これは、アルゴリズムの検証であって、効果の主張ではない。
 */
class FailureDiagnosisTest {

    private static final Path REPORT_PATH = Path.of("target", "pdca-diagnosis-report.txt");
    private static final ArmKey WAVE = Scenarios.WAVE;
    private static final ArmKey FOREST = Scenarios.FOREST;
    private static final ArmKey FADE_15 = Scenarios.FADE_15;
    private static final ArmKey FADE_45 = Scenarios.FADE_45;
    private static final List<ArmKey> ALL_KEYS = List.of(WAVE, FOREST, FADE_15, FADE_45);
    private static final int DAYS_84 = Scenarios.DAYS_84;
    private static final int HALF_POINT_DAY = DAYS_84 / 2;
    private static final double PERCENT = 100.0;
    private static final String RULE_PREFER = "R1";
    private static final String RULE_DROP = "R3";
    private static final String RULE_REFER = "R6";
    private static final String VERDICT_NEED_MORE = "need_more";
    private static final String VERDICT_SIMILAR = "similar";
    private static final String VERDICT_WORSE = "worse";

    private static final String CAT_NEVER_CHECKED = "確認なし（要因が一度も SOUND にならなかった）";
    private static final String CAT_FEW_STILL_SOUND = "まだ NEED_MORE（ペアにできた夜が足りない。要因は SOUND のまま）";
    private static final String CAT_FEW_MOVED_FADE = "NEED_MORE のうちに、要因が FADE に進んだ（SOUND に戻らず）";
    private static final String CAT_PARKED_BONFERRONI = "SIMILAR（見送り）。補正前の p 値は通ったが、Bonferroni 補正で落ちた";
    private static final String CAT_PARKED_NOT_SIGNIFICANT = "SIMILAR（見送り）。補正前でも、有意ではない";
    private static final String CAT_PARKED_MOVED_FADE = "SIMILAR（見送り）のうちに、要因が FADE に進んだ（SOUND に戻らず）";
    private static final String CAT_DROPPED = "WORSE（外された）。ノイズで外れたまま、84 夜内に戻らなかった";
    private static final String CAT_OTHER = "その他（分類外）";

    /** 利用者 1 人ぶんの、確認・見直しの履歴つきの結果（診断専用）。 */
    private record PersonCase(PersonOutcome outcome, List<NightRecord> finalNights, PdcaState finalState) {
    }

    @Test
    void writesDiagnosisReportWithMeasuredNumbers() throws IOException {
        List<PersonCase> s1 = runWithDiagnosis(Scenarios.s1WaveShortens());
        List<PersonCase> s7 = runWithDiagnosis(Scenarios.s7MissingOnset());
        List<PersonCase> s2 = runWithDiagnosis(Scenarios.s2NoEffect());
        List<PersonCase> s4 = runWithDiagnosis(Scenarios.s4Drift());

        StringBuilder text = new StringBuilder(head());
        text.append(renderNotFoundSection("S1", "「波」が BETTER にならない 84 夜以内の人（S1）", s1, WAVE));
        text.append(renderAllocationSection("S1", s1, WAVE, FOREST));
        text.append(renderRetrySection("S1", s1, WAVE));
        text.append(renderNotFoundSection("S7", "「波」が BETTER にならない 84 夜以内の人（S7。寝つきが 50% 欠測）", s7, WAVE));
        text.append(renderFalsePositiveSection("S2", "効きのない選択肢を「よく使う」にしてしまう人（S2。PREFER=R1 の基準）", s2, true));
        text.append(renderFalsePositiveSection("S4", "ドリフトで、どれかが誤って BETTER になる人（S4。BETTER 判定そのものの基準）", s4, false));
        text.append(renderReferralOnS1Section(s1));
        writeReport(text.toString());

        assertCategoriesAreExhaustive(s1, WAVE);
        assertCategoriesAreExhaustive(s7, WAVE);
    }

    private static String head() {
        return String.join("\n",
                "# S1〜S7 で取りこぼす原因の診断（自動生成。FailureDiagnosisTest が書く）",
                "",
                "これは、アルゴリズムの検証であって、効果の主張ではない。実際の利用者のデータは使っていない。",
                "数値は、実際にシミュレータを走らせて得たもの（推測ではない）。本体のロジックは変えていない。",
                "分類は、実際のサービスが残した決定の記録（Decision）を読んで行う。Bonferroni の近さだけ、",
                "既存の公開ユーティリティ（PairedNightDiffs・VerdictJudge・PairedSignFlipTest）に、そのまま渡して確かめる。",
                "");
    }

    /** シナリオを、診断つき（finalNights・finalState を残す）で、人数ぶん走らせる。Scenarios.runAll と同じ種・条件を使う。 */
    private static List<PersonCase> runWithDiagnosis(Scenario scenario) {
        List<PersonCase> cases = new ArrayList<>();
        for (int index = 0; index < scenario.users(); index++) {
            SimulatedSleeper sleeper = new SimulatedSleeper(Scenarios.personSeed(scenario, index), scenario.model());
            SimulationRun run = new SimulationRun();
            PersonOutcome outcome = run.run(sleeper, scenario.days());
            cases.add(new PersonCase(outcome, run.finalNights(), run.finalState()));
        }
        return cases;
    }

    // ---- 「見つからない」の原因の分類（S1・S7。第3節A の1・2） ----

    private static String renderNotFoundSection(String scenarioId, String title, List<PersonCase> cases, ArmKey key) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int found = 0;
        int movedFadeButAlreadyQuarantined = 0;
        int movedFadeCases = 0;
        for (PersonCase personCase : cases) {
            if (personCase.outcome().betterDay(key) != PersonOutcome.NO_DAY) {
                found++;
                continue;
            }
            String category = classifyNotFound(personCase, key);
            counts.merge(category, 1, Integer::sum);
            if (category.equals(CAT_FEW_MOVED_FADE)) {
                movedFadeCases++;
                if (FactorSequencerProbe.isJudged(key, personCase.finalState())) {
                    movedFadeButAlreadyQuarantined++;
                }
            }
        }
        int notFound = cases.size() - found;
        StringBuilder text = new StringBuilder("## ").append(scenarioId).append("  ").append(title).append('\n');
        text.append("- 人数 ").append(cases.size()).append("、見つかった人 ").append(found)
                .append("（").append(percentText(found, cases.size())).append("）、見つからない人 ").append(notFound)
                .append("（").append(percentText(notFound, cases.size())).append("）\n");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            text.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
                    .append(" 人（見つからない人の ").append(percentText(entry.getValue(), notFound)).append("）\n");
        }
        if (movedFadeCases > 0) {
            text.append("  - 「NEED_MORE のうちに FADE へ進んだ」").append(movedFadeCases).append(" 人のうち、")
                    .append(movedFadeButAlreadyQuarantined)
                    .append(" 人は、見直しの統計的な判定（NEED_MORE）とは別に、朝の答え「合わなかった」が多数だったために"
                            + "（規則 R3、isMostlyRejected 経由）すでに隔離済みだった。隔離の記録は期限が切れても消えないため"
                            + "（FactorSequencer.isJudged）、森がもう一方の候補として判定されると、要因は FADE へ進み、"
                            + "二度と波は見直されない。\n");
        }
        return text.append('\n').toString();
    }

    private static String classifyNotFound(PersonCase personCase, ArmKey key) {
        List<KeyCheck> checks = personCase.outcome().keyChecksOf(key);
        if (checks.isEmpty()) {
            return CAT_NEVER_CHECKED;
        }
        KeyCheck last = checks.get(checks.size() - 1);
        boolean movedAway = movedToFadeAfter(personCase.outcome(), last.day());
        return switch (last.verdict()) {
            case VERDICT_NEED_MORE -> movedAway ? CAT_FEW_MOVED_FADE : CAT_FEW_STILL_SOUND;
            case VERDICT_SIMILAR -> classifyParked(key, personCase.finalNights(), movedAway);
            case VERDICT_WORSE -> CAT_DROPPED;
            default -> CAT_OTHER;
        };
    }

    private static String classifyParked(ArmKey key, List<NightRecord> finalNights, boolean movedAway) {
        if (movedAway) {
            return CAT_PARKED_MOVED_FADE;
        }
        return isBonferroniBlocked(key, finalNights) ? CAT_PARKED_BONFERRONI : CAT_PARKED_NOT_SIGNIFICANT;
    }

    /** 要因が、この選択肢の最後の確認より後に、FADE の確認を持っているか（SOUND に戻らず進んでしまった印）。 */
    private static boolean movedToFadeAfter(PersonOutcome outcome, int afterDay) {
        return lastCheckDay(outcome, FADE_15) > afterDay || lastCheckDay(outcome, FADE_45) > afterDay;
    }

    private static int lastCheckDay(PersonOutcome outcome, ArmKey key) {
        List<KeyCheck> checks = outcome.keyChecksOf(key);
        return checks.isEmpty() ? -1 : checks.get(checks.size() - 1).day();
    }

    /**
     * いまの最終的な夜の記録から、この選択肢の差を作り直し（PairedNightDiffs。本体の公開ユーティリティそのまま）、
     * 補正前の有意水準（0.05）なら通っていたはずが、Bonferroni 補正（k で割る）で落ちたかを確かめる。
     * 判定そのものは変えない。既存の計算を、同じ入力で、もう一度呼ぶだけ。
     */
    private static boolean isBonferroniBlocked(ArmKey key, List<NightRecord> finalNights) {
        List<Double> diffs = PairedNightDiffs.diffsFor(NightGoal.DEFAULT, finalNights, key);
        if (diffs.size() < GentlePolicy.MIN_NIGHTS_PER_GROUP) {
            return false;
        }
        double median = VerdictJudge.median(diffs);
        double minDiff = NightMetricReader.minDiffOf(NightGoal.DEFAULT);
        double consistency = VerdictJudge.shareAbove(diffs, 0.0);
        double pValue = PairedSignFlipTest.pValueForBetter(diffs);
        double correctedAlpha = GentlePolicy.SIGNIFICANCE_ALPHA
                / FactorSequencerProbe.nonStandardCandidatesOf(key.getDimension()).size();
        boolean passesUncorrected = median >= minDiff && consistency >= GentlePolicy.CONSISTENCY_RATIO
                && pValue <= GentlePolicy.SIGNIFICANCE_ALPHA;
        return passesUncorrected && pValue > correctedAlpha;
    }

    // ---- 配分（第3節A の1）: 「波」に、変える夜が何夜配られたか ----

    private static String renderAllocationSection(String scenarioId, List<PersonCase> cases, ArmKey key, ArmKey sibling) {
        List<Integer> waveNights = cases.stream().map(personCase -> personCase.outcome().variantNightsOf(key)).sorted().toList();
        int few = (int) waveNights.stream().filter(count -> count < GentlePolicy.MIN_NIGHTS_PER_GROUP).count();
        double meanFirstHalf = cases.stream().mapToInt(personCase -> variantNightsInRange(personCase.finalNights(), key, 0, HALF_POINT_DAY)).average().orElse(0.0);
        double meanSecondHalf = cases.stream().mapToInt(personCase -> variantNightsInRange(personCase.finalNights(), key, HALF_POINT_DAY, DAYS_84)).average().orElse(0.0);
        int prematureFade = (int) cases.stream().filter(personCase -> soundResolvedViaNightCap(personCase, key, sibling)).count();
        StringBuilder text = new StringBuilder("## ").append(scenarioId).append("  配分: 「波」に配られた、変える夜の数\n");
        text.append("- 「波」の変える夜の数（人ごと）: 中央値 ").append(median(waveNights))
                .append("、").append(GentlePolicy.MIN_NIGHTS_PER_GROUP).append(" 夜未満の人 ")
                .append(percentText(few, cases.size())).append('\n');
        text.append("- 前半（1〜").append(HALF_POINT_DAY).append(" 夜目）の平均 ")
                .append(String.format(Locale.ROOT, "%.2f", meanFirstHalf)).append(" 夜、後半（")
                .append(HALF_POINT_DAY + 1).append("〜").append(DAYS_84).append(" 夜目）の平均 ")
                .append(String.format(Locale.ROOT, "%.2f", meanSecondHalf)).append(" 夜\n");
        text.append("- SOUND が、FACTOR_MAX_VARIANT_NIGHTS（").append(GentlePolicy.FACTOR_MAX_VARIANT_NIGHTS)
                .append(" 夜）に届いたために終わり、FADE に進んだ人（「波」「森」の両方が、まだ判定済みでないのに）: ")
                .append(percentText(prematureFade, cases.size())).append('\n');
        return text.append('\n').toString();
    }

    private static int variantNightsInRange(List<NightRecord> finalNights, ArmKey key, int fromInclusive, int toExclusive) {
        List<NightRecord> sorted = finalNights.stream().sorted(Comparator.comparing(NightRecord::getWakeDate)).toList();
        int count = 0;
        for (int day = fromInclusive; day < toExclusive && day < sorted.size(); day++) {
            if (NightMetricReader.findVariantKey(sorted.get(day)).filter(key::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    /**
     * SOUND が、候補（「波」「森」）の判定が出そろう前に、変える夜の上限（FACTOR_MAX_VARIANT_NIGHTS）だけで終わり、
     * FADE に進んでしまったか。FactorSequencer.isJudged（FactorSequencerProbe を通して、本体のまま呼ぶ）で確かめる。
     */
    private static boolean soundResolvedViaNightCap(PersonCase personCase, ArmKey key, ArmKey sibling) {
        boolean keyJudged = FactorSequencerProbe.isJudged(key, personCase.finalState());
        boolean siblingJudged = FactorSequencerProbe.isJudged(sibling, personCase.finalState());
        int soundNights = personCase.outcome().variantNightsOf(key) + personCase.outcome().variantNightsOf(sibling);
        return soundNights >= GentlePolicy.FACTOR_MAX_VARIANT_NIGHTS && !(keyJudged && siblingJudged);
    }

    // ---- 再挑戦（第3節A の3）: PARK/DROP のあと、QUARANTINE_DAYS を経て、また試されるか ----

    private static String renderRetrySection(String scenarioId, List<PersonCase> cases, ArmKey key) {
        int everDropped = 0;
        int droppedTwiceOrMore = 0;
        int everParked = 0;
        for (PersonCase personCase : cases) {
            long dropCount = personCase.outcome().keyActionsOf(key).stream().filter(action -> RULE_DROP.equals(action.rule())).count();
            if (dropCount >= 1) {
                everDropped++;
            }
            if (dropCount >= 2) {
                droppedTwiceOrMore++;
            }
            if (!personCase.outcome().keyChecksOf(key).isEmpty()
                    && personCase.outcome().keyChecksOf(key).stream().anyMatch(check -> VERDICT_SIMILAR.equals(check.verdict()))) {
                everParked++;
            }
        }
        StringBuilder text = new StringBuilder("## ").append(scenarioId).append("  再挑戦: 「波」が外された（DROP）あと、また試されるか\n");
        text.append("- 一度は外された（DROP、規則 R3）人: ").append(percentText(everDropped, cases.size())).append('\n');
        text.append("- QUARANTINE_DAYS（").append(GentlePolicy.QUARANTINE_DAYS)
                .append(" 日）を経て、2 回以上外された人（再挑戦してまた外れた）: ")
                .append(percentText(droppedTwiceOrMore, cases.size())).append('\n');
        text.append("- 一度は見送り（SIMILAR、規則 R2）になった人: ").append(percentText(everParked, cases.size()))
                .append("。見送り（PARK）には、QUARANTINE_DAYS のような決まった期限が無い"
                        + "（NightReviewService.park は、state.isParked のフラグを立てるだけで、隔離の記録は作らない）。"
                        + "再び候補に戻るのは、ほかの候補も見送りか隔離になって、候補が「波」しか残らなくなったとき"
                        + "（NightRoleAssigner.eligibleOptionsNarrowed）。\n");
        return text.append('\n').toString();
    }

    // ---- 誤検出（第3節A。S2・S4） ----

    private static String renderFalsePositiveSection(String scenarioId, String title, List<PersonCase> cases, boolean requirePrefer) {
        Map<String, Integer> byKey = new LinkedHashMap<>();
        int hits = 0;
        for (PersonCase personCase : cases) {
            ArmKey falseKey = requirePrefer ? earliestPreferredKey(personCase.outcome()) : earliestBetterKey(personCase.outcome());
            if (falseKey == null) {
                continue;
            }
            hits++;
            byKey.merge(labelOf(falseKey), 1, Integer::sum);
        }
        StringBuilder text = new StringBuilder("## ").append(scenarioId).append("  ").append(title).append('\n');
        text.append("- 誤って判定された人: ").append(percentText(hits, cases.size())).append('\n');
        for (Map.Entry<String, Integer> entry : byKey.entrySet()) {
            text.append("- そのうち、選択肢「").append(entry.getKey()).append("」が最初に誤った人: ")
                    .append(percentText(entry.getValue(), hits)).append('\n');
        }
        return text.append('\n').toString();
    }

    private static ArmKey earliestBetterKey(PersonOutcome outcome) {
        ArmKey best = null;
        int bestDay = PersonOutcome.NO_DAY;
        for (ArmKey key : ALL_KEYS) {
            int day = outcome.betterDay(key);
            if (day < bestDay) {
                bestDay = day;
                best = key;
            }
        }
        return best;
    }

    private static ArmKey earliestPreferredKey(PersonOutcome outcome) {
        ArmKey best = null;
        int bestDay = PersonOutcome.NO_DAY;
        for (ArmKey key : ALL_KEYS) {
            for (KeyAction action : outcome.keyActionsOf(key)) {
                if (RULE_PREFER.equals(action.rule()) && action.day() < bestDay) {
                    bestDay = action.day();
                    best = key;
                }
            }
        }
        return best;
    }

    // ---- S5: S1 の人（効く選択肢がある）への、誤った受診のすすめ ----

    /**
     * 受診のすすめ（R6）が出た S1 の人を、見つかっていたか・「よく使う」まで届いていたかで分ける。
     * 「一度 BETTER になったが、2 週連続に届かず『よく使う』にならなかった」は、S1 の生の指標（BETTER）には関係しないが、
     * ここ（受診のすすめの誤検出）には直結する（reviewReferral は state.getPreferred() が空のときだけ働くため）。
     */
    private static String renderReferralOnS1Section(List<PersonCase> cases) {
        int referred = 0;
        int neverFoundBetter = 0;
        int foundButNeverPreferred = 0;
        int preferredBeforeReferral = 0;
        int preferredBeforeReferralButRevoked = 0;
        for (PersonCase personCase : cases) {
            PersonOutcome outcome = personCase.outcome();
            int referDay = outcome.ruleDay(RULE_REFER);
            if (referDay >= DAYS_84) {
                continue;
            }
            referred++;
            int betterDay = outcome.betterDay(WAVE);
            int preferDay = outcome.ruleDay(RULE_PREFER);
            if (betterDay == PersonOutcome.NO_DAY) {
                neverFoundBetter++;
            } else if (preferDay == PersonOutcome.NO_DAY || preferDay > referDay) {
                foundButNeverPreferred++;
            }
            if (preferDay != PersonOutcome.NO_DAY && preferDay <= referDay) {
                preferredBeforeReferral++;
                if (personCase.finalState().getPreferred().isEmpty()) {
                    preferredBeforeReferralButRevoked++;
                }
            }
        }
        StringBuilder text = new StringBuilder("## S5  S1 の人への、誤った受診のすすめ（84 夜以内）の内訳\n");
        text.append("- 受診のすすめが出た人: ").append(percentText(referred, cases.size())).append('\n');
        text.append("- そのうち、「波」が一度も BETTER にならなかった人（S1 自体の取りこぼしと重なる）: ")
                .append(percentText(neverFoundBetter, referred)).append('\n');
        text.append("- そのうち、「波」は BETTER になったが、受診のすすめが出るまでに『よく使う』（2 週連続。R1）に届かなかった人: ")
                .append(percentText(foundButNeverPreferred, referred)).append('\n');
        text.append("- 『よく使う』の判定の後に、受診のすすめが出た人: ").append(preferredBeforeReferral)
                .append(" 件（R6 は state.getPreferred() が空のときだけ動くので、一見、整合しないように見えるが、そのうち")
                .append(preferredBeforeReferralButRevoked)
                .append(" 件は、84 夜目には『よく使う』の印が外れている。よく使うと決めた選択肢が、後の見直しで WORSE と判定され、"
                        + "規則 R3（外す）が、よく使う・見送りの印を両方消す仕様のため [PdcaState.withQuarantine]。"
                        + "バグではなく、仕様どおりの「一度よく使うにしたものが、後で外れた」挙動）\n");
        return text.append('\n').toString();
    }

    // ---- 共通の道具 ----

    private static void assertCategoriesAreExhaustive(List<PersonCase> cases, ArmKey key) {
        for (PersonCase personCase : cases) {
            if (personCase.outcome().betterDay(key) != PersonOutcome.NO_DAY) {
                continue;
            }
            String category = classifyNotFound(personCase, key);
            assertTrue(!category.isBlank(), "分類が空になった");
        }
        assertEquals(200, cases.size(), "診断は、既存のシナリオの人数（USERS）をそのまま使う");
    }

    private static String labelOf(ArmKey key) {
        return key.getDimension().asText() + ":" + key.getOptionName().asText();
    }

    private static String median(List<Integer> sortedValues) {
        if (sortedValues.isEmpty()) {
            return "なし";
        }
        int middle = sortedValues.size() / 2;
        double value = sortedValues.size() % 2 == 1 ? sortedValues.get(middle)
                : (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0;
        return String.format(Locale.ROOT, "%.1f 夜", value);
    }

    private static String percentText(long hits, int total) {
        double ratio = total == 0 ? 0.0 : PERCENT * hits / total;
        return String.format(Locale.ROOT, "%.1f%%（%d/%d 人）", ratio, hits, total);
    }

    private static void writeReport(String text) throws IOException {
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, text, StandardCharsets.UTF_8);
    }
}
