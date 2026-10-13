package app.yurune.sample;

import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.OptionName;

import java.util.List;

/**
 * 審査用サンプルの 1 晩（日付、試した音、会話、音量の下げ方、朝の答え）。
 */
public final class SampleNight {

    private final String dateText;
    private final String soundName;
    private final String talkName;
    private final int fadeMinutes;
    private final int answerScore;

    public SampleNight(String dateText, String soundName, String talkName, int fadeMinutes, int answerScore) {
        this.dateText = dateText;
        this.soundName = soundName;
        this.talkName = talkName;
        this.fadeMinutes = fadeMinutes;
        this.answerScore = answerScore;
    }

    public String getDateText() {
        return dateText;
    }

    public OptionName getOption(Dimension dimension) {
        return switch (dimension) {
            case SOUND -> new OptionName(soundName);
            case TALK -> new OptionName(talkName);
            case FADE -> new OptionName(String.valueOf(fadeMinutes));
            default -> throw new IllegalArgumentException("sample has no " + dimension);
        };
    }

    public List<ArmKey> getTriedKeys() {
        return List.of(
                new ArmKey(Dimension.SOUND, getOption(Dimension.SOUND)),
                new ArmKey(Dimension.TALK, getOption(Dimension.TALK)),
                new ArmKey(Dimension.FADE, getOption(Dimension.FADE)));
    }

    public MorningAnswer getAnswer() {
        return MorningAnswer.fromScore(answerScore);
    }
}
