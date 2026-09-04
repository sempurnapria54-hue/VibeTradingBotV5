package com.example.tradingbot.integration.service.okx.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Опознание отказа источника в кредах по коду ответа
 * (docs/integrations/okx/rules/auth-rejection-codes.md; перечень добыт
 * прогоном контура, кейс `SEC1.1`).
 *
 * <p><b>Проверяются три класса, а не два.</b> Отказ кредов, наш собственный
 * дефект сборки запроса и «прочий отказ источника» — разные тропы, и второй
 * не является отрицанием первого. Двоичная проверка («креды или нет») эту
 * разницу потеряла бы и увела бы расхождение часов в биржевую ступень 2.
 */
class OkxCredentialsRejectionResolverTest {

    private final OkxCredentialsRejectionResolver resolver = new OkxCredentialsRejectionResolver();

    @Test
    @DisplayName("Наблюдённые коды отказа кредов опознаются все пять")
    void observedCredentialsRejectionCodesAreRecognised() {
        // 50101 — ключ не того контура; 50105 — passphrase не та;
        // 50111 — ключ неверной формы; 50113 — подпись не принята;
        // 50119 — ключа у источника нет (отозван либо чужой).
        for (String code : new String[]{"50101", "50105", "50111", "50113", "50119"}) {
            assertThat(resolver.isCredentialsRejected(code))
                    .as("код %s наблюдён прогоном как отказ кредов", code)
                    .isTrue();
            assertThat(resolver.isOwnRequestDefect(code)).isFalse();
        }
    }

    /**
     * Несущее различение: обе формы дают тот же {@code HTTP 401} и то же
     * семейство {@code 501xx}, но лечатся по-разному. Отнести их к отказу
     * кредов значило бы поднимать биржевую ступень 2 по <b>собственной</b>
     * ошибке сборки запроса — остановка торговли без основания.
     */
    @Test
    @DisplayName("Наш дефект сборки запроса отказом кредов не считается")
    void ownRequestDefectIsNotCredentialsRejection() {
        for (String code : new String[]{"50102", "50103"}) {
            assertThat(resolver.isCredentialsRejected(code))
                    .as("код %s — наш дефект, а не потеря права", code)
                    .isFalse();
            assertThat(resolver.isOwnRequestDefect(code)).isTrue();
        }
    }

    @Test
    @DisplayName("Код вне обоих перечней не попадает ни в один класс")
    void unknownCodeFallsIntoNeitherClass() {
        // Перечень закрыт над НАБЛЮДЁННЫМИ формами, а не над всем семейством:
        // код вне перечня идёт общей тропой отказа границы — консервативно.
        assertThat(resolver.isCredentialsRejected("50011")).isFalse();
        assertThat(resolver.isOwnRequestDefect("50011")).isFalse();
        assertThat(resolver.isCredentialsRejected("51000")).isFalse();
        assertThat(resolver.isCredentialsRejected("0")).isFalse();
    }

    @Test
    @DisplayName("Пустой и отсутствующий код классом не становятся")
    void blankCodeIsNotAClass() {
        assertThat(resolver.isCredentialsRejected(null)).isFalse();
        assertThat(resolver.isCredentialsRejected("")).isFalse();
        assertThat(resolver.isOwnRequestDefect(null)).isFalse();
    }
}
