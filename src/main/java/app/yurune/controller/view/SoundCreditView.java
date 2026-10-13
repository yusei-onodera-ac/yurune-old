package app.yurune.controller.view;

import app.yurune.constant.InfoConstants;
import app.yurune.domain.value.SoundCredit;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * JSP に渡す、音源の台帳の 1 件。JSP が Value の型を直接知らないようにする。
 */
public final class SoundCreditView {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(InfoConstants.CREDIT_DATE_PATTERN, Locale.JAPAN);

    private final String keyText;
    private final String nameText;
    private final String licenseText;
    private final String sourceUrl;
    private final String acquiredText;

    public SoundCreditView(SoundCredit credit) {
        this.keyText = credit.getKey();
        this.nameText = credit.getName();
        this.licenseText = credit.getLicense();
        this.sourceUrl = credit.getSourceUrl();
        this.acquiredText = DATE_FORMAT.format(credit.getAcquiredOn());
    }

    public String getKeyText() {
        return keyText;
    }

    public String getNameText() {
        return nameText;
    }

    public String getLicenseText() {
        return licenseText;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getAcquiredText() {
        return acquiredText;
    }
}
