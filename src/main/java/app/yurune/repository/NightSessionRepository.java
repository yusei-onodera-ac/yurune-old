package app.yurune.repository;

import app.yurune.domain.entity.NightSession;

import java.util.Optional;

/**
 * いま進んでいる夜の状態の保存先。
 */
public interface NightSessionRepository {

    Optional<NightSession> findCurrentSession();

    void saveSession(NightSession session);
}
