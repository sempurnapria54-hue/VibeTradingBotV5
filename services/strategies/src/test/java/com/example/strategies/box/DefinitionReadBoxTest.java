package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B3} документа кейсов: определение целиком
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Ненайденность опознаётся парой «класс отказа плюс реджект-код
 * {@code STRATEGY_NOT_FOUND}», а неразличимость двух причин — сравнением
 * двух ответов друг с другом</b> (docs/architecture/contracts.md
 * §«Контекст тенанта в вызове»): контрактно значимо, что клиент не может
 * отличить «нет такого» от «есть, но чужое», и число ответа
 * различителем не служит.
 */
class DefinitionReadBoxTest extends SharedStrategiesBox {

    /** Класс отказа, которым отвечают все броски поверхности владельца. */
    private static final String REJECTED = "STRATEGY_REQUEST_REJECTED";

    /** Реджект-код ненайденности определения, названного путём. */
    private static final String NOT_FOUND = "STRATEGY_NOT_FOUND";

    /**
     * Объявление {@code STOCHASTIC}, которого в эталоне нет: три периода
     * РАЗНЫМИ числами — перепутанное имя на проводе не сошлось бы со своим.
     */
    private static final String STOCHASTIC = """
            {"key": "stochastic_probe", "indicatorType": "STOCHASTIC",
             "params": {"timeframe": "FIFTEEN_MINUTES", "kPeriod": 14, "dPeriod": 3, "smoothPeriod": 5},
             "destiny": "ENTRY_CONDITION", "expirationDuration": "PT30M"}
            """;

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
        Answer absent = get(STRATEGIES + "/" + internalId + "-absent", SECOND_TENANT);

        assertThat(foreign.carriesErrorDto()).isTrue();
        assertThat(foreign.errorCode()).isEqualTo(REJECTED);
        assertThat(foreign.errorMessage()).contains(NOT_FOUND);
        assertThat(foreign.status())
                .as("поверхность не отвечает на вопрос о существовании чужой сущности")
                .isEqualTo(absent.status());
        assertThat(foreign.errorCode()).isEqualTo(absent.errorCode());
        assertThat(foreign.errorMessage())
                .as("текст отказа двух причин не различает — отличается только названная идентичность")
                .isEqualTo(absent.errorMessage().replace(internalId + "-absent", internalId));
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
                .isEqualTo(REJECTED);
        assertThat(answer.errorMessage())
                .as("исход опознаётся реджект-кодом ненайденности в пояснении")
                .contains(NOT_FOUND)
                .contains("st-absent-0002");
    }

    /**
     * Проба провода находки F-11: у полей {@code kPeriod} и {@code dPeriod}
     * Lombok печатает {@code getkPeriod}/{@code setkPeriod}, которых Jackson 3
     * не опознаёт, и без явного имени объявление не принималось бы вовсе.
     * Чтение идёт через навес базы, то есть мерит обе стороны — приём на
     * проводе и отдачу сохранённого.
     */
    @Test
    @DisplayName("B3.4 — Объявление STOCHASTIC принимается и отдаётся всеми тремя периодами")
    void b3_4_aStochasticDeclarationIsAcceptedAndGivenBackWithAllItsPeriods() {
        peerResolvesEverything();

        Answer created = post(STRATEGIES, TENANT,
                Bodies.referenceWithElement(tree -> tree.at("/indicatorSettings"), STOCHASTIC));

        assertThat(created.status()).as("объявление принято поверхностью; ответ %s", created.body()).isEqualTo(201);
        String internalId = String.valueOf(created.asObject().get("internalId"));
        Answer answer = get(STRATEGIES + "/" + internalId, TENANT);
        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> params = stochasticParamsOf(answer);
        assertThat(params.get("kPeriod")).as("период %%K своим именем провода; params %s", params).hasToString("14");
        assertThat(params.get("dPeriod")).as("период %%D своим именем провода; params %s", params).hasToString("3");
        assertThat(params.get("smoothPeriod")).as("период сглаживания; params %s", params).hasToString("5");
    }

    /** Параметры объявления {@code STOCHASTIC} в ответе чтения; его отсутствие — провал клетки. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> stochasticParamsOf(Answer answer) {
        return answer.nestedList("indicatorSettings").stream()
                .filter(setting -> Objects.equals("STOCHASTIC", setting.get("indicatorType")))
                .map(setting -> (Map<String, Object>) setting.get("params"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("В ответе чтения нет объявления STOCHASTIC: " + answer.body()));
    }
}
