package app.yurune.domain.value;

/**
 * 設定画面で変えられる項目。画面から届く名前（ID）は、ここにある 6 つだけ。
 */
public enum SettingItem {

    NIGHT_TALK("night_talk"),
    VOLUME_CAP("volume_cap"),
    VOICE("voice"),
    LEARNING("learning"),
    GOAL("night_goal"),
    KEEP_SCREEN_ON("keep_screen_on");

    private final String id;

    SettingItem(String id) {
        this.id = id;
    }

    /** 画面から届いた ID から作る。ないときと、知らない名前のときは、IllegalArgumentException。 */
    public static SettingItem fromId(String id) {
        for (SettingItem item : values()) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        throw new IllegalArgumentException("unknown setting item: " + id);
    }

    public String asId() {
        return id;
    }
}
