package app.yurune.repository;

/**
 * 1 人の利用者のデータを読み書きする、保存先の一式。どれも、その利用者の uid に結びついている。
 * 別の利用者のデータには、触れる窓口がない。
 */
public final class UserRepositories {

    private final ArmRepository armRepository;
    private final SleepRecordRepository sleepRecordRepository;
    private final NightRepository nightRepository;
    private final NightSessionRepository nightSessionRepository;
    private final DecisionRepository decisionRepository;
    private final UserProfileRepository userProfileRepository;
    private final PdcaStateRepository pdcaStateRepository;

    public UserRepositories(ArmRepository armRepository, SleepRecordRepository sleepRecordRepository,
            NightRepository nightRepository,
            NightSessionRepository nightSessionRepository, DecisionRepository decisionRepository,
            UserProfileRepository userProfileRepository, PdcaStateRepository pdcaStateRepository) {
        this.armRepository = armRepository;
        this.sleepRecordRepository = sleepRecordRepository;
        this.nightRepository = nightRepository;
        this.nightSessionRepository = nightSessionRepository;
        this.decisionRepository = decisionRepository;
        this.userProfileRepository = userProfileRepository;
        this.pdcaStateRepository = pdcaStateRepository;
    }

    public ArmRepository getArmRepository() {
        return armRepository;
    }

    public SleepRecordRepository getSleepRecordRepository() {
        return sleepRecordRepository;
    }

    public NightRepository getNightRepository() {
        return nightRepository;
    }

    public NightSessionRepository getNightSessionRepository() {
        return nightSessionRepository;
    }

    public DecisionRepository getDecisionRepository() {
        return decisionRepository;
    }

    public UserProfileRepository getUserProfileRepository() {
        return userProfileRepository;
    }

    public PdcaStateRepository getPdcaStateRepository() {
        return pdcaStateRepository;
    }
}
