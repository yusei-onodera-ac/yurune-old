package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.UserComponents;
import app.yurune.config.AppConfig;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.LogConstants;
import app.yurune.constant.SecurityConstants;
import app.yurune.constant.SettingsConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.BadRequestException;
import app.yurune.controller.Navigation;
import app.yurune.controller.view.SettingChoiceView;
import app.yurune.controller.view.SettingsView;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.filter.CsrfFilter;
import app.yurune.service.MutableClock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 設定画面の 4 項目が、保存され、画面に出て、夜の画面と会話の入口に効くこと。選べない値は 400 で断り、変更は記録に残る。
 */
class SettingsWebTest {

    private static final String TALK_BODY = "{\"text\":\"こんばんは\",\"history\":[]}";
    private static final Path SETTINGS_VIEW = Path.of("src", "main", "webapp", "WEB-INF", "views", "settings.jsp");
    private static final Path SCRIPT = Path.of("src", "main", "webapp", "js", "yurune.js");

    private final MutableClock clock = new MutableClock("2026-10-06T13:10:00Z", "Asia/Tokyo");
    private final List<String> lines = new ArrayList<>();
    private AppComponents components;
    private final AuthenticatedUser alice = new AuthenticatedUser("alice", "alice@example.com", false);
    private final AuthenticatedUser bob = new AuthenticatedUser("bob", "bob@example.com", false);

    @BeforeEach
    void setUp() {
        components = AppComponents.create(new DeploymentConfig(false), clock, AppConfig.defaults(), lines::add);
    }

    private static final class Call {
        final Map<String, String> parameters = new HashMap<>();
        final Map<String, Object> attributes = new HashMap<>();
        String body = "";

        Call with(String name, String value) {
            parameters.put(name, value);
            return this;
        }

        HttpServletRequest request() {
            return FakeHttp.proxy(HttpServletRequest.class, (self, called, args) -> switch (called.getName()) {
                case "getParameter" -> parameters.get((String) args[0]);
                case "setAttribute" -> {
                    attributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "getAttribute" -> attributes.get((String) args[0]);
                case "getInputStream" -> stream(body);
                default -> null;
            });
        }
    }

    private static ServletInputStream stream(String body) {
        ByteArrayInputStream source = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // 読み込みの通知は使わない
            }

            @Override
            public int read() {
                return source.read();
            }
        };
    }

    private Navigation change(AuthenticatedUser user, String item, String value) {
        return components.forUser(user).getSettingsController()
                .updateSetting(new Call().with(SettingsConstants.PARAM_ITEM, item).with(SettingsConstants.PARAM_VALUE, value).request());
    }

    private SettingsView show(AuthenticatedUser user) {
        Call call = new Call();
        assertEquals(ViewConstants.VIEW_SETTINGS, components.forUser(user).getSettingsController().showSettings(call.request()));
        return (SettingsView) call.attributes.get(SettingsConstants.ATTR_SETTINGS);
    }

    private JsonObject talk(AuthenticatedUser user, String body) {
        Call call = new Call();
        call.body = body;
        return JsonParser.parseString(components.forUser(user).getNightController().talk(call.request())).getAsJsonObject();
    }

    private void startNight(AuthenticatedUser user, String mode) {
        UserComponents userComponents = components.forUser(user);
        userComponents.getNightController().startNight(new Call().with(ViewConstants.PARAM_MODE, mode).request());
    }

    private Call showNight(AuthenticatedUser user) {
        Call call = new Call();
        components.forUser(user).getNightController().showNight(call.request());
        return call;
    }

    // ---- 保存と表示 ----

    @Test
    void theScreenShowsTheInitialValuesFromTheSpecification() {
        SettingsView view = show(alice);

        assertTrue(view.isNightTalkEnabled());
        assertEquals("60%", view.getVolumeCapText());
        assertEquals("やわらかい", view.getVoiceLabel());
        assertTrue(view.isLearningEnabled());
        assertEquals("寝つき", view.getGoalLabel());
    }

    @Test
    void changingASettingSavesItGoesBackToTheSettingsScreenAndTheScreenShowsIt() {
        Navigation navigation = change(alice, "volume_cap", "30");
        change(alice, "voice", "slow");
        change(alice, "night_talk", "off");
        change(alice, "learning", "off");
        change(alice, "night_goal", "wake");

        assertTrue(navigation.isRedirect(), "二重に送らないよう、移動で返す");
        assertEquals(ViewConstants.PATH_SETTINGS, navigation.getTarget());
        SettingsView view = show(alice);
        assertFalse(view.isNightTalkEnabled());
        assertEquals("30%", view.getVolumeCapText());
        assertEquals("ゆっくり", view.getVoiceLabel());
        assertFalse(view.isLearningEnabled());
        assertEquals("夜中に目が覚める", view.getGoalLabel());
    }

    @Test
    void theSavedValuesSurviveAReloadOfTheUsersComponents() {
        change(alice, "volume_cap", "20");
        components.forget(alice.getUid());

        assertEquals("20%", show(alice).getVolumeCapText());
    }

    @Test
    void anItemOrAValueThatCannotBeChosenIsRefusedAndNothingChanges() {
        for (String[] bad : new String[][] {{"volume_cap", "100"}, {"volume_cap", "abc"}, {"volume_cap", null}, {"night_talk", "true"},
                {"voice", "robot"}, {"learning", ""}, {"night_goal", "mood"}, {"night_goal", ""},
                {"consent", "off"}, {"seeded", "on"}, {null, "on"}, {"", ""}}) {
            assertThrows(BadRequestException.class, () -> change(alice, bad[0], bad[1]), String.join("=", String.valueOf(bad[0]), String.valueOf(bad[1])));
        }

        SettingsView view = show(alice);
        assertTrue(view.isNightTalkEnabled());
        assertEquals("60%", view.getVolumeCapText());
        assertEquals("やわらかい", view.getVoiceLabel());
        assertTrue(view.isLearningEnabled());
    }

    @Test
    void oneUsersSettingsAreNotSeenByAnotherUser() {
        change(alice, "volume_cap", "20");
        change(alice, "night_talk", "off");

        SettingsView bobView = show(bob);

        assertEquals("60%", bobView.getVolumeCapText());
        assertTrue(bobView.isNightTalkEnabled());
    }

    @Test
    void theChoiceScreensListOnlyTheAllowedValuesAndMarkTheCurrentOne() {
        change(alice, "volume_cap", "40");
        change(alice, "voice", "normal");

        Call volume = new Call();
        assertEquals(SettingsConstants.VIEW_CHOICE, components.forUser(alice).getSettingsController().showVolumeChoices(volume.request()));
        Call voice = new Call();
        assertEquals(SettingsConstants.VIEW_CHOICE, components.forUser(alice).getSettingsController().showVoiceChoices(voice.request()));

        @SuppressWarnings("unchecked")
        List<SettingChoiceView> volumeChoices = (List<SettingChoiceView>) volume.attributes.get(SettingsConstants.ATTR_CHOICES);
        assertEquals(List.of("20", "30", "40", "50", "60"), volumeChoices.stream().map(SettingChoiceView::getId).toList());
        assertEquals(List.of("40"), volumeChoices.stream().filter(SettingChoiceView::isChecked).map(SettingChoiceView::getId).toList());
        assertEquals("初期値", volumeChoices.get(4).getHint());
        assertEquals("volume_cap", volume.attributes.get(SettingsConstants.ATTR_CHOICE_ITEM));
        @SuppressWarnings("unchecked")
        List<SettingChoiceView> voiceChoices = (List<SettingChoiceView>) voice.attributes.get(SettingsConstants.ATTR_CHOICES);
        assertEquals(List.of("soft", "slow", "normal"), voiceChoices.stream().map(SettingChoiceView::getId).toList());
        assertEquals(List.of("normal"), voiceChoices.stream().filter(SettingChoiceView::isChecked).map(SettingChoiceView::getId).toList());
        assertEquals("voice", voice.attributes.get(SettingsConstants.ATTR_CHOICE_ITEM));
    }

    @Test
    void theGoalChoiceScreenListsTheThreeGoalsAndMarksTheCurrentOne() {
        change(alice, "night_goal", "wake");

        Call goal = new Call();
        assertEquals(SettingsConstants.VIEW_CHOICE, components.forUser(alice).getSettingsController().showGoalChoices(goal.request()));

        @SuppressWarnings("unchecked")
        List<SettingChoiceView> goalChoices = (List<SettingChoiceView>) goal.attributes.get(SettingsConstants.ATTR_CHOICES);
        assertEquals(List.of("onset", "wake", "morning"), goalChoices.stream().map(SettingChoiceView::getId).toList());
        assertEquals(List.of("寝つき", "夜中に目が覚める", "朝のようす"), goalChoices.stream().map(SettingChoiceView::getLabel).toList());
        assertEquals(List.of("wake"), goalChoices.stream().filter(SettingChoiceView::isChecked).map(SettingChoiceView::getId).toList());
        assertEquals("night_goal", goal.attributes.get(SettingsConstants.ATTR_CHOICE_ITEM));
    }

    // ---- 記録 ----

    @Test
    void aChangeIsLoggedWithTheItemAndTheNewValueOnly() {
        change(alice, "volume_cap", "30");

        assertEquals(1, lines.size());
        JsonObject line = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals(LogConstants.EVENT_SETTING, line.get(LogConstants.FIELD_EVENT).getAsString());
        JsonObject detail = line.getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("volume_cap", detail.get("item").getAsString());
        assertEquals("30", detail.get("value").getAsString());
        assertEquals(2, detail.size(), "項目と値のほかは、出さない");
        assertFalse(lines.get(0).contains("alice"), "利用者の名前は出さない");
    }

    @Test
    void aRefusedChangeLeavesNothingInTheLog() {
        assertThrows(BadRequestException.class, () -> change(alice, "volume_cap", "ZXQ9<script>"));

        assertTrue(lines.isEmpty());
    }

    // ---- 夜の会話 ----

    @Test
    void talkIsRefusedBeforeAnythingIsReadWhenTheSettingIsOff() {
        startNight(alice, "talk");
        assertEquals("ok", talk(alice, TALK_BODY).get("state").getAsString(), "オンのときは、会話に応じる");

        change(alice, "night_talk", "off");

        assertEquals("talk_disabled", talk(alice, TALK_BODY).get("state").getAsString());
        assertEquals("talk_disabled", talk(alice, "not even json").get("state").getAsString(), "本文を読む前に断る");
    }

    @Test
    void talkIsRefusedForTheUserWhoTurnedItOffButNotForOthers() {
        startNight(alice, "talk");
        startNight(bob, "talk");

        change(alice, "night_talk", "off");

        assertEquals("talk_disabled", talk(alice, TALK_BODY).get("state").getAsString());
        assertEquals("ok", talk(bob, TALK_BODY).get("state").getAsString());
    }

    @Test
    void aNightStartedWithTalkWhileItIsOffIsASoundOnlyNight() {
        change(alice, "night_talk", "off");

        startNight(alice, "talk");

        Call night = showNight(alice);
        assertEquals("sound", night.attributes.get(ViewConstants.ATTR_NIGHT_MODE));
        assertEquals(false, night.attributes.get(SettingsConstants.ATTR_TALK_ENABLED));
        assertEquals("talk_disabled", talk(alice, TALK_BODY).get("state").getAsString());
    }

    @Test
    void turningTalkOffDuringATalkNightMakesTheNextScreenSoundOnly() {
        startNight(alice, "talk");
        assertEquals("talk", showNight(alice).attributes.get(ViewConstants.ATTR_NIGHT_MODE));

        change(alice, "night_talk", "off");

        assertEquals("sound", showNight(alice).attributes.get(ViewConstants.ATTR_NIGHT_MODE));
    }

    @Test
    void thePrepareScreenOffersTalkOnlyWhileTheSettingIsOn() {
        Call before = new Call();
        components.forUser(alice).getPrepareController().showPrepare(before.request());
        change(alice, "night_talk", "off");
        Call after = new Call();
        components.forUser(alice).getPrepareController().showPrepare(after.request());

        assertEquals(true, before.attributes.get(SettingsConstants.ATTR_TALK_ENABLED));
        assertEquals(false, after.attributes.get(SettingsConstants.ATTR_TALK_ENABLED));
    }

    // ---- 音量の上限と声（端末に渡す値） ----

    @Test
    void theNightScreenGetsTheVolumeCapAndTheVoiceFromTheSettings() {
        startNight(alice, "sound");
        Call initial = showNight(alice);
        change(alice, "volume_cap", "30");
        change(alice, "voice", "slow");
        Call changed = showNight(alice);

        assertEquals(60, initial.attributes.get(SettingsConstants.ATTR_NIGHT_VOLUME_CAP));
        assertEquals(0.9, (double) initial.attributes.get(SettingsConstants.ATTR_NIGHT_SPEECH_RATE), 0.0001);
        assertEquals(30, changed.attributes.get(SettingsConstants.ATTR_NIGHT_VOLUME_CAP));
        assertEquals(0.8, (double) changed.attributes.get(SettingsConstants.ATTR_NIGHT_SPEECH_RATE), 0.0001);
    }

    @Test
    void theVolumeOfARunningNightIsBroughtDownToTheCapWhenTheScreenIsOpened() {
        startNight(alice, "sound");
        change(alice, "volume_cap", "20");

        showNight(alice);

        Call observe = new Call().with(ViewConstants.PARAM_LAST_REACTION_AGO_SEC, "0");
        String json = components.forUser(alice).getNightController().observe(observe.request());
        assertFalse(json.contains("set_volume"), "画面を開いたときに、すでにそろえてある");
    }

    // ---- 経路の守り ----

    @Test
    void theSettingsRoutesNeedLoginAndTheCsrfToken() throws Exception {
        for (String path : List.of(SettingsConstants.PATH_UPDATE, SettingsConstants.PATH_VOLUME, SettingsConstants.PATH_VOICE,
                SettingsConstants.PATH_GOAL)) {
            assertFalse(components.getWebRequestPolicy().isPublicPath(path), path);
        }
        ServletContext context = FakeHttp.proxy(ServletContext.class, (self, called, args) ->
                "getAttribute".equals(called.getName()) && ViewConstants.ATTR_COMPONENTS.equals(args[0]) ? components : null);
        CsrfFilter filter = new CsrfFilter();
        filter.init(FakeHttp.proxy(FilterConfig.class, (self, called, args) ->
                "getServletContext".equals(called.getName()) ? context : null));

        FakeHttp.Exchange withoutToken = new FakeHttp.Exchange();
        withoutToken.method = "POST";
        withoutToken.servletPath = SettingsConstants.PATH_UPDATE;
        filter.doFilter(withoutToken.request(), withoutToken.response(), withoutToken.chain());
        assertEquals(403, withoutToken.errorStatus);
        assertEquals(0, withoutToken.chainCalls.get());

        FakeHttp.Exchange withToken = new FakeHttp.Exchange();
        withToken.method = "POST";
        withToken.servletPath = SettingsConstants.PATH_UPDATE;
        String token = components.getCsrfTokenService().issueToken();
        withToken.sessionAttributes.put(SecurityConstants.SESSION_CSRF_TOKEN, token);
        withToken.requestHeaders.put(SecurityConstants.CSRF_HEADER, token);
        filter.doFilter(withToken.request(), withToken.response(), withToken.chain());
        assertEquals(1, withToken.chainCalls.get());
    }

    // ---- 画面のファイル ----

    @Test
    void theSettingsScreenLinksToTheThreePagesInTheSameFormAsTheOtherRows() throws IOException {
        String view = Files.readString(SETTINGS_VIEW, StandardCharsets.UTF_8);

        for (String page : List.of("memory", "credits", "guide", "volume", "voice", "goal")) {
            assertTrue(view.contains("<a class=\"row\" href=\"${base}/settings/" + page + "\">"), page);
        }
        assertTrue(view.contains("<a class=\"row\" href=\"${base}/terms\">"));
        assertTrue(view.contains("<a class=\"row\" href=\"${base}/settings/memory\"><div class=\"t\">覚えたことを見る<small>構造化した項目だけ</small></div><span class=\"v ch\"></span></a>"));
        assertTrue(view.contains("<a class=\"row\" href=\"${base}/settings/credits\"><div class=\"t\">音源のクレジット</div><span class=\"v ch\"></span></a>"));
        assertTrue(view.contains("<a class=\"row\" href=\"${base}/settings/guide\"><div class=\"t\">ご利用にあたって</div><span class=\"v ch\"></span></a>"));
    }

    @Test
    void theSwitchesAreNotStaticDecorationsAndTheScriptSavesThemWithTheCsrfHeader() throws IOException {
        String view = Files.readString(SETTINGS_VIEW, StandardCharsets.UTF_8);
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertFalse(view.contains("type=\"button\" class=\"sw\""), "保存につながらない飾りのスイッチは、残さない");
        assertTrue(view.contains("<y:toggle item=\"night_talk\""));
        assertTrue(view.contains("<y:toggle item=\"learning\""));
        int start = script.indexOf("function bindSwitches()");
        String body = script.substring(start, script.indexOf("function readVolumeCap()"));
        assertTrue(body.contains("postHeaders()"), "fetch には、CSRF トークンの付く headers を使う");
        assertTrue(body.contains("catch"), "保存できなかったときの戻しがある");
    }

    @Test
    void theScriptKeepsTheVolumeCapOnEveryPlaybackPath() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertTrue(script.contains("utterance.volume = Math.min(TALK_SPEECH_VOLUME, volumeCap)"));
        assertTrue(script.contains("audio.volume = volumeCap"));
    }
}
