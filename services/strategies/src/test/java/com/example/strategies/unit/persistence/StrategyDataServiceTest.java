package com.example.strategies.unit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.strategies.exception.StrategyNotFoundException;
import com.example.strategies.exception.handler.GlobalExceptionHandler;
import com.example.strategies.mapping.StrategyMapper;
import com.example.strategies.persistence.repository.StrategyRepository;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.tradingbot.api.model.ErrorApiResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ненайденная строка определения на тропе перехода отвечает классом своего
 * артефакта, а не платформенным (docs/rules/error-handling-policy.md
 * §«Пояснение отказа пишет наша сторона, а не платформа»), — клетка
 * {@code B4.9} документа `.claude/tests/cases/strategies.md`.
 *
 * <p>Прежний {@code IllegalArgumentException} уходил бы наружу постоянным
 * текстом негодного входа, без реджект-кода ненайденности; свой класс несёт
 * тот же отказ, что у поиска определения в контексте тенанта.
 *
 * <p><b>Исход опознаётся ТЕЛОМ, а не числом ответа</b>
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»): класс
 * отказа поверхности плюс реджект-код ненайденности в пояснении. Тело
 * собирает тот же обработчик поверхности, что отвечает вызывающему; число
 * проба не пинит — набор HTTP-кодов провизорен, и ход его выравнивания
 * сделал бы пробу красной на исправной системе.
 */
class StrategyDataServiceTest {

    private static final String INTERNAL_ID = "st-absent";

    private final StrategyRepository repository = mock(StrategyRepository.class);

    private final StrategyDataService dataService = new StrategyDataService(repository, mock(StrategyMapper.class));

    private final GlobalExceptionHandler surfaceHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("B4.9 — Строки определения нет на записи перехода: отказ ненайденности телом, а не платформенный")
    void b4_9_anAbsentDefinitionRowIsANotFoundRejection() {
        when(repository.findByInternalId(INTERNAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dataService.getRequiredEntityByInternalId(INTERNAL_ID))
                .as("B4.9: вход %s — отказ классом своего артефакта, а не платформенным", INTERNAL_ID)
                .isInstanceOfSatisfying(StrategyNotFoundException.class, this::carriesTheNotFoundBody);
    }

    /** Тело, которое вызывающий получит от обработчика поверхности. */
    private void carriesTheNotFoundBody(StrategyNotFoundException rejected) {
        ErrorApiResponse body = surfaceHandler.onResponseStatus(rejected).getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode())
                .as("B4.9: класс отказа поверхности — тот же, что у поиска определения в контексте тенанта")
                .isEqualTo("STRATEGY_REQUEST_REJECTED");
        assertThat(body.getMessage())
                .as("B4.9: пояснение начинается реджект-кодом ненайденности и называет запрошенную идентичность")
                .startsWith("STRATEGY_NOT_FOUND")
                .contains(INTERNAL_ID);
    }
}
