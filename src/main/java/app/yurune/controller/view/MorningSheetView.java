package app.yurune.controller.view;

import app.yurune.service.MorningSheet;

/**
 * 朝のひとことの、推定した時刻の表示用。就寝時刻が分からないときは、bedKnown が false。
 */
public final class MorningSheetView {

    private final boolean bedKnown;
    private final String bedText;
    private final String wakeText;

    public MorningSheetView(MorningSheet sheet) {
        this.bedKnown = sheet.findBedTime().isPresent();
        this.bedText = sheet.findBedTime().map(time -> time.asText()).orElse("");
        this.wakeText = sheet.getWakeTime().asText();
    }

    public boolean isBedKnown() {
        return bedKnown;
    }

    public String getBedText() {
        return bedText;
    }

    public String getWakeText() {
        return wakeText;
    }
}
