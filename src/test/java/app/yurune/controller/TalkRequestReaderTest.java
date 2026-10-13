package app.yurune.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.ConversationConstants;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * 会話の要求（端末から届く JSON）を、大きさと形を確かめて読むこと。信頼しないデータなので、形が違うものは断る。
 */
class TalkRequestReaderTest {

    private final TalkRequestReader reader = new TalkRequestReader();

    private HttpServletRequest requestWith(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ServletInputStream stream = new ServletInputStream() {
            private final ByteArrayInputStream source = new ByteArrayInputStream(bytes);

            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() throws IOException {
                return source.read();
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> "getInputStream".equals(method.getName()) ? stream : null);
    }

    @Test
    void aWellFormedRequestIsRead() {
        TalkRequest talk = reader.read(requestWith("{\"text\":\"こんにちは\",\"history\":[{\"role\":\"user\",\"text\":\"やあ\"},{\"role\":\"model\",\"text\":\"うん\"}]}"));

        assertEquals("こんにちは", talk.getUtterance());
        assertEquals(2, talk.getHistory().size());
        assertTrue(talk.getHistory().get(0).isFromUser());
        assertEquals("うん", talk.getHistory().get(1).getText());
    }

    @Test
    void theHistoryMayBeLeftOut() {
        assertEquals(0, reader.read(requestWith("{\"text\":\"こんにちは\"}")).getHistory().size());
        assertEquals(0, reader.read(requestWith("{\"text\":\"こんにちは\",\"history\":null}")).getHistory().size());
    }

    @Test
    void oversizedRequestsAreRefused() {
        String huge = "{\"text\":\"" + "あ".repeat(ConversationConstants.REQUEST_MAX_BYTES) + "\"}";

        assertThrows(BadRequestException.class, () -> reader.read(requestWith(huge)));
    }

    @Test
    void malformedRequestsAreRefused() {
        for (String body : new String[] {"", "not json", "[]", "{}", "{\"text\":5}", "{\"text\":null}", "{\"text\":\"x\",\"history\":\"y\"}",
                "{\"text\":\"x\",\"history\":[1]}", "{\"text\":\"x\",\"history\":[{\"role\":\"system\",\"text\":\"y\"}]}",
                "{\"text\":\"x\",\"history\":[{\"role\":\"user\"}]}", "{\"text\":\"x\",\"history\":[{\"role\":7,\"text\":\"y\"}]}"}) {
            assertThrows(BadRequestException.class, () -> reader.read(requestWith(body)), body);
        }
    }

    @Test
    void aVeryLongHistoryIsRefused() {
        StringBuilder history = new StringBuilder();
        for (int index = 0; index < 30; index++) {
            history.append(index == 0 ? "" : ",").append("{\"role\":\"user\",\"text\":\"a\"}");
        }

        assertThrows(BadRequestException.class, () -> reader.read(requestWith("{\"text\":\"x\",\"history\":[" + history + "]}")));
    }
}
