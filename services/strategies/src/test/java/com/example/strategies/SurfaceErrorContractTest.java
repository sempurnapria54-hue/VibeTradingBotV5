package com.example.strategies;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.strategies.api.controller.StrategyController;
import com.example.strategies.config.SurfaceProperties;
import com.example.strategies.domain.service.StrategyCreationService;
import com.example.strategies.domain.service.StrategyLifecycleService;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.example.strategies.exception.handler.GlobalExceptionHandler;
import com.example.strategies.mapping.StrategyApiMapper;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.strategies.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Внешний контракт отказа: <b>каждый</b> отвергнутый вызов отвечает одним
 * error-DTO (docs/rules/error-handling-policy.md §«Внешняя поверхность»).
 *
 * <p><b>Проверяются те тропы, которые обработчики не называют
 * поимённо.</b> Отказы, у которых есть свой {@code @ExceptionHandler},
 * проверять нечем: их формат виден в самом обработчике. Ломается клейм
 * там, где отказ производит <b>контейнер</b> — до контроллера и мимо
 * наших обработчиков; все три тропы ниже наблюдались отвечающими пустым
 * телом.
 *
 * <p>Контекст — веб-слой без БД, соседа и контура доступа: предмет здесь
 * форма ответа, а не работа сервиса. Отказ контура проверяется своим
 * тестом ({@link StrategySurfaceAccessTest}) — он возникает в
 * фильтр-цепочке, которой тут нет.
 */
class SurfaceErrorContractTest {

    private static final String TENANT = "tn-0001";

    /** Полное имя доменного класса — то, что платформа кладёт в текст разбора перечня. */
    private static final String PLATFORM_CLASS = "com.example.tradingbot.domain.model.aggregate.strategy.Strategy";

    private static final String PLATFORM_TEXT = "No enum constant " + PLATFORM_CLASS + ".Status.BOGUS";

    private final StrategyDataService strategyDataService = mock(StrategyDataService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StrategyController(
                        mock(StrategyCreationService.class),
                        mock(StrategyLifecycleService.class),
                        mock(StrategyDefinitionValidator.class),
                        strategyDataService,
                        mock(StrategyApiMapper.class),
                        new SurfaceProperties()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Неразбираемое тело отвергается единым error-DTO")
    void anUnreadableBodyIsRejectedWithTheErrorDto() throws Exception {
        mockMvc.perform(post("/api/v1/strategies")
                        .header(Constants.Header.TENANT, TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.occurredAt").exists());
    }

    /**
     * Заголовок контекста тенанта не предъявлен. Вызов не адресован
     * никакому тенанту, и достраивать контекст нечем — отказ, а не
     * умолчание.
     */
    @Test
    @DisplayName("Отсутствующий заголовок контекста отвергается единым error-DTO")
    void aMissingTenantHeaderIsRejectedWithTheErrorDto() throws Exception {
        mockMvc.perform(post("/api/v1/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.occurredAt").exists());
    }

    /** Тело, не прошедшее охрану презенса, — тот же формат и тот же код. */
    @Test
    @DisplayName("Тело без обязательных полей отвергается единым error-DTO")
    void aBodyMissingRequiredFieldsIsRejectedWithTheErrorDto() throws Exception {
        mockMvc.perform(post("/api/v1/strategies")
                        .header(Constants.Header.TENANT, TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.occurredAt").exists());
    }

    /**
     * Негодный вход, распознанный прикладным кодом, отвечает постоянным
     * текстом ветви: класс исключения платформенный, и его текст несёт
     * внутреннюю форму — здесь полное имя доменного класса, как у разбора
     * значения вне перечня (docs/rules/error-handling-policy.md §«Пояснение
     * отказа пишет наша сторона, а не платформа»).
     */
    @Test
    @DisplayName("Негодный вход отвечает текстом нашей стороны, а не текстом платформенного исключения")
    void anInvalidInputAnswersWithOurOwnExplanation() throws Exception {
        when(strategyDataService.findByInternalIdWithTree(anyString()))
                .thenThrow(new IllegalArgumentException(PLATFORM_TEXT));

        mockMvc.perform(get("/api/v1/strategies/st-0001")
                        .header(Constants.Header.TENANT, TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(not(containsString(PLATFORM_CLASS))))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /**
     * Отказ по правам последним обработчиком не разрешается: ответ на него
     * пишет контур снаружи диспетчера. Бросает его здесь коллаборатор, а не
     * пер-операционная проверка права, — таких проверок нет ни одной, и
     * предмет от этого не меняется: мерится, что исключение ВЫХОДИТ из
     * диспетчера.
     */
    @Test
    @DisplayName("Отказ по правам уходит наружу диспетчера, а не разрешается перехватчиком")
    void anAccessDenialLeavesTheDispatcherUnresolved() {
        when(strategyDataService.findByInternalIdWithTree(anyString()))
                .thenThrow(new AccessDeniedException("нет права"));

        assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/strategies/st-0001")
                .header(Constants.Header.TENANT, TENANT)))
                .as("разрешённый здесь отказ ушёл бы кодом 500, и ответ контура его не написал бы")
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }
}
