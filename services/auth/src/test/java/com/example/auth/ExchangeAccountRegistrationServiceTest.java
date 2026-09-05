package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.config.EnvironmentProperties;
import com.example.auth.domain.service.ExchangeAccountRegistrationService;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Допуск контура при регистрации счёта — исполнимое правило
 * docs/spec/environment-contour.json (величина {@code contourAdmitted}),
 * применённое в точке, где оно срабатывает.
 *
 * <p>Состояние здесь настоящее: конфигурация окружения и контур счёта;
 * подменённых предикатов нет (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»). Матрица допуска не переписывается — она берётся из той же
 * спеки, что и корпус, и совпадение проверяет
 * {@code tools/deploy-layout-check.py} (ось 5).
 */
class ExchangeAccountRegistrationServiceTest {

    @Test
    void nonProductionAdmitsDemoAndRefusesLive() {
        ExchangeAccountRegistrationService service = serviceAdmitting(ExchangeAccount.Contour.DEMO);

        assertThat(service.contourAdmitted(ExchangeAccount.Contour.DEMO)).isTrue();
        assertThat(service.contourAdmitted(ExchangeAccount.Contour.LIVE)).isFalse();
    }

    @Test
    void productionAdmitsBoth() {
        ExchangeAccountRegistrationService service =
                serviceAdmitting(ExchangeAccount.Contour.LIVE, ExchangeAccount.Contour.DEMO);

        assertThat(service.contourAdmitted(ExchangeAccount.Contour.LIVE)).isTrue();
        assertThat(service.contourAdmitted(ExchangeAccount.Contour.DEMO)).isTrue();
    }

    /**
     * <b>Пустая конфигурация — отказ, а не «допустимы все».</b> Это и есть
     * состояние окружения, которому забыли задать ось: если бы пустота
     * читалась как разрешение, такое окружение торговало бы боевыми
     * деньгами. Пустое место означает отказ (docs/concept.md).
     */
    @Test
    void unconfiguredEnvironmentAdmitsNothing() {
        ExchangeAccountRegistrationService service = serviceAdmitting();

        assertThat(service.contourAdmitted(ExchangeAccount.Contour.DEMO)).isFalse();
        assertThat(service.contourAdmitted(ExchangeAccount.Contour.LIVE)).isFalse();
    }

    @Test
    void absentContourIsRefused() {
        ExchangeAccountRegistrationService service =
                serviceAdmitting(ExchangeAccount.Contour.LIVE, ExchangeAccount.Contour.DEMO);

        assertThat(service.contourAdmitted(null)).isFalse();
    }

    private ExchangeAccountRegistrationService serviceAdmitting(ExchangeAccount.Contour... contours) {
        EnvironmentProperties properties = new EnvironmentProperties();
        properties.setAdmittedContours(Set.of(contours));
        return new ExchangeAccountRegistrationService(properties);
    }
}
