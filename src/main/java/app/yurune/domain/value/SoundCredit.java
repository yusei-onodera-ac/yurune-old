package app.yurune.domain.value;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 音源の台帳の 1 件（キー、名前、ライセンス、出典の URL、取得日）。
 * 出典の URL は、画面のリンクになる。{@code javascript:} など、https 以外を書けないよう、作るときに確かめる。
 */
public final class SoundCredit {

    private static final String SCHEME_HTTPS = "https";

    private final String key;
    private final String name;
    private final String license;
    private final String sourceUrl;
    private final LocalDate acquiredOn;

    public SoundCredit(String key, String name, String license, String sourceUrl, LocalDate acquiredOn) {
        this.key = requireText(key, "key");
        this.name = requireText(name, "name");
        this.license = requireText(license, "license");
        this.sourceUrl = requireHttpsUrl(sourceUrl);
        this.acquiredOn = Objects.requireNonNull(acquiredOn);
    }

    private static String requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("sound credit " + field + " must not be blank");
        }
        return text;
    }

    private static String requireHttpsUrl(String url) {
        String checked = requireText(url, "source url");
        URI uri = URI.create(checked);
        if (!SCHEME_HTTPS.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("sound credit source url must be an https url");
        }
        return checked;
    }

    /** 音源カタログのキー（計画の音の選択肢と同じ名前）。 */
    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getLicense() {
        return license;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDate getAcquiredOn() {
        return acquiredOn;
    }
}
