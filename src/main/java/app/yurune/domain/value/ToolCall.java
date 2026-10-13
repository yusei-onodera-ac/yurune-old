package app.yurune.domain.value;

import java.util.Map;
import java.util.Objects;

/**
 * エージェントが選んだツールの呼び出し（名前、引数、理由）。
 * 名前は文字列のまま持つ。許可リストにない名前も、受け取って検証し、拒否して記録するため。
 */
public final class ToolCall {

    private final String toolId;
    private final Map<String, String> arguments;
    private final DisplayText reason;

    public ToolCall(String toolId, Map<String, String> arguments, DisplayText reason) {
        this.toolId = Objects.requireNonNull(toolId);
        this.arguments = Map.copyOf(arguments);
        this.reason = Objects.requireNonNull(reason);
    }

    public String getToolId() {
        return toolId;
    }

    /** 引数の文字列を返す。ないときは null。 */
    public String findArgument(String name) {
        return arguments.get(name);
    }

    public DisplayText getReason() {
        return reason;
    }
}
