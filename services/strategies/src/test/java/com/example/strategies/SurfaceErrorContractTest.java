package com.example.strategies;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.strategies.api.GlobalExceptionHandler;
import com.example.strategies.api.controller.StrategyController;
import com.example.strategies.config.SurfaceProperties;
import com.example.strategies.domain.service.StrategyCreationService;
import com.example.strategies.domain.service.StrategyLifecycleService;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.example.strategies.mapping.StrategyApiMapper;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.strategies.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StrategyController(
                        mock(StrategyCreationService.class),
                        mock(StrategyLifecycleService.class),
                        mock(StrategyDefinitionValidator.class),
                        mock(StrategyDataService.class),
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
}
