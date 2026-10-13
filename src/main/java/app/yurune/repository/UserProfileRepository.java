package app.yurune.repository;

import app.yurune.domain.entity.UserProfile;

/**
 * 利用者ごとの、利用に関する記録の保存先。
 */
public interface UserProfileRepository {

    /** まだ何もないときは、空の記録を返す。 */
    UserProfile findProfile();

    void saveProfile(UserProfile profile);
}
