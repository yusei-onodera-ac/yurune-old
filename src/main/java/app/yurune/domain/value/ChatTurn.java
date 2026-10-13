package app.yurune.domain.value;

/**
 * 直前までのやり取りの 1 回分（誰が、何を言ったか）。端末が持ち、会話のたびに送ってくる。サーバーは保存しない。
 * 端末から届くものは、信頼しないデータとして、検査し直してから使う。
 */
public final class ChatTurn {

    private final boolean fromUser;
    private final String text;

    public ChatTurn(boolean fromUser, String text) {
        this.fromUser = fromUser;
        this.text = text;
    }

    public boolean isFromUser() {
        return fromUser;
    }

    public String getText() {
        return text;
    }
}
