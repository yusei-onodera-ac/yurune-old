package app.yurune.gateway;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore の REST の値（型つきの JSON）と、Java の値（文字列、整数、真偽、null、Map、List）を、相互に変換する。
 */
public final class FirestoreValueCodec {

    private static final String FIELDS = "fields";
    private static final String VALUES = "values";

    public JsonObject encodeFields(Map<String, Object> fields) {
        JsonObject encoded = new JsonObject();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            encoded.add(entry.getKey(), encodeValue(entry.getValue()));
        }
        return encoded;
    }

    public Map<String, Object> decodeFields(JsonObject fields) {
        Map<String, Object> decoded = new LinkedHashMap<>();
        if (fields == null) {
            return decoded;
        }
        for (Map.Entry<String, JsonElement> entry : fields.entrySet()) {
            decoded.put(entry.getKey(), decodeValue(entry.getValue().getAsJsonObject()));
        }
        return decoded;
    }

    @SuppressWarnings("unchecked")
    JsonObject encodeValue(Object value) {
        JsonObject encoded = new JsonObject();
        if (value == null) {
            encoded.add("nullValue", JsonNull.INSTANCE);
        } else if (value instanceof String text) {
            encoded.addProperty("stringValue", text);
        } else if (value instanceof Boolean flag) {
            encoded.addProperty("booleanValue", flag);
        } else if (value instanceof Integer || value instanceof Long) {
            encoded.addProperty("integerValue", String.valueOf(value));
        } else if (value instanceof Map<?, ?> map) {
            JsonObject mapValue = new JsonObject();
            mapValue.add(FIELDS, encodeFields((Map<String, Object>) map));
            encoded.add("mapValue", mapValue);
        } else if (value instanceof List<?> list) {
            JsonArray values = new JsonArray();
            for (Object element : list) {
                values.add(encodeValue(element));
            }
            JsonObject arrayValue = new JsonObject();
            arrayValue.add(VALUES, values);
            encoded.add("arrayValue", arrayValue);
        } else {
            throw new IllegalArgumentException("unsupported value type: " + value.getClass().getName());
        }
        return encoded;
    }

    private Object decodeValue(JsonObject value) {
        if (value.has("stringValue")) {
            return value.get("stringValue").getAsString();
        }
        if (value.has("booleanValue")) {
            return value.get("booleanValue").getAsBoolean();
        }
        if (value.has("integerValue")) {
            return Long.parseLong(value.get("integerValue").getAsString());
        }
        if (value.has("mapValue")) {
            return decodeFields(value.getAsJsonObject("mapValue").getAsJsonObject(FIELDS));
        }
        if (value.has("arrayValue")) {
            List<Object> decoded = new ArrayList<>();
            JsonArray values = value.getAsJsonObject("arrayValue").getAsJsonArray(VALUES);
            if (values != null) {
                for (JsonElement element : values) {
                    decoded.add(decodeValue(element.getAsJsonObject()));
                }
            }
            return decoded;
        }
        return null;
    }
}
