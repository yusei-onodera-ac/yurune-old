package app.yurune.controller;

import app.yurune.domain.value.ChatTurn;

import java.util.List;

/**
 * 端末から届いた、会話の要求（いまの発言と、直前までのやり取り）。信頼しないデータで、検査は Service が行う。
 */
final class TalkRequest {

    private final String utterance;
    private final List<ChatTurn> history;

    TalkRequest(String utterance, List<ChatTurn> history) {
        this.utterance = utterance;
        this.history = List.copyOf(history);
    }

    String getUtterance() {
        return utterance;
    }

    List<ChatTurn> getHistory() {
        return history;
    }
}
