package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.constant.NoteSummaryConstants;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.NightRepository;
import app.yurune.repository.PdcaStateRepository;

import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

/**
 * ふりかえりの「いまのところ」を、利用者ごとの保存先から作り、「今は表示しない」を受け付ける。
 * 夜のめやすは、そのつど設定から読む（設定を変えたら、次の表示から変わる）。
 */
public final class NoteSummaryService {

    private final NightRepository nightRepository;
    private final PdcaStateRepository stateRepository;
    private final Supplier<NightGoal> goalSupplier;
    private final Clock clock;

    public NoteSummaryService(NightRepository nightRepository, PdcaStateRepository stateRepository,
            Supplier<NightGoal> goalSupplier, Clock clock) {
        this.nightRepository = nightRepository;
        this.stateRepository = stateRepository;
        this.goalSupplier = goalSupplier;
        this.clock = clock;
    }

    public NoteSummary findSummary() {
        List<NightRecord> nights = nightRepository.findRecentNights(GentlePolicy.REVIEW_HISTORY_NIGHTS);
        return new NoteSummaryBuilder(goalSupplier.get()).build(WakeDate.today(clock), nights, stateRepository.findState());
    }

    /**
     * 受診のすすめが出ているときだけ、一定の日数のあいだ出さない。出ていないときは何もしない
     * （出ていない状態で止めると、次の受診のすすめまで、止まったままになるため）。
     */
    public void hideReferral() {
        WakeDate today = WakeDate.today(clock);
        PdcaState state = stateRepository.findState();
        if (!NoteSummaryBuilder.isReferralShown(today, state)) {
            return;
        }
        stateRepository.saveState(state.withReferHiddenUntil(today.plusDays(NoteSummaryConstants.REFER_HIDE_DAYS)));
    }
}
