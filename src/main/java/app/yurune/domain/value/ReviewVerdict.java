package app.yurune.domain.value;

/**
 * 変えた夜と、標準の夜を比べた判定。まだ夜数が足りないときは NEED_MORE で、結論を出さない。
 */
public enum ReviewVerdict {

    NEED_MORE("need_more"),
    BETTER("better"),
    WORSE("worse"),
    SIMILAR("similar");

    private final String id;

    ReviewVerdict(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
