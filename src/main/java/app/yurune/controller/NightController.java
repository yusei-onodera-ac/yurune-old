package app.yurune.controller;

import app.yurune.config.AppConfig;
import app.yurune.constant.OptionLabelConstants;
import app.yurune.constant.SettingsConstants;
import app.yurune.constant.SoundConstants;
import app.yurune.constant.TimeConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.FactSheet;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.VoiceStyle;
import app.yurune.service.ConversationReply;
import app.yurune.service.ConversationService;
import app.yurune.service.NightObservationResult;
import app.yurune.service.NoActiveNightException;
import app.yurune.service.NightSessionService;
import app.yurune.service.NightSoundState;
import app.yurune.service.PlanService;
import app.yurune.service.SettingsService;
import app.yurune.service.SpeechGateway;
import app.yurune.service.SpeechUnavailableException;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 夜の画面の入出力を受け持つ。始める、すごし方を変える、終わる、観測を受け取る。
 * 音だけと会話は、同じ画面の中で切り替える。
 */
public final class NightController {

    private static final String TOOL_SWITCH_MODE = "switch_mode";
    private static final String STATE_TALK_DISABLED = "talk_disabled";

    private final PlanService planService;
    private final NightSessionService nightSessionService;
    private final AppConfig config;
    private final ConversationService conversationService;
    private final SpeechGateway speechGateway;
    private final SettingsService settingsService;
    private final TalkRequestReader talkRequestReader = new TalkRequestReader();

    public NightController(PlanService planService, NightSessionService nightSessionService, AppConfig config,
            ConversationService conversationService, SpeechGateway speechGateway, SettingsService settingsService) {
        this.planService = planService;
        this.nightSessionService = nightSessionService;
        this.config = config;
        this.conversationService = conversationService;
        this.speechGateway = speechGateway;
        this.settingsService = settingsService;
    }

    /**
     * 進んでいる夜があれば表示する。なければ、準備の画面へ移す。
     * 設定に合わない状態（会話がオフなのに会話、音量が上限超え）は、表示の前にコードが直す。
     */
    public Navigation showNight(HttpServletRequest request) {
        nightSessionService.enforceSettings();
        Optional<NightSession> session = nightSessionService.findActiveSession();
        Optional<NightPlan> plan = nightSessionService.findActivePlan();
        if (session.isEmpty() || plan.isEmpty()) {
            return Navigation.redirect(ViewConstants.PATH_PREPARE);
        }
        request.setAttribute(ViewConstants.ATTR_NIGHT_MODE, session.get().getMode().asId());
        request.setAttribute(ViewConstants.ATTR_NIGHT_SOUND,
                OptionLabelConstants.labelOf(Dimension.SOUND, plan.get().getOption(Dimension.SOUND)));
        request.setAttribute(ViewConstants.ATTR_NIGHT_TOTAL_MINUTES,
                Integer.parseInt(plan.get().getOption(Dimension.DURATION).asText()));
        request.setAttribute(ViewConstants.ATTR_NIGHT_INTERVAL_SEC, config.getObservationIntervalSec());
        request.setAttribute(ViewConstants.ATTR_NIGHT_STARTED_AT, session.get().getStartedAt().toEpochMilli());
        request.setAttribute(ViewConstants.ATTR_NIGHT_NO_REACTION_SEC,
                config.getNoReactionMin() * TimeConstants.SECONDS_PER_MINUTE);
        nightSessionService.findSoundState().ifPresent(state -> setSoundAttributes(request, state));
        setSettingAttributes(request, settingsService.findEnforcedSettings());
        return Navigation.forward(ViewConstants.VIEW_NIGHT);
    }

    /** 端末が、読み込みのときから、いまの音を鳴らし始められるように渡す（読み直したときも、同じ音に戻る）。 */
    private void setSoundAttributes(HttpServletRequest request, NightSoundState state) {
        request.setAttribute(SoundConstants.ATTR_SOUND_KEY, state.getSoundKey());
        request.setAttribute(SoundConstants.ATTR_SOUND_VOLUME_PCT, state.getVolumePct());
        request.setAttribute(SoundConstants.ATTR_FADE_REMAINING_SEC, state.getFadeRemainingSec());
    }

    /** 端末の再生が守る値（音量の上限、読み上げの速さと高さ）と、会話の入口を出すか。 */
    private void setSettingAttributes(HttpServletRequest request, UserSettings settings) {
        request.setAttribute(SettingsConstants.ATTR_TALK_ENABLED, settings.isNightTalkEnabled());
        request.setAttribute(SettingsConstants.ATTR_NIGHT_VOLUME_CAP,
                settings.getVolumeCapPctWithin(config.getVolumeCapPct()));
        request.setAttribute(SettingsConstants.ATTR_NIGHT_SPEECH_RATE, settings.getVoiceStyle().getBrowserRate());
        request.setAttribute(SettingsConstants.ATTR_NIGHT_SPEECH_PITCH, settings.getVoiceStyle().getBrowserPitch());
        request.setAttribute(SettingsConstants.ATTR_NIGHT_KEEP_SCREEN_ON, settings.isKeepScreenOnEnabled());
    }

    /** 今夜の計画で夜を始め、戻る画面の場所を返す。すでに進んでいる夜があるときは、そのまま続ける。 */
    public String startNight(HttpServletRequest request) {
        if (nightSessionService.findActiveSession().isEmpty()) {
            nightSessionService.startNight(planService.proposeTonightPlan(), readMode(request));
        }
        return ViewConstants.PATH_NIGHT;
    }

    /** 利用者が、すごし方を切り替える。 */
    public String changeMode(HttpServletRequest request) {
        NightMode mode = readMode(request);
        try {
            nightSessionService.changeMode(mode);
        } catch (NoActiveNightException exception) {
            /* 夜はすでに終わっている。夜の画面は、夜がなければ準備の画面へ移すので、ここでは何もしない */
        }
        return ViewConstants.PATH_NIGHT;
    }

    /**
     * 夜を終える。利用者が「終わる」を押したとき（reason が user、または省略）と、
     * 端末のタイマーが無反応を検出したとき（reason が no_reaction）。
     */
    public String endNight(HttpServletRequest request) {
        try {
            EndReason reason = readEndReason(request);
            if (nightSessionService.findActiveSession().isPresent()) {
                nightSessionService.endNight(reason, reason == EndReason.USER ? 0 : readLastReactionAgoSec(request));
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("invalid end request", exception);
        } catch (NoActiveNightException exception) {
            /* 端末のタイマーと「終わる」が同時に届き、先の要求で夜が終わった。終えたい結果は、すでに得られている */
        }
        return ViewConstants.PATH_HOME;
    }

    /** 端末の観測を渡し、端末が反映する内容を JSON で返す。 */
    public String observe(HttpServletRequest request) {
        if (nightSessionService.findActiveSession().isEmpty()) {
            return toJson(true, false, List.of(), null);
        }
        NightObservationResult result;
        try {
            result = nightSessionService.observe(readLastReactionAgoSec(request));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("invalid observation", exception);
        } catch (NoActiveNightException exception) {
            /* 確認のあとに、ほかの要求が夜を終えた。端末には、終わったと伝える */
            return toJson(true, false, List.of(), null);
        }
        List<String> accepted = new ArrayList<>();
        result.getAppliedCalls().stream()
                .filter(call -> call.isAccepted())
                .forEach(call -> accepted.add(call.getToolId()));
        return toJson(result.isEnded(), result.hasAcceptedCall(TOOL_SWITCH_MODE), accepted, result.getSoundState());
    }

    /**
     * 会話の 1 回分。利用者の発言（文字にしたもの）を受け取り、検査を通した返事を JSON で返す。
     * 発言も返事も、保存しない。会話のときでなければ、何も返さない（state が not_talking）。
     * 設定で夜の会話がオフのときは、LLM を呼ばずに断る（state が talk_disabled）。
     * サーバーでの読み上げが使えるときは、返事の音声（MP3、base64）も、設定の声の種類で添える。使えないときは、端末の読み上げに任せる。
     */
    public String talk(HttpServletRequest request) {
        UserSettings settings = settingsService.findEnforcedSettings();
        JsonObject json = new JsonObject();
        if (!settings.isNightTalkEnabled()) {
            json.addProperty("state", STATE_TALK_DISABLED);
            return json.toString();
        }
        TalkRequest talkRequest = talkRequestReader.read(request);
        Optional<FactSheet> facts = nightSessionService.findTalkFacts();
        if (facts.isEmpty()) {
            json.addProperty("state", "not_talking");
            return json.toString();
        }
        nightSessionService.recordUserReaction();
        ConversationReply reply = conversationService.respond(talkRequest.getUtterance(), talkRequest.getHistory(), facts.get());
        json.addProperty("state", "ok");
        json.addProperty("reply", reply.getText());
        json.addProperty("source", reply.getSource().asId());
        json.addProperty("speak", reply.shouldSpeak());
        if (reply.shouldSpeak() && speechGateway.isEnabled()) {
            addAudio(json, reply.getText(), settings.getVoiceStyle());
        }
        return json.toString();
    }

    private void addAudio(JsonObject json, String text, VoiceStyle voiceStyle) {
        try {
            json.addProperty("audio", Base64.getEncoder().encodeToString(speechGateway.synthesize(text, voiceStyle)));
        } catch (SpeechUnavailableException exception) {
            json.addProperty("audio", "");
        }
    }

    private NightMode readMode(HttpServletRequest request) {
        try {
            return NightMode.fromId(request.getParameter(ViewConstants.PARAM_MODE));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("invalid mode", exception);
        }
    }

    /** 端末が申告する「最後の反応から何秒たったか」。数でないときは、IllegalArgumentException。 */
    private int readLastReactionAgoSec(HttpServletRequest request) {
        try {
            return Integer.parseInt(request.getParameter(ViewConstants.PARAM_LAST_REACTION_AGO_SEC));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("not a number", exception);
        }
    }

    /** reason が省略されたときは、利用者が終わらせた（user）とみなす。 */
    private EndReason readEndReason(HttpServletRequest request) {
        String reason = request.getParameter(ViewConstants.PARAM_REASON);
        return reason == null || reason.isEmpty() ? EndReason.USER : EndReason.fromId(reason);
    }

    /**
     * 端末へ返す JSON。sound は、夜がなければ null。音の状態は、端末が鳴らす音を決めるためだけに使う。
     * 既存の項目（ended、reload、actions）は、そのまま残す。
     */
    private String toJson(boolean ended, boolean reload, List<String> acceptedTools, NightSoundState sound) {
        JsonArray actions = new JsonArray();
        acceptedTools.forEach(actions::add);
        JsonObject json = new JsonObject();
        json.addProperty("ended", ended);
        json.addProperty("reload", reload);
        json.add("actions", actions);
        json.add(SoundConstants.JSON_SOUND, sound == null ? JsonNull.INSTANCE : soundJson(sound));
        return json.toString();
    }

    private JsonObject soundJson(NightSoundState sound) {
        JsonObject json = new JsonObject();
        json.addProperty(SoundConstants.JSON_TRACK, sound.getSoundKey());
        json.addProperty(SoundConstants.JSON_VOLUME_PCT, sound.getVolumePct());
        json.addProperty(SoundConstants.JSON_FADE_REMAINING_SEC, sound.getFadeRemainingSec());
        return json;
    }
}
