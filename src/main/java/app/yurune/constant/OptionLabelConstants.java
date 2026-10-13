package app.yurune.constant;

import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;

import java.util.List;
import java.util.Map;

/**
 * 選択肢と次元の、画面に出す名前。
 */
public final class OptionLabelConstants {

    private static final Map<String, String> OPTION_LABELS = Map.ofEntries(
            Map.entry("sound__rain", "雨の音"),
            Map.entry("sound__wave", "波の音"),
            Map.entry("sound__forest", "森の音"),
            Map.entry("talk__none", "会話なし"),
            Map.entry("talk__on", "会話あり"),
            Map.entry("fade__15", "15分かけて"),
            Map.entry("fade__30", "30分かけて"),
            Map.entry("fade__45", "45分かけて"),
            Map.entry("duration__30", "30分"),
            Map.entry("duration__45", "45分"));

    private static final Map<Dimension, String> DIMENSION_LABELS = Map.of(
            Dimension.SOUND, "音",
            Dimension.DURATION, "長さ",
            Dimension.TALK, "会話",
            Dimension.FADE, "音量の下げ方",
            Dimension.NO_REACTION, "終わるまでの無反応時間");

    /** 名前が未登録のときは、選択肢の ID をそのまま返す（画面が空にならないため）。 */
    public static String labelOf(Dimension dimension, OptionName optionName) {
        String documentId = new ArmKey(dimension, optionName).asDocumentId();
        return OPTION_LABELS.getOrDefault(documentId, optionName.asText());
    }

    /** 音の選択肢の、画面に出す名前の一覧。会話で、計画にない音を言っていないかの照合に使う。 */
    public static List<String> soundLabels() {
        return PlanConstants.SOUND_CANDIDATES.stream()
                .map(candidate -> labelOf(Dimension.SOUND, new OptionName(candidate)))
                .toList();
    }

    public static String dimensionLabelOf(Dimension dimension) {
        return DIMENSION_LABELS.get(dimension);
    }

    private OptionLabelConstants() {
    }
}
