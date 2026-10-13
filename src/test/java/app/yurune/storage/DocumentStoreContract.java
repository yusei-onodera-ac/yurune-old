package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.repository.DocumentStore;
import app.yurune.repository.StoredDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 文書ストアが満たすべき約束。メモリ上のものと、Firestore（エミュレータ）のものが、同じ答えを返すことを確かめる。
 */
abstract class DocumentStoreContract {

    protected DocumentStore store;

    protected abstract DocumentStore createStore() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        store = createStore();
    }

    private Map<String, Object> fields(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return map;
    }

    private List<String> ids(List<StoredDocument> documents) {
        return documents.stream().map(StoredDocument::getId).toList();
    }

    @Test
    void valuesComeBackWithTheirTypes() {
        Map<String, Object> written = fields("text", "雨の音", "count", 7L, "flag", true, "nothing", null,
                "nested", fields("a", "x", "b", 2L), "items", List.of("sound", "fade"));

        store.set("users/u1/arms/sound__rain", written);

        Map<String, Object> read = store.get("users/u1/arms/sound__rain").orElseThrow();
        assertEquals("雨の音", read.get("text"));
        assertEquals(7L, read.get("count"));
        assertEquals(true, read.get("flag"));
        assertTrue(read.containsKey("nothing") && read.get("nothing") == null);
        assertEquals(fields("a", "x", "b", 2L), read.get("nested"));
        assertEquals(List.of("sound", "fade"), read.get("items"));
    }

    @Test
    void aMissingDocumentIsEmptyAndDeletingItIsHarmless() {
        assertTrue(store.get("users/u1/arms/none").isEmpty());
        store.delete("users/u1/arms/none");
    }

    @Test
    void setReplacesTheWholeDocument() {
        store.set("users/u1/arms/a", fields("old", "value", "keep", "me"));
        store.set("users/u1/arms/a", fields("new", "value"));

        assertEquals(fields("new", "value"), store.get("users/u1/arms/a").orElseThrow());
    }

    @Test
    void listReturnsOnlyDirectChildrenInIdOrder() {
        store.set("users/u1/arms/b", fields("n", 2L));
        store.set("users/u1/arms/a", fields("n", 1L));
        store.set("users/u1/arms/a/deeper/x", fields("n", 9L));
        store.set("users/u1/nights/2026-10-07", fields("n", 3L));

        assertEquals(List.of("a", "b"), ids(store.list("users/u1/arms")));
    }

    @Test
    void listReadsEveryPage() {
        for (int index = 0; index < 350; index++) {
            store.set("users/u1/many/d" + String.format("%04d", index), fields("n", (long) index));
        }

        assertEquals(350, store.list("users/u1/many").size());
    }

    @Test
    void queryOrdersByAFieldAndLimits() {
        store.set("users/u1/nights/x1", fields("wakeDate", "2026-10-05"));
        store.set("users/u1/nights/x2", fields("wakeDate", "2026-10-07"));
        store.set("users/u1/nights/x3", fields("wakeDate", "2026-10-06"));
        store.set("users/u1/nights/x4", fields("other", "no wake date"));

        assertEquals(List.of("x2"), ids(store.queryOrderedByField("users/u1/nights", "wakeDate", true, 1)));
        assertEquals(List.of("x2", "x3", "x1"), ids(store.queryOrderedByField("users/u1/nights", "wakeDate", true, 10)));
        assertEquals(List.of("x1", "x3"), ids(store.queryOrderedByField("users/u1/nights", "wakeDate", false, 2)));
    }

    @Test
    void queryFieldEqualsReturnsMatchesInIdOrder() {
        store.set("users/u1/decisions/n1_b", fields("nightId", "2026-10-07"));
        store.set("users/u1/decisions/n1_a", fields("nightId", "2026-10-07"));
        store.set("users/u1/decisions/n2_a", fields("nightId", "2026-10-06"));

        assertEquals(List.of("n1_a", "n1_b"), ids(store.queryFieldEquals("users/u1/decisions", "nightId", "2026-10-07")));
        assertTrue(store.queryFieldEquals("users/u1/decisions", "nightId", "2026-01-01").isEmpty());
    }

    @Test
    void deleteTreeRemovesTheDocumentAndEverythingBelowButNothingElse() {
        store.set("users/u1", fields("created", true));
        store.set("users/u1/arms/a", fields("n", 1L));
        store.set("users/u1/nights/n1", fields("n", 1L));
        store.set("users/u1/nights/n1/sub/deep", fields("n", 1L));
        store.set("users/u2/arms/a", fields("n", 1L));
        store.set("users/u10/arms/a", fields("n", 1L));

        store.deleteTree("users/u1");

        assertTrue(store.get("users/u1").isEmpty());
        assertTrue(store.list("users/u1/arms").isEmpty());
        assertTrue(store.list("users/u1/nights").isEmpty());
        assertTrue(store.get("users/u1/nights/n1/sub/deep").isEmpty());
        assertEquals(List.of("a"), ids(store.list("users/u2/arms")));
        assertEquals(List.of("a"), ids(store.list("users/u10/arms")), "前の部分が同じ名前の別の利用者を、巻き込まない");
    }

    protected List<String> collectIds(List<StoredDocument> documents) {
        return new ArrayList<>(ids(documents));
    }
}
