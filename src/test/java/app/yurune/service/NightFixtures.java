package app.yurune.service;

import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.OptionName;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * 夜に関するテストで共通に使う、計画などの用意。
 */
final class NightFixtures {

    private NightFixtures() {
    }

    /** 出力の検査（使い捨ての合言葉）。テストで、ツールの検証に渡す。 */
    static OutputGuard createOutputGuard() {
        return new OutputGuard(new ForbiddenDomainFilter(), new PiiMasker(), new PromptSecrets());
    }

    static NightPlan createPlan(String sound, String talk, String fade) {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName(sound));
        options.put(Dimension.TALK, new OptionName(talk));
        options.put(Dimension.FADE, new OptionName(fade));
        options.put(Dimension.DURATION, new OptionName("45"));
        return new NightPlan(options, EnumSet.of(Dimension.SOUND), new DisplayText("テスト用の計画"), 1L);
    }
}
