package app.yurune.constant;

/**
 * 「ゆるり」の方針の数値と上限（仮置き。根拠なし、と各項目に明記する）。
 * 方針の数値を一か所に集め、規則を変えたときに、どこを直せばよいか分かるようにする。
 */
public final class GentlePolicy {

    /**
     * ループの規則の版。夜の記録に残し、あとから「どの規則で測った夜か」を区別できるようにする。
     * 規則を変えたら上げる。版が 0 の記録は、版が古い（不明）の意味。
     * 1 → 2: FADE（音量の下げ方。計画の 15/30/45 分）が、記録・集計だけで、夜の動作には
     * 使われていなかったのを直し、ResilientNightAgent が、夜のあいだ実際に音量を下げるようにした。
     * 版 1 以前の夜は、FADE が動作に効いていない夜なので、版 2 以降の夜と、厳密には同じ条件で比べられない。
     * 今回は、版をまたいで比べない仕組み（NightRecord.policyVersion でのフィルタ）は、実装していない
     * （このアプリはこれから初めて使われるため、版の混在は理論上だけで、実害は小さいと判断した）。
     */
    public static final int POLICY_VERSION = 2;

    /**
     * 最初の何夜を、基準（標準プランの夜）にするか。仮置き。根拠なし。
     * 基準の中央値とばらつきを見るのに、少なすぎる夜数で決めないため。
     */
    public static final int BASELINE_NIGHTS = 7;

    /**
     * 基準のあと、何夜に 1 夜を対照（標準プラン）にするか。仮置き。根拠なし。
     * 変えた夜だけを続けると、夜の違いと日々の違いが混ざるため、同じ時期の対照を残す。
     */
    public static final int CONTROL_EVERY_NIGHTS = 3;

    /**
     * 同じ「変える」夜が、連続で続く上限。仮置き。根拠なし（M2r で 3 → 8 に緩めた。理由: 設計根拠R5。
     * 要因を順番にしか試さなくなった（設計根拠R3。決定記録の規則R3「外す」とは別の番号づけ）ので、
     * 一つの選択肢に夜数を集めやすくし、検定に要る夜数を早くそろえるため）。
     */
    public static final int MAX_CONSECUTIVE_VARIANT = 8;

    /**
     * 本検定（確認・見直し）を行う周期（夜）。設計根拠R1: 毎晩は、集計の更新と、戻す（ROLLBACK）だけ。仮置き。根拠なし。
     * 基準の最終夜から、この夜数ごとに見直しの日になる（日付から決まるので、同じ日は同じ判定になる）。
     */
    public static final int REVIEW_EVERY_NIGHTS = 7;

    /**
     * 1 つの要因（音、音量の下げ方）を試す夜数の上限。設計根拠R3: これに届いたら、まだ判定が出ていない選択肢があっても、次の要因へ進む。
     * 仮置き。根拠なし。シミュレータで 24 夜を試したところ、2 つの選択肢に分かれる分（1 つに約 12 夜）では、
     * 符号反転検定と 2 週連続の確認（設計根拠R3）に足りる夜数がそろう前に音量の下げ方へ進んでしまい、S1・S3・S7 の検出力が崩れた
     * （検出力の曲線がとても早く頭打ちになった）ため、84 夜の予算の大半を、音（効果の対象）に使えるよう緩めた。
     */
    public static final int FACTOR_MAX_VARIANT_NIGHTS = 56;

    /**
     * 変えた夜と、時間が近い対照夜との、ペアを作るときの日数の範囲（前後）。設計根拠R2: ドリフト対策。仮置き。根拠なし。
     */
    public static final int PAIR_WINDOW_DAYS = 3;

    /** 1 つの変えた夜に対して、ペアにする対照夜の数の上限。設計根拠R2。仮置き。根拠なし。 */
    public static final int MAX_PAIRED_CONTROLS = 2;

    /**
     * 符号反転の並べ替え検定の、片側の有意水準（設計根拠R3で、同じ要因の選択肢の数 k で割る。Bonferroni）。仮置き。根拠なし。
     */
    public static final double SIGNIFICANCE_ALPHA = 0.05;

    /**
     * 寝つきのめやすで、意味があると見なす最小の差（分）。仮置き。根拠なし。
     * これより小さい差は、端末が触らなくなった時刻のぶれの範囲とみなし、良くも悪くもないとする。
     */
    public static final int MIN_ONSET_DIFF_MIN = 10;

    /**
     * 夜中の再開の回数で、意味があると見なす最小の差（回）。仮置き。根拠なし。
     * 1 回のずれは、再開の押し忘れや押し間違いでも起きうるため、判定では夜数と合わせて見る。
     */
    public static final int MIN_WAKE_DIFF = 1;

    /**
     * 朝のようすの点数で、意味があると見なす最小の差（点）。仮置き。根拠なし。
     * 朝の答えは気分の選び方に左右されやすいので、小さい差は見ない。
     */
    public static final double MIN_MORNING_DIFF = 0.3;

    /**
     * 基準の延長の上限（夜）。仮置き。根拠なし。
     * 寝つきが記録されない人（利用者が自分で終える夜が多い人）が、ずっと基準のままにならないよう、ここで打ち切る。
     */
    public static final int MAX_BASELINE_NIGHTS = 21;

    /**
     * 基準として使える（寝つきの値があり、利用者が変えていない）夜の、下限（夜）。仮置き。根拠なし。
     * これに届くまで、基準を延ばす（上限は MAX_BASELINE_NIGHTS）。
     */
    public static final int MIN_VALID_BASELINE_NIGHTS = 5;

    /**
     * 比べられる（近い対照夜とペアにできた）差の下限（夜）。仮置き。根拠なし（M2r で、群ごとの下限から、ペアの数の下限に意味を変えた。
     * 理由: 設計根拠R2。時間が近い対照とのペアに変えたため、「変えた夜」と「標準の夜」で別々の下限を持つ意味がなくなった）。
     * 夜数が少ないうちは、中央値も検定も、偶然の差を拾うため、結論を出さない。
     */
    public static final int MIN_NIGHTS_PER_GROUP = 5;

    /**
     * 「変えた夜のうち、よい（わるい）夜の割合」の下限。仮置き。根拠なし。
     * 中央値の差だけでは、一部の夜の外れ値を拾うため、夜ごとの向きも揃っていることを求める。
     */
    public static final double CONSISTENCY_RATIO = 0.6;

    /**
     * 順位の検定（Mann-Whitney の正規近似）の z の下限。片側 5% の目安（1.645）。仮置き。根拠なし。
     * 選択肢を複数見比べるので、実際の誤りの率は、この目安より高くなりうる（多重比較の補正はしない。既知の制限）。
     */
    public static final double SIGNIFICANCE_Z = 1.645;

    /** 候補から外す期間（日）。仮置き。根拠なし。外したものを、すぐに試し直さないため。 */
    public static final int QUARANTINE_DAYS = 14;

    /**
     * 見送り（PARK、SIMILAR）にしたあと、再挑戦（通常の候補と同じ扱いに戻す）までの間隔（日）。仮置き。根拠なし。
     * 失敗の原因の診断（FailureDiagnosisTest）で、見送りには、もともと期限が無く、他方の候補が片付くまで夜数が集まらないことが、
     * 検出力不足（S1 の取りこぼしの多く）の一因と分かったため、隔離（QUARANTINE_DAYS）と同じ長さの間隔を仮に置く。
     */
    public static final int PARK_RETRY_DAYS = 14;

    /** 戻すかどうかを見る、直近の変えた夜の数。仮置き。根拠なし。 */
    public static final int ROLLBACK_NIGHTS = 3;

    /** 戻したあと、標準のやり方で過ごす夜の数。仮置き。根拠なし。 */
    public static final int ROLLBACK_HOLD_NIGHTS = 2;

    /** 候補から外すかどうかを見る、その選択肢の直近の答えの数。仮置き。根拠なし。 */
    public static final int REJECT_RECENT_NIGHTS = 3;

    /** 「合わなかった」が、この回数以上なら、その選択肢は候補から外す。仮置き。根拠なし。 */
    public static final int REJECT_MAJORITY_COUNT = 2;

    /**
     * 「多数」と見るときの割合（半分より多く）。「合わなかった」が多数のとき、その選択肢は外す。
     * 半分は、多数決の素直な境目（仮置き。根拠なし）。
     */
    public static final double MAJORITY_RATIO = 0.5;

    /**
     * 受診のすすめを考え始める、「よく使う」選択肢が見つからないままの夜数（8 週間）。設計根拠R6: 仮置き。根拠なし。
     * 悪くなっていることそのものの検出は、ここでは見ない（ROLLBACK で扱う。設計根拠R6 は「あまり役に立っていない」ことの通知。
     * 決定記録の規則R6「受診のすすめ」とは別の番号づけ）。
     */
    public static final int REFER_NO_PREFERRED_NIGHTS = 56;

    /** 停滞を見る、直近の対照夜（標準のやり方の夜）の数。設計根拠R6: 仮置き。根拠なし。 */
    public static final int REFER_RECENT_NIGHTS = 14;

    /** 受診のすすめを、もう一度出すまでの日数。仮置き。根拠なし。繰り返し出さないため。 */
    public static final int REFER_COOLDOWN_DAYS = 14;

    /**
     * 見直しに読む、直近の夜の数（起きた日の新しい順）。仮置き。根拠なし。
     * 夜ごとに読むので、件数は抑える。基準の夜は、この件数より前にあるとき読めない（既知の制限）。
     */
    public static final int REVIEW_HISTORY_NIGHTS = 365;

    /**
     * よく選ぶと決めた選択肢を、何日に 1 回、優先して候補にするか。仮置き。根拠なし。
     * 毎晩その選択肢だけにすると、ほかの選択肢を試せなくなるため、半分の日だけ優先する。
     */
    public static final int PREFERRED_PICK_EVERY_DAYS = 2;

    /** 気分の点（穏やか）。仮置き。画面には出さない。 */
    public static final int MOOD_CALM_POINT = 2;

    /** 気分の点（ふつう）。仮置き。画面には出さない。 */
    public static final int MOOD_NORMAL_POINT = 1;

    /** 気分の点（そのほか）。仮置き。画面には出さない。 */
    public static final int MOOD_OTHER_POINT = 0;

    private GentlePolicy() {
    }
}
