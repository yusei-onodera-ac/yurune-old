package app.yurune.domain.value;

/**
 * 夜のエージェントが呼べるツール（許可リスト）。ここにないものは、実装も登録もしない。
 */
public enum ToolName {

    SET_VOLUME("set_volume"),
    FADE_OUT("fade_out"),
    SWITCH_TRACK("switch_track"),
    SET_TALK_LEVEL("set_talk_level"),
    SWITCH_MODE("switch_mode"),
    SAY("say"),
    END_SESSION("end_session");

    private final String id;

    ToolName(String id) {
        this.id = id;
    }

    /** 許可リストにない名前は例外にする（呼び出し側が、拒否して記録する）。 */
    public static ToolName fromId(String id) {
        for (ToolName tool : values()) {
            if (tool.id.equals(id)) {
                return tool;
            }
        }
        throw new IllegalArgumentException("tool is not in the allowlist: " + id);
    }

    public String asId() {
        return id;
    }
}
