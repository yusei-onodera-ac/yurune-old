package app.yurune.service;

import app.yurune.domain.entity.UserProfile;
import app.yurune.domain.value.SettingItem;
import app.yurune.domain.value.UserSettings;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.repository.UserProfileRepository;

/**
 * 1 人の利用者の設定（夜の会話、音量の上限、ぽてねの声、好みを覚える）の、読み出しと変更。
 * 設定は、夜の制御、会話、学習が、そのつど読む（コードが守る。画面で隠すだけにしない）。
 * 変更は、選べる値だけを受け付け、新しい値を作って保存する。消すのは「データを消す」だけで、ここでは消さない。
 */
public final class SettingsService {

    private static final System.Logger LOGGER = System.getLogger(SettingsService.class.getName());

    private final UserProfileRepository profileRepository;
    private UserSettings lastKnown;

    public SettingsService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /** 設定画面に出す設定。保存先が使えないときは、そのまま例外にする（古い値や、仮の値を、設定として見せない）。 */
    public synchronized UserSettings findSettings() {
        UserSettings stored = profileRepository.findProfile().getSettings();
        lastKnown = stored;
        return stored;
    }

    /**
     * 夜の制御、会話、学習が守る設定。保存先が止まっているときは、直前に読めた値を使う。
     * 直前の値もないときは、守る側へ倒した値（会話なし、音量は最小の上限、学習なし）にして、夜の音と自動終了を止めない。
     */
    public synchronized UserSettings findEnforcedSettings() {
        try {
            return findSettings();
        } catch (StorageUnavailableException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "settings store is down; using the last known settings");
            return lastKnown != null ? lastKnown : UserSettings.cautious();
        }
    }

    /**
     * 1 項目を変えて保存し、変えたあとの設定を返す。選べない値のときは IllegalArgumentException（何も保存しない）。
     * 同じ利用者の変更は、1 つずつ順に行う（2 つの項目を続けて変えても、片方が消えない）。
     */
    public synchronized UserSettings changeSetting(SettingItem item, String valueId) {
        UserProfile profile = profileRepository.findProfile();
        UserSettings changed = profile.getSettings().change(item, valueId);
        profileRepository.saveProfile(profile.withSettings(changed));
        lastKnown = changed;
        return changed;
    }
}
