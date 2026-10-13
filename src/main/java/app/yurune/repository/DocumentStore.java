package app.yurune.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文書（項目の集まり）を、パスで読み書きする保存先の窓口。Firestore の形に合わせてある。
 * パスは「コレクション/文書/コレクション/文書…」の形で、文書のパスは、要素の数が偶数。
 * Repository は、この窓口だけを使い、Firestore の通信を知らない。
 */
public interface DocumentStore {

    Optional<Map<String, Object>> get(String documentPath);

    /** 文書を、まるごと置き換える（なければ、作る）。 */
    void set(String documentPath, Map<String, Object> fields);

    /** なくても、失敗にしない。 */
    void delete(String documentPath);

    /** コレクションの全文書を、文書の名前の順に返す。 */
    List<StoredDocument> list(String collectionPath);

    /** コレクションを、ある項目の順に並べて、先頭から limit 件返す。項目がない文書は含まれない。 */
    List<StoredDocument> queryOrderedByField(String collectionPath, String field, boolean descending, int limit);

    /** ある項目が、指定した文字列に等しい文書を、文書の名前の順に返す。 */
    List<StoredDocument> queryFieldEquals(String collectionPath, String field, String value);

    /** 文書と、その下のすべてのコレクション・文書を消す（データの消去）。 */
    void deleteTree(String documentPath);
}
