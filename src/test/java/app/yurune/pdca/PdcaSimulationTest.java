package app.yurune.pdca;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.yurune.pdca.Scenarios.Judgement;
import app.yurune.pdca.Scenarios.Scenario;
import app.yurune.pdca.Scenarios.Section;
import app.yurune.pdca.SimulationRun.PersonOutcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * シナリオ S1〜S8（と、保留のシナリオ）を、実際のサービスで回す。受け入れ基準の数字は、target/pdca-sim-report.txt に書く。
 * 基準を満たさないシナリオは、テストを落とさず、数字として報告する（基準は変えない）。
 * 夜が進まない、または、確認の夜数が規則どおりに数えた値と合わないときは、シミュレータか規則の計算が壊れているので、落とす。
 */
class PdcaSimulationTest {

    private static final Map<String, List<PersonOutcome>> RESULTS = new LinkedHashMap<>();
    private static final Path REPORT_PATH = Path.of("target", "pdca-sim-report.txt");
    private static final String REPORT_HEAD = String.join("\n",
            "# シミュレータ S1〜S8 と、検出力の曲線、保留のシナリオの結果（自動生成。PdcaSimulationTest が書く）",
            "",
            "これは、アルゴリズムの検証であって、効果の主張ではない。実際の利用者のデータは使っていない。",
            "数値はすべて仮置き（SimulatedSleeper と Scenarios の定数）。受け入れ基準は、あらかじめ置いた仮の数値をそのまま使い、結果に合わせて変えていない。",
            "この文書の語（基準、対照、判定など）は、技術的な報告の語であり、画面に出す文言ではない。",
            "S5 の「S1 の利用者」は、S1 と同じ種で、音「波」の効きを外した世界（S1-NOEFF）も、参考に示す。基準の判定は、効きのある S1 の世界で行った。",
            "保留のシナリオ（HO1〜HO4）は、過学習を避けるための確認で、基準に数えない。設計やしきい値は、これらの結果を見て調整していない。",
            "");

    @Test
    void s1WaveShortensOnsetBetterIsFoundInEightyFourNights() {
        assertScenario(Scenarios.s1WaveShortens());
    }

    @Test
    void s2NoEffectRarelyPreferred() {
        assertScenario(Scenarios.s2NoEffect());
    }

    @Test
    void s3ForestLengthensOnsetIsDroppedInFortyTwoNights() {
        assertScenario(Scenarios.s3ForestLengthens());
    }

    @Test
    void s4DriftDoesNotMakeNoEffectOptionBetter() {
        assertScenario(Scenarios.s4Drift());
    }

    @Test
    void s5StagnantGetsReferral() {
        assertScenario(Scenarios.s5Stagnant());
    }

    @Test
    void s6WorseningGetsRolledBack() {
        assertScenario(Scenarios.s6Worsening());
    }

    @Test
    void s7MissingOnsetProxyStillFindsBetter() {
        assertScenario(Scenarios.s7MissingOnset());
    }

    @Test
    void s8ChangedNightsAreExcludedFromComparisons() {
        assertScenario(Scenarios.s8ChangedNights());
    }

    @Test
    void supplementaryRunForStagnantComparison() {
        assertScenario(Scenarios.s1SeedNoEffect());
    }

    /** 音量の下げ方（FADE）が、実際に寝つきへ効くことの確認。 */
    @Test
    void s9FadeAffectsOnsetDetection() {
        assertScenario(Scenarios.s9FadeAffectsOnset());
    }

    @Test
    void holdoutScenariosDoNotBreakTheQualitativeBehaviour() {
        for (Scenario scenario : Scenarios.holdoutScenarios()) {
            assertScenario(scenario);
        }
    }

    /** 実行と整合の確かめ。基準の判定は、報告で示す（テストは落とさない）。 */
    private static List<PersonOutcome> assertScenario(Scenario scenario) {
        List<PersonOutcome> outcomes = RESULTS.computeIfAbsent(scenario.id(), id -> Scenarios.runAll(scenario));
        for (PersonOutcome outcome : outcomes) {
            assertEquals(scenario.days(), outcome.endedNights(), "夜が進まない: " + scenario.id());
            assertEquals(0, outcome.checkMismatches(), "確認の夜数が、規則どおりに数えた値と合わない: " + scenario.id());
        }
        return outcomes;
    }

    @AfterAll
    static void writeReport() {
        List<Section> sections = new ArrayList<>();
        for (Scenario scenario : Scenarios.allScenarios()) {
            Scenarios.judge(scenario, RESULTS).ifPresent(sections::add);
        }
        StringBuilder text = new StringBuilder(REPORT_HEAD);
        text.append(Scenarios.powerCurveText(RESULTS.get("S1")));
        for (Section section : sections) {
            text.append(Scenarios.render(section));
        }
        text.append(summaryTable(sections));
        try {
            Files.createDirectories(REPORT_PATH.getParent());
            Files.writeString(REPORT_PATH, text.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("the simulation report could not be written: " + exception.getMessage());
        }
    }

    private static String summaryTable(List<Section> sections) {
        StringBuilder table = new StringBuilder("## 一覧\n\n| 節 | 判定 | 数字 | 基準 | 満たしたか |\n| --- | --- | --- | --- | --- |\n");
        for (Section section : sections) {
            for (Judgement judgement : section.judgements()) {
                table.append("| ").append(section.scenario().id()).append(" | ").append(judgement.label())
                        .append(" | ").append(judgement.measured()).append(" | ").append(judgement.criterion())
                        .append(" | ").append(judgement.passed() ? "はい" : "いいえ").append(" |\n");
            }
        }
        return table.toString();
    }
}
