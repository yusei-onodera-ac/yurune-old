package app.yurune.service;

/**
 * 「ぽてねが覚えていること」で、選択肢に付ける目印。
 */
public enum NoteLabel {

    /** 試した回数が足りない。 */
    TRY_MORE,

    /** 試した回数が足りていて、合っていた割合が最も高い。 */
    TOP_CANDIDATE,

    /** どちらでもない。 */
    NONE
}
