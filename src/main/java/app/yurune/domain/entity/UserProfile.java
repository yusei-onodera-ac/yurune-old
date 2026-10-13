package app.yurune.domain.entity;

import app.yurune.domain.value.UserSettings;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 利用者ごとの、利用に関する記録（同意したときの規約の版と時刻、審査用サンプルを置いたか、設定画面で決めた設定）。
 * メールアドレスは、保存しない（認証の仕組みだけが持つ）。
 */
public final class UserProfile {

    private final String consentVersion;
    private final Instant consentedAt;
    private final boolean seeded;
    private final UserSettings settings;

    private UserProfile(String consentVersion, Instant consentedAt, boolean seeded, UserSettings settings) {
        this.consentVersion = consentVersion;
        this.consentedAt = consentedAt;
        this.seeded = seeded;
        this.settings = Objects.requireNonNull(settings);
    }

    public static UserProfile empty() {
        return new UserProfile(null, null, false, UserSettings.defaults());
    }

    /** 設定のない、古い保存データから作る。設定は、初期値になる。 */
    public static UserProfile restore(String consentVersion, Instant consentedAt, boolean seeded) {
        return new UserProfile(consentVersion, consentedAt, seeded, UserSettings.defaults());
    }

    public static UserProfile restore(String consentVersion, Instant consentedAt, boolean seeded,
            UserSettings settings) {
        return new UserProfile(consentVersion, consentedAt, seeded, settings);
    }

    public UserProfile withConsent(String version, Instant at) {
        return new UserProfile(version, at, seeded, settings);
    }

    public UserProfile withSeeded() {
        return new UserProfile(consentVersion, consentedAt, true, settings);
    }

    public UserProfile withSettings(UserSettings newSettings) {
        return new UserProfile(consentVersion, consentedAt, seeded, newSettings);
    }

    public Optional<String> findConsentVersion() {
        return Optional.ofNullable(consentVersion);
    }

    public Optional<Instant> findConsentedAt() {
        return Optional.ofNullable(consentedAt);
    }

    public boolean isSeeded() {
        return seeded;
    }

    public UserSettings getSettings() {
        return settings;
    }
}
