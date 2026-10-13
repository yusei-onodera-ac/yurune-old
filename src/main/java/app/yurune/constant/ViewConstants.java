package app.yurune.constant;

/**
 * 画面の経路と、Controller から View へ渡す値の名前。
 */
public final class ViewConstants {

    public static final String PATH_HOME = "/home";
    public static final String PATH_LOG = "/log";
    public static final String PATH_PREPARE = "/prepare";
    public static final String PATH_NIGHT = "/night";
    public static final String PATH_NIGHT_MODE = "/night/mode";
    public static final String PATH_NIGHT_END = "/night/end";
    public static final String PATH_NIGHT_OBSERVE = "/night/observe";
    public static final String PATH_REVIEW = "/review";
    public static final String PATH_SETTINGS = "/settings";
    public static final String PATH_MORNING = "/morning";

    /** WEB-INF 配下に置くことで、URL から JSP を直接開けなくする（Controller 経由のみ）。 */
    public static final String VIEW_HOME = "/WEB-INF/views/home.jsp";
    public static final String VIEW_LOG = "/WEB-INF/views/log.jsp";
    public static final String VIEW_PREPARE = "/WEB-INF/views/prepare.jsp";
    public static final String VIEW_NIGHT = "/WEB-INF/views/night.jsp";
    public static final String VIEW_REVIEW = "/WEB-INF/views/review.jsp";
    public static final String VIEW_SETTINGS = "/WEB-INF/views/settings.jsp";

    /** アプリの部品一式を、アプリケーション全体で共有するときの名前。 */
    public static final String ATTR_COMPONENTS = "yuruneComponents";

    public static final String ATTR_SAYS = "says";
    public static final String ATTR_DEMO_ACCOUNT = "demoAccount";
    public static final String ATTR_LAST_NIGHT = "lastNight";
    public static final String ATTR_MORNING_SHEET = "morningSheet";
    public static final String ATTR_MOODS = "moods";
    public static final String ATTR_ANSWERS = "answers";
    public static final String ATTR_DAYS = "days";
    public static final String ATTR_PLAN_ITEMS = "planItems";
    public static final String ATTR_PLAN_REASON = "planReason";
    public static final String ATTR_NIGHT_MODE = "nightMode";
    public static final String ATTR_NIGHT_SOUND = "nightSound";
    public static final String ATTR_NIGHT_TOTAL_MINUTES = "nightTotalMinutes";
    public static final String ATTR_NIGHT_INTERVAL_SEC = "nightIntervalSec";
    public static final String ATTR_NIGHT_STARTED_AT = "nightStartedAt";
    public static final String ATTR_NIGHT_NO_REACTION_SEC = "nightNoReactionSec";
    public static final String ATTR_WEEK = "week";
    public static final String ATTR_WEEK_SAY = "weekSay";
    public static final String ATTR_GOAL_LABEL = "goalLabel";
    public static final String ATTR_GOAL_DESCRIPTION = "goalDescription";
    public static final String ATTR_GOAL_SAY = "goalSay";

    /** 画面の入力欄の名前。 */
    public static final String PARAM_DATE = "date";
    public static final String PARAM_BED = "bed";
    public static final String PARAM_WAKE = "wake";
    public static final String PARAM_MODE = "mode";
    public static final String PARAM_MOOD = "mood";
    public static final String PARAM_ANSWER = "answer";
    public static final String PARAM_SKIP = "skip";
    public static final String PARAM_LAST_REACTION_AGO_SEC = "lastReactionAgoSec";
    public static final String PARAM_REASON = "reason";

    /** 画面の吹き出しの文を区切る文字。 */
    public static final String SAY_SEPARATOR = "|";

    private ViewConstants() {
    }
}
