package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.SoundCreditConstants;
import app.yurune.domain.value.SoundCredit;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 音源のライセンス台帳。確認していない音源や、書き間違いが、台帳に載らないこと。
 */
class SoundCreditServiceTest {

    private static final LocalDate ACQUIRED_ON = LocalDate.of(2026, 10, 7);
    private static final String LICENSE_NON_COMMERCIAL = "CC BY-NC 4.0";

    /** 試験用の見本。実在の音源ではない（example.com は、例示のために予約された宛先）。 */
    private static SoundCredit sample(String key, String license) {
        return new SoundCredit(key, "試験用の音 " + key, license, "https://example.com/" + key, ACQUIRED_ON);
    }

    @Test
    void theShippedLedgerIsValid() {
        assertEquals(SoundCreditConstants.ENTRIES, new SoundCreditService(SoundCreditConstants.ENTRIES).findCredits());
    }

    @Test
    void entriesComeBackInTheOrderTheyWereWritten() {
        SoundCreditService service = new SoundCreditService(List.of(
                sample("wave", SoundCreditConstants.LICENSE_CC0), sample("rain", SoundCreditConstants.LICENSE_CC0)));

        assertEquals(List.of("wave", "rain"), service.findCredits().stream().map(SoundCredit::getKey).toList());
    }

    @Test
    void aLedgerWithTheSameKeyTwiceIsRejected() {
        List<SoundCredit> ledger = List.of(
                sample("rain", SoundCreditConstants.LICENSE_CC0), sample("rain", SoundCreditConstants.LICENSE_CC0));

        assertThrows(IllegalArgumentException.class, () -> new SoundCreditService(ledger));
    }

    @Test
    void aLicenseOutsideTheAcceptedListIsRejected() {
        List<SoundCredit> ledger = List.of(sample("rain", LICENSE_NON_COMMERCIAL));

        assertThrows(IllegalArgumentException.class, () -> new SoundCreditService(ledger));
    }

    @Test
    void theSourceMustBeAnHttpsUrlBecauseItBecomesALink() {
        for (String url : List.of("javascript:alert(1)", "http://example.com/rain", "https:///rain", "example.com/rain", " ")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SoundCredit("rain", "雨", SoundCreditConstants.LICENSE_CC0, url, ACQUIRED_ON), url);
        }
    }

    @Test
    void everyFieldIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new SoundCredit(" ", "雨", SoundCreditConstants.LICENSE_CC0, "https://example.com/a", ACQUIRED_ON));
        assertThrows(IllegalArgumentException.class,
                () -> new SoundCredit("rain", "", SoundCreditConstants.LICENSE_CC0, "https://example.com/a", ACQUIRED_ON));
        assertThrows(IllegalArgumentException.class,
                () -> new SoundCredit("rain", "雨", null, "https://example.com/a", ACQUIRED_ON));
        assertThrows(NullPointerException.class,
                () -> new SoundCredit("rain", "雨", SoundCreditConstants.LICENSE_CC0, "https://example.com/a", null));
    }

    @Test
    void theAcceptedLicensesAreOnlyCc0ForNow() {
        assertTrue(SoundCreditConstants.ACCEPTED_LICENSES.contains(SoundCreditConstants.LICENSE_CC0));
        assertEquals(1, SoundCreditConstants.ACCEPTED_LICENSES.size());
    }
}
