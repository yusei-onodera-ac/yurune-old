package app.yurune.sample;

import app.yurune.constant.PlanConstants;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.UserRepositories;

import java.time.Clock;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * 審査用アカウントに、デモ用のデータ（14 日分の記録、答え待ちの 1 晩）を置く。ほかの利用者には、何も置かない。
 * 学習の結果（選択肢ごとの集計）は置かない。ふりかえりの「いまのところ」や覚えたことは、ほかの利用者と同じく、このアカウントの夜からだけ作る。
 *
 * 記録の日付は「今日」からの日数で決まるので、審査員が開く日がいつでも、「今週」と「先週」が埋まっているように、
 * 日が変わって開かれたときは、その日の日付で入れ直す（すでにある日は、書き換えない。利用者が直した記録を残すため）。
 * 答え待ちの 1 晩も、その日の分がなければ置く（朝のひとこと の画面を、その日にも見せるため）。
 */
public final class DemoDataSeeder {

    private final Clock clock;

    public DemoDataSeeder(Clock clock) {
        this.clock = clock;
    }

    public void seedIfNeeded(UserRepositories repositories) {
        WakeDate today = WakeDate.today(clock);
        boolean seeded = repositories.getUserProfileRepository().findProfile().isSeeded();
        fillRecords(repositories, today);
        fillPendingNight(repositories, today);
        if (!seeded) {
            repositories.getUserProfileRepository().saveProfile(
                    repositories.getUserProfileRepository().findProfile().withSeeded());
        }
    }

    /** 直近 14 日の記録のうち、まだない日だけを置く（あるものは、書き換えない）。 */
    private void fillRecords(UserRepositories repositories, WakeDate today) {
        for (SleepRecord record : SampleRecordData.build(today)) {
            if (repositories.getSleepRecordRepository().findRecord(record.getWakeDate()).isEmpty()) {
                repositories.getSleepRecordRepository().saveRecord(record);
            }
        }
    }

    /** その日の晩が、まだなければ、朝のひとことの答え待ちとして置く（朝のひとことの画面を見せるため）。 */
    private void fillPendingNight(UserRepositories repositories, WakeDate today) {
        if (repositories.getNightRepository().findNight(today).isPresent()) {
            return;
        }
        List<SampleNight> nights = SampleNightData.USER_A;
        repositories.getNightRepository().saveNight(
                NightRecord.started(today, createPlanOfNight(nights.get(nights.size() - 1))));
    }

    /** サンプルの 1 晩を、その晩の計画にする。次の計画で、何を変えたかを示すための「前の計画」になる。 */
    private NightPlan createPlanOfNight(SampleNight lastNight) {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, lastNight.getOption(Dimension.SOUND));
        options.put(Dimension.TALK, lastNight.getOption(Dimension.TALK));
        options.put(Dimension.FADE, lastNight.getOption(Dimension.FADE));
        options.put(Dimension.DURATION, new OptionName(PlanConstants.DEFAULT_DURATION));
        return new NightPlan(options, EnumSet.noneOf(Dimension.class), new DisplayText("前の晩の計画"), 0L);
    }
}
