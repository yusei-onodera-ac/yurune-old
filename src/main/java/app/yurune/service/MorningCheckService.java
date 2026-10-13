package app.yurune.service;

import app.yurune.constant.OptionLabelConstants;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.DecisionRepository;
import app.yurune.repository.NightRepository;

import java.time.Clock;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 「朝のひとこと」を扱う。このアプリを使って寝た晩の朝にだけ聞き、一度確認した晩には、二度と聞かない。
 * 答えがあった晩だけ、試した選択肢の集計に反映する（答えのなかった晩は、報酬がないため反映しない）。
 * 設定の「好みを覚える」がオフのときは、答えは晩の記録には残すが、集計には反映せず、更新しなかったことを判断の記録に残す。
 */
public final class MorningCheckService {

    private final NightRepository nightRepository;
    private final DecisionRepository decisionRepository;
    private final LearningService learningService;
    private final RecordService recordService;
    private final Clock clock;
    private final Consumer<WakeDate> onMorningChecked;

    public MorningCheckService(NightRepository nightRepository, DecisionRepository decisionRepository,
            LearningService learningService, RecordService recordService, Clock clock) {
        this(nightRepository, decisionRepository, learningService, recordService, clock, wakeDate -> { });
    }

    /** @param onMorningChecked 朝の答えを保存したあとに、その夜の起きた日で呼ぶ（朝のようすのめやすの見直しのため）。 */
    public MorningCheckService(NightRepository nightRepository, DecisionRepository decisionRepository,
            LearningService learningService, RecordService recordService, Clock clock, Consumer<WakeDate> onMorningChecked) {
        this.nightRepository = nightRepository;
        this.decisionRepository = decisionRepository;
        this.learningService = learningService;
        this.recordService = recordService;
        this.clock = clock;
        this.onMorningChecked = onMorningChecked;
    }

    /** まだ確認していない晩があるときだけ、ひとことに出す内容を返す。 */
    public Optional<MorningSheet> findPendingSheet() {
        return nightRepository.findLatestUncheckedNight(recordService.findToday()).map(this::estimateSheet);
    }

    private MorningSheet estimateSheet(NightRecord night) {
        SleepRecord record = recordService.findRecord(night.getWakeDate());
        ClockTime wakeTime = record.isRecorded() ? record.getWakeTime() : ClockTime.of(LocalTime.now(clock));
        ClockTime bedTime = record.isRecorded()
                ? record.getBedTime()
                : night.findLastReactionTime().orElse(null);
        return new MorningSheet(bedTime, wakeTime);
    }

    /** 保存する。確認する晩がないとき（二重の送信など）は、何もしない。 */
    public void completeMorning(MorningInput input) {
        Optional<NightRecord> pending = nightRepository.findLatestUncheckedNight(recordService.findToday());
        if (pending.isEmpty()) {
            return;
        }
        NightRecord night = pending.get();
        saveTimes(night.getWakeDate(), input);

        Optional<MorningAnswer> answer = input.findAnswer();
        answer.ifPresent(value -> recordAnswer(night, value));
        nightRepository.saveNight(night.withMorningResult(answer.orElse(null), input.findMood().orElse(null)));
        onMorningChecked.accept(night.getWakeDate());
    }

    private void saveTimes(WakeDate wakeDate, MorningInput input) {
        if (input.findBedTime().isPresent() && input.findWakeTime().isPresent()) {
            recordService.saveRecord(wakeDate, input.findBedTime().get(), input.findWakeTime().get());
        }
    }

    /**
     * 朝の答えを、判断の記録に残す。答えは、夜の記録に残し、次の見直しで「合わなかった」の数に使う。
     * 勝ちの数（集計）には使わない。勝ちは、夜の指標（寝つきなど）で決める。
     */
    private void recordAnswer(NightRecord night, MorningAnswer answer) {
        String sound = OptionLabelConstants.labelOf(Dimension.SOUND, night.getPlan().getOption(Dimension.SOUND));
        if (!learningService.isEnabled()) {
            decisionRepository.saveDecision(new Decision(night.getWakeDate(), ClockTime.of(LocalTime.now(clock)),
                    DecisionPhase.UPDATE, Map.of(), new DisplayText("「" + sound + "」の記録は、更新しなかった"),
                    new DisplayText("設定で、好みを覚えないことにしているため。"), true));
            return;
        }
        decisionRepository.saveDecision(new Decision(night.getWakeDate(), ClockTime.of(LocalTime.now(clock)),
                DecisionPhase.UPDATE, Map.of("answer", answer.asId()),
                new DisplayText("「" + sound + "」の朝の答えを記録（" + answer.asLabel() + "）"),
                new DisplayText("次の見直しで、「合わなかった」の数に使う。"), true));
    }
}
