package app.yurune.pdca;

import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;
import app.yurune.pdca.SimulatedSleeper.SleepModel;
import app.yurune.pdca.SimulationRun.PersonOutcome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * シナリオ S1〜S8（と、過学習を避けるための保留のシナリオ）の定義、新しい受け入れ基準の判定、報告の文。
 * 基準は、あらかじめ置いた仮の数値をそのまま使う。結果に合わせて基準を変えない。
 * これはアルゴリズムの検証であって、効果の主張ではない。
 */
final class Scenarios {

    static final int USERS = 200;
    /** 保留のシナリオ（過学習を避ける確認）は、本体の基準に数えないので、計算の時間を抑えるため人数を減らす。仮置き。 */
    static final int HOLDOUT_USERS = 100;
    static final int PERSON_SEED_STRIDE = 1000;
    static final int DAYS_14 = 14;
    static final int DAYS_21 = 21;
    static final int DAYS_28 = 28;
    static final int DAYS_42 = 42;
    static final int DAYS_56 = 56;
    static final int DAYS_70 = 70;
    static final int DAYS_84 = 84;
    /**
     * S9（FADE 要因）専用の夜数。要因は音（SOUND）を先に試す（FactorSequencer。R3）ので、
     * 音量の下げ方（FADE）の見直しは、音の見直しのあとに回ってくる。84 夜（S1 と同じ）では、音の見直しに
     * 予算の大半を使われてしまい、FADE の変える夜がほとんど無いまま終わる（試した結果、84 夜・140 夜・250 夜で
     * 数字がほぼ変わらなかった）。140 夜にしたのは、それでも
     * 多少は FADE の変える夜が増える範囲で、これ以上延ばしても数字が変わらないと確かめた、仮置きの値。
     */
    static final int DAYS_140 = 140;
    static final List<Integer> POWER_CURVE_CHECKPOINTS = List.of(DAYS_14, DAYS_28, DAYS_42, DAYS_56, DAYS_70, DAYS_84);

    static final String RULE_PREFER = "R1";
    static final String RULE_DROP = "R3";
    static final String RULE_ROLLBACK = "R4";
    static final String RULE_REFER = "R6";

    static final ArmKey WAVE = new ArmKey(Dimension.SOUND, new OptionName("wave"));
    static final ArmKey FOREST = new ArmKey(Dimension.SOUND, new OptionName("forest"));
    static final ArmKey FADE_15 = new ArmKey(Dimension.FADE, new OptionName("15"));
    static final ArmKey FADE_45 = new ArmKey(Dimension.FADE, new OptionName("45"));
    static final double WAVE_EFFECT_MIN = -15.0;
    static final double FOREST_EFFECT_MIN = 15.0;
    /**
     * S9（FADE 要因）の仮置きの効果。RuleBasedNightAgent.decideFade の実装（開始音量から下限まで、FADE の
     * 分数をかけて直線に下げる）から見て、FADE が短い（15 分）ほど、早く下限まで下がる＝寝つきへの効きが大きい、
     * FADE が長い（45 分）ほど、下がり方がゆるやか＝効きが小さい、という向きにする（仮置き。根拠なし。
     * S1 の WAVE_EFFECT_MIN と同じ大きさを、FADE 15 分にも使い、同じ検出力で比べられるようにする）。
     */
    static final double FADE_15_EFFECT_MIN = -15.0;
    static final double FADE_45_EFFECT_MIN = -5.0;
    static final double DRIFT_IMPROVING_MIN_PER_DAY = -0.3;
    static final double DRIFT_WORSENING_MIN_PER_DAY = 0.3;
    static final double USER_ENDS_RATIO_S7 = 0.5;
    static final double CHANGE_RATIO = 0.3;
    static final double FIT_BASE = 0.35;

    static final double BETTER_SHARE_MIN_S1 = 0.70;
    static final double BETTER_SHARE_MIN_S7 = 0.50;
    static final double PREFER_SHARE_MAX = 0.10;
    static final double QUARANTINE_SHARE_MIN_S3 = 0.80;
    static final double FOREST_NIGHTS_MAX = 8.0;
    static final double ANY_BETTER_SHARE_MAX_S4 = 0.15;
    static final double REFER_SHARE_MIN_S5 = 0.80;
    static final double REFER_S1_SHARE_MAX = 0.10;
    static final double ROLLBACK_SHARE_MIN_S6 = 0.80;
    /**
     * S9（FADE 要因）。S1（音。84 夜、同じ大きさの効果）の基準（70%）とは、大きく違う（低い）値を仮に置く。
     * 要因を順番に試す規則（R3、FactorSequencer）により、FADE は音のあとに回ってくるため、同じ夜数では、
     * FADE の変える夜が、音よりずっと少ない（シミュレータで確かめた数字: 音 1 選択肢あたり平均 9〜10 夜、
     * FADE 1 選択肢あたり平均 2〜3 夜）。この基準は「死んだ要因でないことの確認」であり、S1 のような
     * 検出力の目標ではない（仮置き。根拠なし）。
     */
    static final double BETTER_SHARE_MIN_S9 = 0.05;
    static final double PERCENT = 100.0;
    static final double ONE_NIGHT = 1.0;

    static final long SEED_S1 = 1001L;
    static final long SEED_S2 = 1002L;
    static final long SEED_S3 = 1003L;
    static final long SEED_S4 = 1004L;
    static final long SEED_S5 = 1005L;
    static final long SEED_S6 = 1006L;
    static final long SEED_S8 = 1008L;
    static final long SEED_S9 = 1009L;
    static final long SEED_HO1 = 2001L;
    static final long SEED_HO2 = 2002L;
    static final long SEED_HO3 = 2003L;
    static final long SEED_HO4 = 2004L;

    private static final String NO_VALUE = "なし";

    private Scenarios() {
    }

    /** シナリオ（ID、題、夜数、種、利用者の条件）。 */
    record Scenario(String id, String title, int days, long seed, SleepModel model, int users) {
    }

    /** 基準の判定 1 件。passed は、基準を満たしたか。 */
    record Judgement(String label, String measured, String criterion, boolean passed) {
    }

    /** 報告の 1 節。 */
    record Section(Scenario scenario, List<Judgement> judgements, List<String> facts) {
    }

    // ---- S1〜S8 ----

    static Scenario s1WaveShortens() {
        return new Scenario("S1", "音「波」が、寝つきを 15 分短くする", DAYS_84, SEED_S1,
                model(FIT_BASE, 0.0, 0.0, 0.0, Map.of(WAVE, WAVE_EFFECT_MIN)), USERS);
    }

    static Scenario s2NoEffect() {
        return new Scenario("S2", "どの選択肢も、効きがない", DAYS_84, SEED_S2, model(FIT_BASE, 0.0, 0.0, 0.0, Map.of()), USERS);
    }

    static Scenario s3ForestLengthens() {
        return new Scenario("S3", "音「森」が、寝つきを 15 分わるくする", DAYS_84, SEED_S3,
                model(FIT_BASE, 0.0, 0.0, 0.0, Map.of(FOREST, FOREST_EFFECT_MIN)), USERS);
    }

    static Scenario s4Drift() {
        return new Scenario("S4", "全部の夜が、少しずつ早くなる（0.3 分/日）", DAYS_84, SEED_S4,
                model(FIT_BASE, 0.0, 0.0, DRIFT_IMPROVING_MIN_PER_DAY, Map.of()), USERS);
    }

    static Scenario s5Stagnant() {
        return new Scenario("S5", "84 夜、変化なし（停滞）", DAYS_84, SEED_S5, model(FIT_BASE, 0.0, 0.0, 0.0, Map.of()), USERS);
    }

    static Scenario s6Worsening() {
        return new Scenario("S6", "徐々に、わるくなる（0.3 分/日）", DAYS_42, SEED_S6,
                model(FIT_BASE, 0.0, 0.0, DRIFT_WORSENING_MIN_PER_DAY, Map.of()), USERS);
    }

    static Scenario s7MissingOnset() {
        return new Scenario("S7", "S1 と同じで、寝つきのめやすが 50% 欠ける（利用者が自分で終える夜）", DAYS_84, SEED_S1,
                modelWithUserEnds(FIT_BASE, 0.0, Map.of(WAVE, WAVE_EFFECT_MIN), USER_ENDS_RATIO_S7), USERS);
    }

    static Scenario s8ChangedNights() {
        return new Scenario("S8", "利用者が自分で変える夜が 30%（S1 の条件）", DAYS_84, SEED_S8,
                model(FIT_BASE, CHANGE_RATIO, 0.0, 0.0, Map.of(WAVE, WAVE_EFFECT_MIN)), USERS);
    }

    /** 補足。S5 の「S1 の利用者」を、S1 と同じ種で、効きを外した世界として比べるための実行。 */
    static Scenario s1SeedNoEffect() {
        return new Scenario("S1-NOEFF", "S1 と同じ種で、音「波」の効きを外した世界（比べる用）", DAYS_84, SEED_S1,
                model(FIT_BASE, 0.0, 0.0, 0.0, Map.of()), USERS);
    }

    /**
     * S9: 音量の下げ方（FADE）が、実際に寝つきへ効く場面。
     * 音（SOUND）には効果を置かない（S1〜S8 の既存の数字に影響しないため、かつ、音の見直しだけで要因が
     * 終わらないようにして、FADE の見直しに夜数が回るようにするため）。FADE 15 分の効果を、S1 の音「波」と
     * 同じ大きさにし、FADE 45 分は、より緩やかな効果にする（RuleBasedNightAgent.decideFade の、
     * 直線のフェードという実装から導いた向き。仮置き）。
     */
    static Scenario s9FadeAffectsOnset() {
        return new Scenario("S9", "音量の下げ方「15分」が、寝つきを15分短くする。「45分」は、緩やかに5分短くする", DAYS_140, SEED_S9,
                model(FIT_BASE, 0.0, 0.0, 0.0, Map.of(FADE_15, FADE_15_EFFECT_MIN, FADE_45, FADE_45_EFFECT_MIN)), USERS);
    }

    // ---- 保留のシナリオ（過学習を避ける確認）。基準には数えない、参考の実行 ----

    static Scenario ho1SmallEffectHighSpread() {
        return new Scenario("HO1", "保留: 効果 10 分・ばらつき 25 分", DAYS_84, SEED_HO1,
                modelWithSpread(FIT_BASE, Map.of(WAVE, -10.0), 25.0), HOLDOUT_USERS);
    }

    static Scenario ho2LargeEffectLowSpread() {
        return new Scenario("HO2", "保留: 効果 20 分・ばらつき 15 分", DAYS_84, SEED_HO2,
                modelWithSpread(FIT_BASE, Map.of(WAVE, -20.0), 15.0), HOLDOUT_USERS);
    }

    static Scenario ho3NoEffectHighSpread() {
        return new Scenario("HO3", "保留: 効果なし・ばらつき 30 分", DAYS_84, SEED_HO3,
                modelWithSpread(FIT_BASE, Map.of(), 30.0), HOLDOUT_USERS);
    }

    static Scenario ho4SteeperDrift() {
        return new Scenario("HO4", "保留: ドリフト 0.5 分/日", DAYS_84, SEED_HO4,
                model(FIT_BASE, 0.0, 0.0, 0.5, Map.of()), HOLDOUT_USERS);
    }

    static List<Scenario> mainScenarios() {
        return List.of(s1WaveShortens(), s2NoEffect(), s3ForestLengthens(), s4Drift(), s5Stagnant(),
                s6Worsening(), s7MissingOnset(), s8ChangedNights(), s1SeedNoEffect(), s9FadeAffectsOnset());
    }

    static List<Scenario> holdoutScenarios() {
        return List.of(ho1SmallEffectHighSpread(), ho2LargeEffectLowSpread(), ho3NoEffectHighSpread(), ho4SteeperDrift());
    }

    static List<Scenario> allScenarios() {
        List<Scenario> all = new ArrayList<>(mainScenarios());
        all.addAll(holdoutScenarios());
        return all;
    }

    /** 既存のシグネチャ（SimulatedSleeperTest から使う）。欠ける夜の割合（R7）とばらつき（保留のシナリオ）は既定値。 */
    static SleepModel model(double fitBase, double changeRatio, double missingRatio, double drift,
            Map<ArmKey, Double> effects) {
        return new SleepModel(fitBase, changeRatio, missingRatio, drift, effects, 0.0, SimulatedSleeper.NIGHT_SD_MIN);
    }

    /** R7: 寝つきのめやすが欠ける割合（利用者が自分で終える夜）を指定する版。 */
    static SleepModel modelWithUserEnds(double fitBase, double changeRatio, Map<ArmKey, Double> effects, double userEndsRatio) {
        return new SleepModel(fitBase, changeRatio, 0.0, 0.0, effects, userEndsRatio, SimulatedSleeper.NIGHT_SD_MIN);
    }

    /** 保留のシナリオ: 夜ごとのばらつき（SD）を変える版。 */
    static SleepModel modelWithSpread(double fitBase, Map<ArmKey, Double> effects, double nightSdMin) {
        return new SleepModel(fitBase, 0.0, 0.0, 0.0, effects, 0.0, nightSdMin);
    }

    /** 利用者 users 人を、同じ条件で回す。人の種は、シナリオの種と人番号から決まる。 */
    static List<PersonOutcome> runAll(Scenario scenario) {
        List<PersonOutcome> outcomes = new ArrayList<>();
        for (int index = 0; index < scenario.users(); index++) {
            SimulatedSleeper sleeper = new SimulatedSleeper(personSeed(scenario, index), scenario.model());
            outcomes.add(new SimulationRun().run(sleeper, scenario.days()));
        }
        return outcomes;
    }

    static long personSeed(Scenario scenario, int index) {
        return scenario.seed() * PERSON_SEED_STRIDE + index;
    }

    /** 基準の判定と、報告の数字を作る。results は、シナリオの ID から、利用者の結果への対応。 */
    static Optional<Section> judge(Scenario scenario, Map<String, List<PersonOutcome>> results) {
        List<PersonOutcome> outcomes = results.get(scenario.id());
        if (outcomes == null) {
            return Optional.empty();
        }
        List<Judgement> judgements = switch (scenario.id()) {
            case "S1" -> judgeBetter(outcomes, "音「波」が BETTER と判定された人（84 夜以内）", DAYS_84, BETTER_SHARE_MIN_S1);
            case "S2" -> judgePreferNoEffect(outcomes);
            case "S3" -> judgeForest(outcomes);
            case "S4" -> judgeAnyBetter(outcomes);
            case "S5" -> judgeStagnant(outcomes, results.get("S1"));
            case "S6" -> judgeWorsening(outcomes);
            case "S7" -> judgeBetter(outcomes, "音「波」が BETTER と判定された人（84 夜以内、寝つきのめやすが 50% 欠ける）",
                    DAYS_84, BETTER_SHARE_MIN_S7);
            case "S8" -> judgeExclusion(outcomes);
            case "S9" -> judgeFade(outcomes);
            default -> judgeHoldout(scenario, outcomes);
        };
        return Optional.of(new Section(scenario, judgements, facts(scenario, outcomes)));
    }

    private static List<Judgement> judgeBetter(List<PersonOutcome> outcomes, String label, int days, double minShare) {
        long hits = count(outcomes, outcome -> outcome.betterDay(WAVE) < days);
        return List.of(judgement(label, hits, outcomes.size(), ">= " + percentOf(minShare), ratio(hits, outcomes.size()) >= minShare));
    }

    private static List<Judgement> judgePreferNoEffect(List<PersonOutcome> outcomes) {
        long hits = count(outcomes, outcome -> outcome.ruleDay(RULE_PREFER) < DAYS_84);
        return List.of(judgement("効きのない選択肢を「よく使う」にした人（84 夜以内）", hits, outcomes.size(),
                "<= " + percentOf(PREFER_SHARE_MAX), ratio(hits, outcomes.size()) <= PREFER_SHARE_MAX));
    }

    private static List<Judgement> judgeForest(List<PersonOutcome> outcomes) {
        long hits = count(outcomes, outcome -> outcome.quarantineDay(FOREST) < DAYS_42);
        double meanForest = mean(outcomes, outcome -> outcome.variantNightsOf(FOREST));
        return List.of(
                judgement("音「森」を外せた人（42 夜以内。外す、または戻す）", hits, outcomes.size(),
                        ">= " + percentOf(QUARANTINE_SHARE_MIN_S3), ratio(hits, outcomes.size()) >= QUARANTINE_SHARE_MIN_S3),
                new Judgement("音「森」を変えた夜の平均（84 夜のうち）", String.format(Locale.ROOT, "%.2f 夜", meanForest),
                        "<= " + FOREST_NIGHTS_MAX + " 夜", meanForest <= FOREST_NIGHTS_MAX));
    }

    private static List<Judgement> judgeAnyBetter(List<PersonOutcome> outcomes) {
        long hits = count(outcomes, outcome -> outcome.anyBetterDay() < DAYS_84);
        return List.of(judgement("どれかの選択肢が BETTER と判定された人（84 夜以内）", hits, outcomes.size(),
                "<= " + percentOf(ANY_BETTER_SHARE_MAX_S4), ratio(hits, outcomes.size()) <= ANY_BETTER_SHARE_MAX_S4));
    }

    private static List<Judgement> judgeStagnant(List<PersonOutcome> outcomes, List<PersonOutcome> s1World) {
        long hits = count(outcomes, outcome -> outcome.ruleDay(RULE_REFER) < DAYS_84);
        List<Judgement> judgements = new ArrayList<>();
        judgements.add(judgement("受診のすすめ（R6）が出た人（84 夜以内）", hits, outcomes.size(),
                ">= " + percentOf(REFER_SHARE_MIN_S5), ratio(hits, outcomes.size()) >= REFER_SHARE_MIN_S5));
        if (s1World != null) {
            long s1Hits = count(s1World, outcome -> outcome.ruleDay(RULE_REFER) < DAYS_84);
            judgements.add(judgement("S1 の利用者に受診のすすめ（R6）が出た人（84 夜以内）", s1Hits, s1World.size(),
                    "<= " + percentOf(REFER_S1_SHARE_MAX), ratio(s1Hits, s1World.size()) <= REFER_S1_SHARE_MAX));
        }
        return judgements;
    }

    /** 基準は「21 夜以内に戻すが働いた」。受診のすすめは、R6 から悪化の検出を外したので、基準には数えない（参考だけ示す）。 */
    private static List<Judgement> judgeWorsening(List<PersonOutcome> outcomes) {
        long hits = count(outcomes, outcome -> outcome.ruleDay(RULE_ROLLBACK) < DAYS_21);
        long referHits = count(outcomes, outcome -> outcome.ruleDay(RULE_REFER) < DAYS_42);
        return List.of(
                judgement("戻す（R4）が働いた人（21 夜以内）", hits, outcomes.size(),
                        ">= " + percentOf(ROLLBACK_SHARE_MIN_S6), ratio(hits, outcomes.size()) >= ROLLBACK_SHARE_MIN_S6),
                new Judgement("（参考）受診のすすめ（R6）も出た人（42 夜以内。基準には数えない）",
                        percentText(referHits, outcomes.size()), "参考（基準なし）", true));
    }

    /**
     * S9（FADE 要因）の基準: (1) FADE 15 分が BETTER と判定される人が、S1 の音「波」と同じ基準で見つかる。
     * (2) FADE 45 分（より緩やかな効果）の BETTER の割合は、FADE 15 分より低い（基準の判定には数えない。
     * RuleBasedNightAgent.decideFade の直線のフェードの実装から見て、下げ方が緩やかな方は、効きも小さいはず、
     * という向きの確認。参考として示す）。
     */
    private static List<Judgement> judgeFade(List<PersonOutcome> outcomes) {
        long fade15Hits = count(outcomes, outcome -> outcome.betterDay(FADE_15) < DAYS_140);
        long fade45Hits = count(outcomes, outcome -> outcome.betterDay(FADE_45) < DAYS_140);
        double fade15Share = ratio(fade15Hits, outcomes.size());
        double fade45Share = ratio(fade45Hits, outcomes.size());
        return List.of(
                judgement("FADE「15分」が BETTER と判定された人（140 夜以内）", fade15Hits, outcomes.size(),
                        ">= " + percentOf(BETTER_SHARE_MIN_S9), fade15Share >= BETTER_SHARE_MIN_S9),
                new Judgement("（参考）FADE「45分」が BETTER と判定された人（140 夜以内。より緩やかな効果）",
                        percentText(fade45Hits, outcomes.size()),
                        "< FADE「15分」の割合（" + percentOf(fade15Share) + "）。緩やかな方は、効きの検出も弱いはず",
                        fade45Share < fade15Share));
    }

    private static List<Judgement> judgeExclusion(List<PersonOutcome> outcomes) {
        int mismatches = sum(outcomes, PersonOutcome::checkMismatches);
        int excluded = sum(outcomes, PersonOutcome::excludedVariantNights);
        long changedNights = sum(outcomes, PersonOutcome::userChangedNights);
        long allNights = sum(outcomes, PersonOutcome::endedNights);
        return List.of(
                new Judgement("確認の夜数（ペアにできた数）が、記録から数え直した値と一致した（照合のずれ）",
                        mismatches + " 件のずれ", "0 件", mismatches == 0),
                new Judgement("利用者が変えた夜のうち、変えた選択肢の夜（判定から外れた数）",
                        excluded + " 夜（全 " + allNights + " 夜のうち、利用者が変えた夜 " + changedNights + " 夜）",
                        "1 夜以上（外れることを確かめた）", excluded > 0));
    }

    /** 保留のシナリオ（基準なし）。どれかの選択肢が BETTER になった割合だけを、参考として示す。 */
    private static List<Judgement> judgeHoldout(Scenario scenario, List<PersonOutcome> outcomes) {
        long hits = count(outcomes, outcome -> outcome.anyBetterDay() < scenario.days());
        return List.of(new Judgement("どれかの選択肢が BETTER と判定された人（" + scenario.days() + " 夜以内）。参考（基準なし。設計の調整はしていない）",
                percentText(hits, outcomes.size()), "参考（基準なし）", true));
    }

    /** S1 の検出力の曲線（夜数ごとの、累積で BETTER と判定された割合）。 */
    static String powerCurveText(List<PersonOutcome> s1Outcomes) {
        if (s1Outcomes == null) {
            return "";
        }
        StringBuilder text = new StringBuilder("## S1 の検出力の曲線（音「波」が、各夜数の時点までに、累積で BETTER と判定された人の割合）\n\n");
        text.append("| 夜数 | 割合 |\n| --- | --- |\n");
        for (int checkpoint : POWER_CURVE_CHECKPOINTS) {
            long hits = count(s1Outcomes, outcome -> outcome.betterDay(WAVE) < checkpoint);
            text.append("| ").append(checkpoint).append(" | ").append(percentText(hits, s1Outcomes.size())).append(" |\n");
        }
        return text.append('\n').toString();
    }

    private static List<String> facts(Scenario scenario, List<PersonOutcome> outcomes) {
        String id = scenario.id();
        List<String> facts = new ArrayList<>();
        long anyBetter = count(outcomes, outcome -> outcome.anyBetterDay() < scenario.days());
        facts.add("どれかの選択肢が BETTER と判定された人（" + scenario.days() + " 夜以内）: "
                + percentText(anyBetter, outcomes.size()));
        facts.add("夜数（終わった夜の平均）: " + meanText(outcomes, PersonOutcome::endedNights));
        facts.add("変えた夜（音と音量の下げ方の、標準と違う夜）の平均: "
                + meanText(outcomes, PersonOutcome::variantNightsTotal));
        facts.add("選択肢ごとの変えた夜（人あたりの平均）: 音「波」 " + meanText(outcomes, o -> o.variantNightsOf(WAVE))
                + "、音「森」 " + meanText(outcomes, o -> o.variantNightsOf(FOREST))
                + "、音量 15 分 " + meanText(outcomes, o -> o.variantNightsOf(FADE_15))
                + "、音量 45 分 " + meanText(outcomes, o -> o.variantNightsOf(FADE_45)));
        facts.add("標準の夜（基準と対照）の平均: " + meanText(outcomes, PersonOutcome::controlNights)
                + "（対照）、基準の夜（有効な夜）の平均: " + meanText(outcomes, PersonOutcome::validBaselineNights));
        facts.add("寝つきのめやすが欠けた夜（利用者が自分で終えた夜）の平均: " + meanText(outcomes, PersonOutcome::missingAnswers));
        facts.add("利用者が会話へ切り替えた夜の平均: " + meanText(outcomes, PersonOutcome::userChangedNights));
        facts.add("確認の判定（夜の数の合計）: BETTER " + sum(outcomes, o -> o.verdictCount("better"))
                + "、WORSE " + sum(outcomes, o -> o.verdictCount("worse"))
                + "、SIMILAR " + sum(outcomes, o -> o.verdictCount("similar"))
                + "、NEED_MORE " + sum(outcomes, o -> o.verdictCount("need_more"))
                + "。判定のうち BETTER の割合（NEED_MORE を除く）: " + betterShareText(outcomes));
        facts.add("規則の発火（人あたりの平均）: R1 " + meanText(outcomes, o -> o.ruleCount(RULE_PREFER))
                + "、R3 " + meanText(outcomes, o -> o.ruleCount(RULE_DROP))
                + "、R4 " + meanText(outcomes, o -> o.ruleCount(RULE_ROLLBACK))
                + "、R6 " + meanText(outcomes, o -> o.ruleCount(RULE_REFER)));
        facts.add(detailFact(id, outcomes));
        return facts;
    }

    private static String detailFact(String id, List<PersonOutcome> outcomes) {
        return switch (id) {
            case "S1", "S7" -> "音「波」の BETTER の初回（夜の番号、1 始まり。達成した人の中央値）: "
                    + medianText(outcomes, o -> o.betterDay(WAVE));
            case "S3" -> "音「森」を外した初回（夜の番号、1 始まり。外せた人の中央値）: "
                    + medianText(outcomes, o -> o.quarantineDay(FOREST));
            case "S5" -> "R6 の初回（夜の番号、1 始まり。出た人の中央値）: " + medianText(outcomes, o -> o.ruleDay(RULE_REFER));
            case "S6" -> "R4 の初回（夜の番号、1 始まり。働いた人の中央値）: " + medianText(outcomes, o -> o.ruleDay(RULE_ROLLBACK));
            case "S8" -> "基準の夜（有効な夜）の数: " + meanText(outcomes, PersonOutcome::validBaselineNights)
                    + "（基準の夜の総数 " + meanText(outcomes, PersonOutcome::baselineNights) + "）";
            case "S9" -> "FADE「15分」の BETTER の初回（夜の番号、1 始まり。達成した人の中央値）: "
                    + medianText(outcomes, o -> o.betterDay(FADE_15))
                    + "／FADE「45分」の初回: " + medianText(outcomes, o -> o.betterDay(FADE_45));
            default -> "どれかの選択肢を外した（R3）か戻した（R4）人: "
                    + percentText(count(outcomes, o -> Math.min(o.ruleDay(RULE_DROP), o.ruleDay(RULE_ROLLBACK)) < DAYS_84), outcomes.size());
        };
    }

    static String render(Section section) {
        Scenario scenario = section.scenario();
        StringBuilder text = new StringBuilder();
        text.append("## ").append(scenario.id()).append("  ").append(scenario.title()).append('\n');
        text.append("- 人数 ").append(scenario.users()).append("、夜数 ").append(scenario.days())
                .append("、種 ").append(personSeed(scenario, 0)).append(" から ")
                .append(personSeed(scenario, scenario.users() - 1)).append("（人番号順）\n");
        text.append("- 条件: ").append(modelText(scenario.model())).append('\n');
        for (Judgement judgement : section.judgements()) {
            text.append("- 判定: ").append(judgement.label()).append(" = ").append(judgement.measured())
                    .append("（基準: ").append(judgement.criterion()).append("）→ ")
                    .append(judgement.passed() ? "満たす" : "満たさない").append('\n');
        }
        for (String fact : section.facts()) {
            text.append("- ").append(fact).append('\n');
        }
        return text.append('\n').toString();
    }

    private static String modelText(SleepModel model) {
        String effects = model.effectMin().entrySet().stream()
                .map(entry -> entry.getKey().getDimension().name() + " " + entry.getKey().getOptionName().asText()
                        + " " + String.format(Locale.ROOT, "%+.1f", entry.getValue()) + " 分")
                .sorted()
                .collect(Collectors.joining("、"));
        return String.format(Locale.ROOT,
                "基準の寝つき %.0f 分（人どうし SD %.0f 分、夜ごと SD %.0f 分）、土日 +%.0f 分、傾向 %+.1f 分/日、"
                        + "選択肢の差 [%s]、寝つきのめやすが欠ける夜 %.0f%%（利用者が自分で終える）、会話へ切り替える夜 %.0f%%、"
                        + "「合っていた」の基準確率 %.2f",
                SimulatedSleeper.BASELINE_MEAN_MIN, SimulatedSleeper.PERSON_SD_MIN, model.nightSdMin(),
                SimulatedSleeper.WEEKEND_SHIFT_MIN, model.driftMinPerDay(),
                effects.isEmpty() ? NO_VALUE : effects, model.userEndsRatio() * PERCENT,
                model.changeRatio() * PERCENT, model.fitBase());
    }

    static Judgement judgement(String label, long hits, int total, String criterion, boolean passed) {
        return new Judgement(label, percentText(hits, total), criterion, passed);
    }

    static String percentText(long hits, int total) {
        return String.format(Locale.ROOT, "%.1f%%（%d/%d 人）", ratio(hits, total) * PERCENT, hits, total);
    }

    private static String percentOf(double share) {
        return String.format(Locale.ROOT, "%.0f%%", share * PERCENT);
    }

    static double ratio(long hits, int total) {
        return total == 0 ? 0.0 : (double) hits / total;
    }

    static long count(List<PersonOutcome> outcomes, Predicate<PersonOutcome> predicate) {
        return outcomes.stream().filter(predicate).count();
    }

    static int sum(List<PersonOutcome> outcomes, ToIntFunction<PersonOutcome> value) {
        return outcomes.stream().mapToInt(value).sum();
    }

    static double mean(List<PersonOutcome> outcomes, ToIntFunction<PersonOutcome> value) {
        return outcomes.stream().mapToInt(value).average().orElse(0.0);
    }

    /** 判定のうち、BETTER の割合（夜数がそろわない NEED_MORE は除く）。 */
    private static String betterShareText(List<PersonOutcome> outcomes) {
        int better = sum(outcomes, o -> o.verdictCount("better"));
        int decided = better + sum(outcomes, o -> o.verdictCount("worse"))
                + sum(outcomes, o -> o.verdictCount("similar"));
        if (decided == 0) {
            return NO_VALUE;
        }
        return String.format(Locale.ROOT, "%.1f%%", PERCENT * better / decided);
    }

    private static String meanText(List<PersonOutcome> outcomes, ToIntFunction<PersonOutcome> value) {
        return String.format(Locale.ROOT, "%.2f", mean(outcomes, value));
    }

    /** 初回の日（夜の番号）の中央値。日が見つからない人は除く。夜の番号は 1 始まりに直して示す。 */
    static String medianText(List<PersonOutcome> outcomes, ToIntFunction<PersonOutcome> day) {
        List<Integer> days = outcomes.stream().mapToInt(day).filter(value -> value != PersonOutcome.NO_DAY)
                .boxed().sorted(Comparator.naturalOrder()).toList();
        if (days.isEmpty()) {
            return NO_VALUE;
        }
        int middle = days.size() / 2;
        double median = days.size() % 2 == 1 ? days.get(middle)
                : (days.get(middle - 1) + days.get(middle)) / 2.0;
        return String.format(Locale.ROOT, "%.1f 夜目（%d 人）", median + ONE_NIGHT, days.size());
    }
}
