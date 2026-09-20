package com.example.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
 *
 * <p><b>Прохождение эталона охраной создания здесь больше не
 * проверяется:</b> это ожидание поглощено клеткой {@code U1.1} документа
 * `.claude/tests/cases/strategy-definition-validation.md`
 * ({@code RejectionFormTest}), и второй носитель одного ожидания
 * разошёлся бы с первым. Здесь остаётся предмет, которого документ не
 * берёт, — ФОРМА ТЕЛА команды.
 */
class ReferenceDefinitionBodyTest {

    private static final String REFERENCE_DEFINITION = "strategy-examples/trend-following-ema.json";

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

    private CreateStrategyApiRequest readReference() throws IOException {
        return new ObjectMapper().readValue(reference(), CreateStrategyApiRequest.class);
    }

    private java.io.InputStream reference() throws IOException {
        return new ClassPathResource(REFERENCE_DEFINITION).getInputStream();
    }
}
