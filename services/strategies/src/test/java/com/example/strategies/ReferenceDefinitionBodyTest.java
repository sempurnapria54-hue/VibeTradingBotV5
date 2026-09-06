package com.example.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.domain.model.TenantRiskAppetite;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Эталонное определение репозитория — годное ТЕЛО команды создания у
 * владельца определений.
 *
 * <p><b>Зачем проверка нужна.</b> Эталон живёт в репозитории как образец
 * того, что система умеет исполнять, и читается людьми и спекой. Если он
 * при этом не разбирается поверхностью владельца, образцом он быть
 * перестал, а узнали бы об этом при первой попытке его отправить.
 * Прежняя редакция эталона несла {@code internalId} и не несла счёта:
 * первое владелец не принимает вовсе (идентичность присваивает он сам),
 * второго требует корень
 * (docs/models/domain/aggregate/Strategy.md §«У каждого поля контекста
 * назван писатель и момент»).
 *
 * <p><b>Копия донора остаётся своей и расходится законно</b>: у неё
 * другая форма тела (идентичность из тела, счёта нет вовсе), и она
 * исчезает вместе с донором. Здесь — форма ВЛАДЕЛЬЦА.
 */
class ReferenceDefinitionBodyTest {

    private static final String REFERENCE_DEFINITION = "strategy-examples/trend-following-ema.json";

    private final StrategyDefinitionValidator validator = new StrategyDefinitionValidator();

    @Test
    @DisplayName("Эталон разбирается телом команды создания владельца целиком")
    void theReferenceParsesAsTheOwnersCreateBody() throws IOException {
        CreateStrategyApiRequest request = readReference();

        assertThat(request.getExchangeAccountInternalId())
                .as("счёт — содержательный выбор автора, и корень его требует")
                .isNotBlank();
        assertThat(request.getInstrumentInternalId()).isNotBlank();
        assertThat(request.getDetails()).isNotEmpty();
    }

    /**
     * Идентичности в теле нет: её присваивает сервис-владелец до первой
     * записи. Принятая от вызывающего, она позволила бы ему выбрать имя
     * чужой сущности (docs/architecture/data-ownership.md §Идентификаторы).
     */
    @Test
    @DisplayName("Идентичности определения эталон не несёт — её присваивает владелец")
    void theReferenceCarriesNoIdentity() throws IOException {
        JsonNode tree = new ObjectMapper().readTree(reference());

        assertThat(tree.has("internalId"))
                .as("идентичность из тела владелец не принимает")
                .isFalse();
        assertThat(tree.has("tenantId"))
                .as("тенант приходит контекстом вызова, а не телом")
                .isFalse();
    }

    /**
     * Эталон проходит охрану создания под числами, которые он же и
     * объявляет пределом.
     *
     * <p>Числа — операнд, а не константа владельца: они живут на строке
     * тенанта у ядра (docs/rules/strategy-validation.md §«Исключения:
     * неравенства, проверяемые на создании»). Взяты самые тесные, которые
     * эталон ещё проходит: планка свободнее ничего не проверяла бы.
     */
    @Test
    @DisplayName("Эталон проходит охрану создания под объявленными им числами")
    void theReferencePassesCreateValidation() throws IOException {
        CreateStrategyApiRequest request = readReference();

        assertThatCode(() -> validator.validateCreate(request, referenceAppetite()))
                .doesNotThrowAnyException();
    }

    private TenantRiskAppetite referenceAppetite() {
        return new TenantRiskAppetite(BigDecimal.ONE, new BigDecimal("100"));
    }

    private CreateStrategyApiRequest readReference() throws IOException {
        return new ObjectMapper().readValue(reference(), CreateStrategyApiRequest.class);
    }

    private java.io.InputStream reference() throws IOException {
        return new ClassPathResource(REFERENCE_DEFINITION).getInputStream();
    }
}
