package app.yurune.controller;

import app.yurune.constant.ConversationConstants;
import app.yurune.domain.value.ChatTurn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 会話の要求（JSON）を、大きさと形を確かめながら読む。形が違うとき、大きすぎるときは、BadRequestException。
 * 中身（発言の意味）の検査は、ここでは行わない（Service の InputGuard が行う）。
 */
final class TalkRequestReader {

    /** 端末が送ってよい、やり取りの数。多く送っても、LLM に渡すのは、さらに少ない件数。 */
    private static final int MAX_HISTORY_ITEMS = 12;

    TalkRequest read(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readNBytes(ConversationConstants.REQUEST_MAX_BYTES + 1);
            if (body.length > ConversationConstants.REQUEST_MAX_BYTES) {
                throw new BadRequestException("talk request is too large");
            }
            JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            return new TalkRequest(readText(json, "text"), readHistory(json.get("history")));
        } catch (IOException | JsonParseException | IllegalStateException | ClassCastException exception) {
            throw new BadRequestException("invalid talk request", exception);
        }
    }

    private String readText(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new BadRequestException("talk request has no text");
        }
        return element.getAsString();
    }

    private List<ChatTurn> readHistory(JsonElement element) {
        List<ChatTurn> history = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return history;
        }
        JsonArray items = element.getAsJsonArray();
        if (items.size() > MAX_HISTORY_ITEMS) {
            throw new BadRequestException("talk history is too long");
        }
        for (JsonElement item : items) {
            JsonObject turn = item.getAsJsonObject();
            String role = readText(turn, "role");
            if (!ConversationConstants.ROLE_USER.equals(role) && !ConversationConstants.ROLE_MODEL.equals(role)) {
                throw new BadRequestException("talk history has an unknown role");
            }
            history.add(new ChatTurn(ConversationConstants.ROLE_USER.equals(role), readText(turn, "text")));
        }
        return history;
    }
}
