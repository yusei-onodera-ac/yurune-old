package app.yurune.repository;

import app.yurune.constant.StorageConstants;

import java.util.regex.Pattern;

/**
 * 利用者ごとのデータの置き場所（users/{uid}/…）の組み立て。
 * uid は、認証した結果だけから作る。画面の入力から作らない。それでも、パスを崩す文字が入らないことは、ここで確かめる。
 */
public final class UserPaths {

    private static final Pattern SAFE_UID = Pattern.compile("[A-Za-z0-9_\\-]{1,128}");
    private static final String SEPARATOR = "/";

    private final String userRoot;

    public UserPaths(String uid) {
        if (uid == null || !SAFE_UID.matcher(uid).matches()) {
            throw new IllegalArgumentException("uid has unsafe characters");
        }
        this.userRoot = StorageConstants.USERS + SEPARATOR + uid;
    }

    /** 利用者の文書のパス。データの消去は、ここから下をすべて消す。 */
    public String userDocument() {
        return userRoot;
    }

    public String collection(String name) {
        return userRoot + SEPARATOR + name;
    }

    public String document(String collectionName, String documentId) {
        return collection(collectionName) + SEPARATOR + documentId;
    }
}
