package com.example.strategies.unit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.strategies.exception.StrategyNotFoundException;
import com.example.strategies.mapping.StrategyMapper;
import com.example.strategies.persistence.repository.StrategyRepository;
import com.example.strategies.persistence.service.StrategyDataService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Ненайденная строка определения на тропе перехода отвечает классом своего
 * артефакта, а не платформенным (docs/rules/error-handling-policy.md
 * §«Пояснение отказа пишет наша сторона, а не платформа»).
 *
 * <p>Прежний {@code IllegalArgumentException} уходил бы наружу постоянным
 * текстом негодного входа, без реджект-кода ненайденности; свой класс несёт
 * тот же отказ, что у поиска определения в контексте тенанта.
 */
class StrategyDataServiceTest {

    private static final String INTERNAL_ID = "st-absent";

    private final StrategyRepository repository = mock(StrategyRepository.class);

    private final StrategyDataService dataService = new StrategyDataService(repository, mock(StrategyMapper.class));

    @Test
    @DisplayName("Строки определения нет: отказ ненайденности со своим реджект-кодом")
    void anAbsentDefinitionRowIsANotFoundRejection() {
        when(repository.findByInternalId(INTERNAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dataService.getRequiredEntityByInternalId(INTERNAL_ID))
                .isInstanceOfSatisfying(StrategyNotFoundException.class, rejected -> {
                    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rejected.getReason()).startsWith("STRATEGY_NOT_FOUND").contains(INTERNAL_ID);
                });
    }
}
