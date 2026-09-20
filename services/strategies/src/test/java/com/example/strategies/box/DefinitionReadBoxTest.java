package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B3} документа кейсов: определение целиком
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>У ненайденности нет ни реджект-кода, ни дома, и ожидание держится
 * ФОРМОЙ тела и неразличимостью двух причин</b> (находка F-6 того же
 * документа). Поэтому клетки {@code B3.2} и {@code B3.3} сравнивают два
 * ответа друг с другом, а не с пиньнутым кодом: контрактно значимо
 * именно то, что клиент не может отличить «нет такого» от «есть, но
 * чужое».
 */
class DefinitionReadBoxTest extends SharedStrategiesBox {

    @Test
    @DisplayName("B3.1 — Определение отдаётся вместе с деревом")
    void b3_1_theDefinitionIsGivenWithItsTree() {
        peerResolvesEverything();
        String internalId = given(TENANT);

        Answer answer = get(STRATEGIES + "/" + internalId, TENANT);

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> body = answer.asObject();
        assertThat(body).containsEntry("internalId", internalId);
        assertThat(body.get("marketPhaseSetting")).as("клаузы фазы едут целиком").isNotNull();
        assertThat(answer.nestedList("indicatorSettings")).isNotEmpty();
        assertThat(answer.nestedList("marketStructureSettings")).isNotEmpty();
        assertThat(answer.nestedList("details"))
                .as("детали по типам фазы — все объявленные")
                .hasSize(4);
        assertThat(answer.body())
                .as("порядок шагов и действий сохранён от присланного при создании")
                .contains("\"bull_entry\"")
                .contains("\"bull_protection_oco\"")
                .contains("\"bull_sl_to_breakeven\"");
        assertThat(answer.body())
                .as("наружу идёт internalId, а ключа базы нет ни у одного узла")
                .doesNotContain("\"id\":");
    }

    @Test
    @DisplayName("B3.2 — Чужое определение читается как ненайденное")
    void b3_2_aForeignDefinitionReadsAsNotFound() {
        peerResolvesEverything();
        String internalId = given(TENANT);

        Answer foreign = get(STRATEGIES + "/" + internalId, SECOND_TENANT);
        Answer absent = get(STRATEGIES + "/st-absent-0001", SECOND_TENANT);

        assertThat(foreign.carriesErrorDto()).isTrue();
        assertThat(foreign.status())
                .as("поверхность не отвечает на вопрос о существовании чужой сущности")
                .isEqualTo(absent.status());
        assertThat(rows.countWhere(STRATEGIES_TABLE, "internal_id", internalId))
                .as("чтение чужого определения его не трогает")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("B3.3 — Неизвестная идентичность")
    void b3_3_anUnknownIdentity() {
        peerResolvesEverything();

        Answer answer = get(STRATEGIES + "/st-absent-0002", TENANT);

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode())
                .as("класс отказа тот же, что у всякого броска поверхности")
                .isEqualTo("STRATEGY_REQUEST_REJECTED");
        assertThat(answer.errorMessage())
                .as("реджект-кода у этого исхода нет вовсе (F-6)")
                .doesNotContain("STRATEGY_")
                .contains("st-absent-0002");
    }
}
