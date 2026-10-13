package app.yurune.service;

import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;

import java.util.List;

/**
 * 夜のエージェント。観測を見て、どのツールをいつ呼ぶかを選ぶ（枠の中の判断）。
 * 実装は差し替えられる。LLM が使えないときは、{@link RuleBasedNightAgent} が代わる。
 */
public interface NightAgent {

    /** 呼ぶツールを、呼ぶ順に返す。何もしないときは空。 */
    List<ToolCall> decide(Observation observation, NightSession session);
}
