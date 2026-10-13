package app.yurune.service;

import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.Minutes;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 見直しのテストで使う、夜の記録の用意。寝つきの値（onsetMin）がない夜は、利用者が終えた夜として作る（欠測）。
 */
final class ReviewFixtures {

    static final String BASE_DAY = "2026-09-01";
    static final String STANDARD_SOUND = "rain";
    static final String STANDARD_FADE = "30";

    private ReviewFixtures() {
    }

    /**
     * 1 晩の記録。role が変える夜のときは、factor の選択肢（option）を、標準と違うものにする。
     * onsetMin が null の夜は、利用者が終えた夜（寝つきの値なし）になる。answer が null の夜は、朝の答えなし。
     */
    static NightRecord night(WakeDate day, NightRole role, Dimension factor, String option, Integer onsetMin,
            MorningAnswer answer) {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName(STANDARD_SOUND));
        options.put(Dimension.FADE, new OptionName(STANDARD_FADE));
        options.put(Dimension.DURATION, new OptionName("45"));
        options.put(Dimension.TALK, new OptionName("none"));
        Set<Dimension> changed = EnumSet.noneOf(Dimension.class);
        if (role == NightRole.VARIANT) {
            options.put(factor, new OptionName(option));
            changed.add(factor);
        }
        NightPlan plan = new NightPlan(options, changed, new DisplayText("テスト用の計画"), day.toEpochDay(), role);
        NightRecord record = NightRecord.started(day, plan, role, role == NightRole.VARIANT ? factor : null);
        if (onsetMin == null) {
            record = record.withEnd(EndReason.USER, new Minutes(30), ClockTime.parse("22:40"));
        } else {
            record = record.withEnd(EndReason.NO_REACTION, new Minutes(30), ClockTime.parse("22:40"),
                    new Minutes(onsetMin));
        }
        if (answer != null) {
            record = record.withMorningResult(answer, null);
        }
        return record;
    }

    /** 基準の夜（標準のやり方）。 */
    static NightRecord standard(WakeDate day, NightRole role, Integer onsetMin, MorningAnswer answer) {
        return night(day, role, Dimension.SOUND, STANDARD_SOUND, onsetMin, answer);
    }

    /** 音を変えた夜（選択肢を指定）。 */
    static NightRecord soundVariant(WakeDate day, String option, Integer onsetMin, MorningAnswer answer) {
        return night(day, NightRole.VARIANT, Dimension.SOUND, option, onsetMin, answer);
    }

    /**
     * 変えた夜（音、選択肢を指定）を count 夜ぶん、対照夜を 2 夜おきに混ぜて作る（R2 のペアが、必ず近くに見つかるように）。
     * startOffset から、起きた日の順に並べる。
     */
    static List<NightRecord> soundVariantsWithControls(int startOffset, int count, String option, int variantOnsetMin,
            int controlOnsetMin) {
        List<NightRecord> nights = new ArrayList<>();
        int offset = startOffset;
        for (int made = 1; made <= count; made++) {
            nights.add(soundVariant(day(offset), option, variantOnsetMin, null));
            offset++;
            if (made % 2 == 0) {
                nights.add(standard(day(offset), NightRole.CONTROL, controlOnsetMin, null));
                offset++;
            }
        }
        return nights;
    }

    static WakeDate day(int offset) {
        return WakeDate.parse(BASE_DAY).plusDays(offset);
    }
}
