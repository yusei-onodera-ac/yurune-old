package app.yurune.domain.value;

/**
 * 制御が縮退した・復帰した状態の名前（運用の記録 control ログ用）。
 * kind ごとに使う値は決まっている（breaker は OPEN/HALF_OPEN/CLOSED、停止スイッチは ON/OFF、
 * Armor・保存先は DOWN/UP、呼び出しの上限は NIGHT/TALK/DAILY）が、型は 1 つにまとめる。
 * ログに出る文字列は、これまでと同じ（deploy/monitoring の検索が、この文字列で引くため、変えない）。
 */
public enum ControlState {

    CLOSED("closed"),
    OPEN("open"),
    HALF_OPEN("half_open"),
    ON("on"),
    OFF("off"),
    DOWN("down"),
    UP("up"),
    NIGHT("night"),
    TALK("talk"),
    DAILY("daily");

    private final String id;

    ControlState(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
