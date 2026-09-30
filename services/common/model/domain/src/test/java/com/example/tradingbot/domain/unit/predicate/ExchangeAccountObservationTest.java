package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Возраст наблюдения счёта проактивной детекцией — операнд гейта входа:
 * группа `U18` документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/EntryScannerJob.md §«Гейт входа»; величина —
 * docs/models/domain/core/ExchangeAccount.md, поле {@code observedPassAt}).
 *
 * <p><b>Базовая сборка:</b> счёт с настоящим моментом последнего
 * наблюдённого прохода; момент вопроса и допуск подаются явно — часы процесса
 * исхода не решают.
 */
class ExchangeAccountObservationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 30, 10, 3, 0, 0, ZoneOffset.UTC);
    private static final Duration MAX_AGE = Duration.ofSeconds(195);

    /** Наблюдения не было вовсе — молчание разрешением не является. */
    @Test
    @DisplayName("U18.1 — наблюдения не было: вход закрыт")
    void u18_1_aNeverObservedAccountIsNotObservedWithinAnyAge() {
        assertThat(observedAt(null).observedWithin(MAX_AGE, NOW)).isFalse();
    }

    @Test
    @DisplayName("U18.2 — наблюдение моложе допуска: вход открыт")
    void u18_2_aFreshObservationPasses() {
        assertThat(observedAt(NOW.minusSeconds(30)).observedWithin(MAX_AGE, NOW)).isTrue();
    }

    /** Граница включена: возраст, равный допуску, ещё допустим. */
    @Test
    @DisplayName("U18.3 — возраст наблюдения равен допуску: вход открыт")
    void u18_3_theToleranceItselfIsAdmitted() {
        assertThat(observedAt(NOW.minus(MAX_AGE)).observedWithin(MAX_AGE, NOW)).isTrue();
    }

    @Test
    @DisplayName("U18.4 — наблюдение старше допуска на секунду: вход закрыт")
    void u18_4_anObservationBeyondTheToleranceIsRefused() {
        assertThat(observedAt(NOW.minus(MAX_AGE).minusSeconds(1)).observedWithin(MAX_AGE, NOW)).isFalse();
    }

    private static ExchangeAccount observedAt(OffsetDateTime observedPassAt) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(2L);
        account.setSafetyRung(ExchangeAccount.SafetyRung.ACTIVE);
        account.setObservedPassAt(observedPassAt);
        return account;
    }
}
