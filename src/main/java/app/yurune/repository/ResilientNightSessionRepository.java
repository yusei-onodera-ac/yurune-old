package app.yurune.repository;

import app.yurune.domain.entity.NightSession;

import java.util.Optional;

/**
 * 夜の状態の保存先が止まっても、夜の音と自動終了を止めないための包み。
 * 書き込みは、まずこの場のメモリに置き、保存先に書けなければ、次の書き込みで書き直す。
 * 読み込みは、保存先を優先する（別のサーバーが更新しているかもしれないため）。保存先が止まっているときと、
 * まだ書けていない新しい状態があるときだけ、メモリのものを返す。
 */
public final class ResilientNightSessionRepository implements NightSessionRepository {

    private static final System.Logger LOGGER = System.getLogger(ResilientNightSessionRepository.class.getName());

    private final NightSessionRepository primary;
    private NightSession latest;
    private boolean unsaved;
    private boolean reportedDown;

    public ResilientNightSessionRepository(NightSessionRepository primary) {
        this.primary = primary;
    }

    @Override
    public synchronized Optional<NightSession> findCurrentSession() {
        if (unsaved && trySave(latest)) {
            unsaved = false;
        }
        if (unsaved) {
            return Optional.of(latest);
        }
        try {
            Optional<NightSession> stored = primary.findCurrentSession();
            stored.ifPresent(session -> latest = session);
            return stored;
        } catch (StorageUnavailableException exception) {
            reportDown();
            return Optional.ofNullable(latest);
        }
    }

    @Override
    public synchronized void saveSession(NightSession session) {
        latest = session;
        unsaved = !trySave(session);
    }

    private void reportDown() {
        if (!reportedDown) {
            LOGGER.log(System.Logger.Level.WARNING, "night session store is down; using the copy in memory");
            reportedDown = true;
        }
    }

    private boolean trySave(NightSession session) {
        try {
            primary.saveSession(session);
            reportedDown = false;
            return true;
        } catch (StorageUnavailableException exception) {
            reportDown();
            return false;
        }
    }
}
