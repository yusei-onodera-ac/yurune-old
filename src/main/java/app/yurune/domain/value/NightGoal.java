package app.yurune.domain.value;

/**
 * 夜のめやす（どの値で、夜のすごし方を比べるか）。小さいほどよいものと、大きいほどよいものがある。
 */
public enum NightGoal {

    /** 寝つき。夜の開始から最後の反応まで。小さいほどよい。 */
    ONSET("onset"),
    /** 夜中の再開の回数。小さいほどよい。 */
    WAKE("wake"),
    /** 朝のようす（気分の点）。大きいほどよい。 */
    MORNING("morning");

    /** 設定の初期値（寝つき）。寝つきは、もっとも直接に測れるため。 */
    public static final NightGoal DEFAULT = ONSET;

    private final String id;

    NightGoal(String id) {
        this.id = id;
    }

    public static NightGoal fromId(String id) {
        for (NightGoal goal : values()) {
            if (goal.id.equals(id)) {
                return goal;
            }
        }
        throw new IllegalArgumentException("unknown night goal: " + id);
    }

    public String asId() {
        return id;
    }
}
