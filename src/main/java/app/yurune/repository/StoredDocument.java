package app.yurune.repository;

import java.util.Map;

/**
 * 文書ストアから読んだ 1 件の文書（文書の名前と、項目）。
 * 項目の値は、文字列、整数（Long）、真偽、null、入れ子の Map、List のどれか。
 */
public final class StoredDocument {

    private final String id;
    private final Map<String, Object> fields;

    public StoredDocument(String id, Map<String, Object> fields) {
        this.id = id;
        this.fields = fields;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getFields() {
        return fields;
    }
}
