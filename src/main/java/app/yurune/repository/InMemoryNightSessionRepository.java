package app.yurune.repository;

import app.yurune.domain.entity.NightSession;

import java.util.Optional;

/**
 * メモリ上の保存先。
 */
public final class InMemoryNightSessionRepository implements NightSessionRepository {

    private NightSession currentSession;

    @Override
    public Optional<NightSession> findCurrentSession() {
        return Optional.ofNullable(currentSession);
    }

    @Override
    public void saveSession(NightSession session) {
        this.currentSession = session;
    }
}
