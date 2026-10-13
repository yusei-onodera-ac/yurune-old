package app.yurune.service;

import app.yurune.constant.SoundCreditConstants;
import app.yurune.domain.value.SoundCredit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 音源のライセンス台帳を、画面に出すために返す。
 * 台帳の書き間違い（キーの重複、受け入れていないライセンス）は、起動のときに気づけるよう、作るときに検査する。
 */
public final class SoundCreditService {

    private final List<SoundCredit> credits;

    public SoundCreditService(List<SoundCredit> ledger) {
        Set<String> keys = new HashSet<>();
        for (SoundCredit credit : ledger) {
            if (!SoundCreditConstants.ACCEPTED_LICENSES.contains(credit.getLicense())) {
                throw new IllegalArgumentException("license is not accepted: " + credit.getKey());
            }
            if (!keys.add(credit.getKey())) {
                throw new IllegalArgumentException("duplicated sound key: " + credit.getKey());
            }
        }
        this.credits = List.copyOf(ledger);
    }

    /** 台帳に書いた順。 */
    public List<SoundCredit> findCredits() {
        return credits;
    }
}
