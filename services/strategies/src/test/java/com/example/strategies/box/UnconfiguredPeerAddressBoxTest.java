package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Пустой адрес соседа — клетка {@code B9.2}
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Свой контекст, и это ВХОД клетки:</b> адрес соседа приезжает
 * конфигурацией и фиксируется подъёмом. Ставить его общему ящику значило
 * бы отнять охрану создания у всех его соседок.
 *
 * <p><b>Предмет — не код ответа, а то, ЧТО НЕ СЛУЧИЛОСЬ.</b> Незаданный
 * адрес означает, что операнд проверки не добыть; исходов у клетки два и
 * оба законны — отказ создания либо отказ подъёма контекста, — а
 * незаконен ровно третий: {@code 201} с непроверенным определением
 * (javadoc {@code NeighbourProperties}).
 */
class UnconfiguredPeerAddressBoxTest extends StrategiesBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StrategiesSubstrate.register(registry,
                Map.of(StrategiesSubstrate.PEER_ADDRESS_KEY, ""));
    }

    /**
     * <b>Класс отказа — наш дефект, а не недоступность и не дефект входа.</b>
     * Адрес без схемы клиент отвергает ещё до отправки; повтор тем же
     * адресом даст тот же отказ, и чинить здесь не вызывающему, а
     * конфигурацию сервиса (docs/rules/runtime-error-classification.md
     * §«Ненастроенная тропа к соседу — наш дефект»). Прежде отказ минувал
     * разбор соседа и отвечал {@code 400 INVALID_REQUEST} — находка F-9
     * закрыта.
     */
    @Test
    @DisplayName("B9.2 — Пустой адрес соседа отвергает создание, а не пропускает его")
    void b9_2_anEmptyNeighbourAddressRefusesCreationRatherThanLettingItThrough() {
        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.status())
                .as("непроверенным определение не проходит")
                .isNotEqualTo(201);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode())
                .as("тропа к соседу не настроена — это наш дефект, а не дефект тела")
                .isEqualTo("PEER_REFUSED");
        assertThat(rows.count(STRATEGIES_TABLE)).as("строки определения нет").isZero();
        assertThat(rows.count(OUTBOX_TABLE)).isZero();
    }
}
