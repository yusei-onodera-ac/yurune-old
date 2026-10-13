package app.yurune.constant;

import app.yurune.domain.value.SoundCredit;

import java.util.List;
import java.util.Set;

/**
 * 音源のライセンス台帳（仮置き: 空）。
 * 空なのは、いまのゆるねに、外部の音源のファイルが入っていないため。確認していない音源を、台帳に書かない。
 * 音源を入れるときは、ライセンスを確認してから、{@link SoundCredit} を 1 件ずつ足す（画面は、これを表示するだけ）。
 */
public final class SoundCreditConstants {

    public static final String LICENSE_CC0 = "CC0 1.0";

    /**
     * 台帳に書いてよいライセンス。音源は、ライセンスを確認できた CC0 のものだけを暫定に使う（仕様 U-09）ため。
     * 商用不可のもの（CC BY-NC など）が、書き間違いで載らないようにする。広げるときは、利用者の決定のあとに、ここを変える。
     */
    public static final Set<String> ACCEPTED_LICENSES = Set.of(LICENSE_CC0);

    public static final List<SoundCredit> ENTRIES = List.of();

    private SoundCreditConstants() {
    }
}
