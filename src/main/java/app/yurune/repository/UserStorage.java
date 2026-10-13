package app.yurune.repository;

/**
 * 利用者ごとの保存先を開く窓口。uid ごとに、その人のデータだけを読み書きできる一式を返す。
 */
public interface UserStorage {

    UserRepositories openFor(String uid);

    /** その利用者のデータ（users/{uid} 以下）を、すべて消す。認証のアカウントは消さない。 */
    void eraseAll(String uid);
}
