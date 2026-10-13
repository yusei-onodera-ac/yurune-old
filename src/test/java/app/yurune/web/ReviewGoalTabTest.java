package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.config.AppConfig;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.NightGoalConstants;
import app.yurune.constant.SettingsConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.SettingItem;
import app.yurune.service.MutableClock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ふりかえりの「目標」のタブ。設定の「夜のめやす」のいまの値を、そのまま出す（新しい保存先は作らない）。
 * 「実験」の区分は外したので、画面のファイルに、実験の語が残っていないことも確かめる。
 */
class ReviewGoalTabTest {

    private static final Path VIEWS_DIRECTORY = Path.of("src", "main", "webapp", "WEB-INF", "views");
    private static final String EXPERIMENT_WORD = "実験";

    private final MutableClock clock = new MutableClock("2026-10-09T13:00:00Z", "Asia/Tokyo");
    private final AuthenticatedUser alice = new AuthenticatedUser("uid-alice", "alice@example.com", false);
    private AppComponents components;

    @BeforeEach
    void setUp() {
        components = AppComponents.create(new DeploymentConfig(false), clock, AppConfig.defaults(), line -> { });
    }

    private FakeHttp.Exchange openReview(AuthenticatedUser user) {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        components.forUser(user).getReviewController().showReview(exchange.request());
        return exchange;
    }

    private void changeGoal(AuthenticatedUser user, NightGoal goal) {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.parameters.put(SettingsConstants.PARAM_ITEM, SettingItem.GOAL.asId());
        exchange.parameters.put(SettingsConstants.PARAM_VALUE, goal.asId());
        components.forUser(user).getSettingsController().updateSetting(exchange.request());
    }

    @Test
    void theGoalTabShowsTheCurrentGoalWithItsDescriptionAndASentence() {
        FakeHttp.Exchange exchange = openReview(alice);

        String label = NightGoalConstants.labelOf(NightGoal.ONSET);
        assertEquals(label, exchange.requestAttributes.get(ViewConstants.ATTR_GOAL_LABEL));
        assertEquals(NightGoalConstants.hintOf(NightGoal.ONSET), exchange.requestAttributes.get(ViewConstants.ATTR_GOAL_DESCRIPTION));
        assertEquals("いまの目標は「" + label + "」だよ。", exchange.requestAttributes.get(ViewConstants.ATTR_GOAL_SAY));
    }

    @Test
    void changingTheNightGoalInTheSettingsChangesTheGoalTab() {
        changeGoal(alice, NightGoal.WAKE);

        FakeHttp.Exchange exchange = openReview(alice);

        assertEquals(NightGoalConstants.labelOf(NightGoal.WAKE), exchange.requestAttributes.get(ViewConstants.ATTR_GOAL_LABEL));
        assertEquals(NightGoalConstants.hintOf(NightGoal.WAKE), exchange.requestAttributes.get(ViewConstants.ATTR_GOAL_DESCRIPTION));
    }

    @Test
    void theGoalOfOneUserIsNotShownToAnotherUser() {
        AuthenticatedUser bob = new AuthenticatedUser("uid-bob", "bob@example.com", false);
        changeGoal(alice, NightGoal.MORNING);

        FakeHttp.Exchange bobsReview = openReview(bob);

        assertEquals(NightGoalConstants.labelOf(NightGoal.ONSET), bobsReview.requestAttributes.get(ViewConstants.ATTR_GOAL_LABEL));
    }

    @Test
    void theReviewAndHomeScreensNoLongerHaveTheExperimentSection() throws IOException {
        for (String view : new String[] {"review.jsp", "home.jsp"}) {
            String text = Files.readString(VIEWS_DIRECTORY.resolve(view), StandardCharsets.UTF_8);
            assertFalse(text.contains(EXPERIMENT_WORD), view + " に、実験の語が残っている");
        }
        String review = Files.readString(VIEWS_DIRECTORY.resolve("review.jsp"), StandardCharsets.UTF_8);
        assertTrue(review.contains("data-panel=\"goal\""), "ふりかえりに、目標のタブがある");
    }

    @Test
    void theReviewScreenHasOnlyTheWeekAndGoalTabsAndTheSummaryLivesUnderTheGoal() throws IOException {
        String review = Files.readString(VIEWS_DIRECTORY.resolve("review.jsp"), StandardCharsets.UTF_8);

        assertFalse(review.contains("ぽてねのノート"), "ノートのタブは、外した");
        assertFalse(review.contains("data-panel=\"note\""));
        int goalPanelAt = review.indexOf("data-seg-panel=\"review:goal\"");
        assertTrue(goalPanelAt > 0);
        assertTrue(review.indexOf("いまのところ") > goalPanelAt, "「いまのところ」は、「目標」の中に出す");
        assertTrue(review.indexOf("/review/refer/hide") > goalPanelAt, "受診のすすめの「今は表示しない」も、「目標」の中");
    }
}
