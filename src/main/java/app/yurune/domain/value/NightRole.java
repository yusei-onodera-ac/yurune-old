package app.yurune.domain.value;

/**
 * 夜の役割。基準（標準プラン）、対照（標準プラン）、変える（要因を 1 つだけ変える）の 3 つ。
 */
public enum NightRole {

    BASELINE("baseline"),
    CONTROL("control"),
    VARIANT("variant");

    private final String id;

    NightRole(String id) {
        this.id = id;
    }

    public static NightRole fromId(String id) {
        for (NightRole role : values()) {
            if (role.id.equals(id)) {
                return role;
            }
        }
        throw new IllegalArgumentException("unknown night role: " + id);
    }

    public String asId() {
        return id;
    }
}
