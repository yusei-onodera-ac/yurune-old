package app.yurune.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文書の項目を、型をつけて取り出す小さな道具。項目がない、または型が違うときは、保存されたものが壊れているので、例外にする。
 */
final class DocumentFields {

    private DocumentFields() {
    }

    static String text(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalStateException("stored field is not text: " + name);
    }

    static Optional<String> optionalText(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        return value instanceof String text ? Optional.of(text) : Optional.empty();
    }

    static long number(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value instanceof Long number) {
            return number;
        }
        throw new IllegalStateException("stored field is not a number: " + name);
    }

    static Optional<Long> optionalNumber(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        return value instanceof Long number ? Optional.of(number) : Optional.empty();
    }

    static boolean flag(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalStateException("stored field is not a flag: " + name);
    }

    /** 項目がない（古い文書）ときと、型が違うときは、空。後から足した項目を、既定の値で読むために使う。 */
    static Optional<Boolean> optionalFlag(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        return value instanceof Boolean flag ? Optional.of(flag) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("stored field is not a map: " + name);
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalStateException("stored field is not a list: " + name);
    }
}
