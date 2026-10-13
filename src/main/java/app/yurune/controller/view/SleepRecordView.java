package app.yurune.controller.view;

import app.yurune.domain.entity.SleepRecord;

/**
 * 1 晩の記録の表示用。記録のない日は、時刻の文字を空にする。
 */
public final class SleepRecordView {

    private final String isoDate;
    private final String dateLabel;
    private final boolean recorded;
    private final String bedText;
    private final String wakeText;
    private final String durationText;
    private final String durationClockText;

    public SleepRecordView(SleepRecord record) {
        this.isoDate = record.getWakeDate().asIsoText();
        this.dateLabel = record.getWakeDate().asLabelText();
        this.recorded = record.isRecorded();
        this.bedText = recorded ? record.getBedTime().asText() : "";
        this.wakeText = recorded ? record.getWakeTime().asText() : "";
        this.durationText = recorded ? record.getDuration().asHourMinuteText() : "";
        this.durationClockText = recorded ? record.getDuration().asClockText() : "—";
    }

    public String getIsoDate() {
        return isoDate;
    }

    public String getDateLabel() {
        return dateLabel;
    }

    public boolean isRecorded() {
        return recorded;
    }

    public String getBedText() {
        return bedText;
    }

    public String getWakeText() {
        return wakeText;
    }

    public String getDurationText() {
        return durationText;
    }

    public String getDurationClockText() {
        return durationClockText;
    }
}
