package app.yurune;

import app.yurune.controller.HomeController;
import app.yurune.controller.LogController;
import app.yurune.controller.MemoryController;
import app.yurune.controller.MorningController;
import app.yurune.controller.NightController;
import app.yurune.controller.PrepareController;
import app.yurune.controller.ReviewController;
import app.yurune.controller.SettingsController;

/**
 * 1 人の利用者のための、画面の入口（Controller）の一式。その人の保存先にだけ結びついている。
 * 組み立ては {@link AppComponents} が行い、Servlet は、ログイン中の利用者の一式だけを受け取る。
 */
public final class UserComponents {

    private final HomeController homeController;
    private final LogController logController;
    private final PrepareController prepareController;
    private final NightController nightController;
    private final ReviewController reviewController;
    private final SettingsController settingsController;
    private final MorningController morningController;
    private final MemoryController memoryController;

    UserComponents(HomeController homeController, LogController logController, PrepareController prepareController,
            NightController nightController, ReviewController reviewController,
            SettingsController settingsController, MorningController morningController,
            MemoryController memoryController) {
        this.homeController = homeController;
        this.logController = logController;
        this.prepareController = prepareController;
        this.nightController = nightController;
        this.reviewController = reviewController;
        this.settingsController = settingsController;
        this.morningController = morningController;
        this.memoryController = memoryController;
    }

    public HomeController getHomeController() {
        return homeController;
    }

    public LogController getLogController() {
        return logController;
    }

    public PrepareController getPrepareController() {
        return prepareController;
    }

    public NightController getNightController() {
        return nightController;
    }

    public ReviewController getReviewController() {
        return reviewController;
    }

    public SettingsController getSettingsController() {
        return settingsController;
    }

    public MorningController getMorningController() {
        return morningController;
    }

    public MemoryController getMemoryController() {
        return memoryController;
    }
}
