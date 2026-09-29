package com.example.bff;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bff.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Клейм «единый error-DTO» на тропах, которых обработчики не называют
 * поимённо (docs/rules/error-handling-policy.md §«Отказ, произведённый
 * контейнером, — тот же контракт»).
 *
 * <p><b>Проверяется прогоном, а не чтением:</b> отказ, отвечающий пустым
 * телом, от исправного в коде неотличим — ровно так три тропы соседнего
 * сервиса и прожили до первого прогона поверхности.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PerimeterSurfaceContext.class)
class SurfaceErrorContractTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * Периметр себя не проксирует: адрес под его именем либо заведён
     * контроллером, либо не существует вовсе — и отвечает своим DTO, а
     * не форматом контейнера.
     */
    @Test
    @DisplayName("Незаведённый адрес периметра отвечает единым error-DTO")
    void anUnknownPerimeterPathAnswersWithTheSameErrorDto() throws Exception {
        mockMvc.perform(get("/api/v1/bff/nothing-here").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERIMETER_REQUEST_REJECTED"))
                .andExpect(jsonPath("$.occurredAt").exists());
    }

    /**
     * Адрес вне версии внешней поверхности не обслуживается ничем — и
     * ответ на него тоже собирается нашим DTO, а не контейнером.
     */
    @Test
    @DisplayName("Адрес вне внешней поверхности отвечает единым error-DTO")
    void aPathOutsideTheApiAnswersWithTheSameErrorDto() throws Exception {
        mockMvc.perform(get("/nothing-at-all").with(jwt()))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.occurredAt").exists());
    }

    /**
     * Отказ по правам последним обработчиком не разрешается: ответ на него
     * пишет контур снаружи диспетчера. Пер-операционных проверок права нет
     * ни одной, поэтому отказ бросает контроллер пробы, собранный без
     * контекста, — мерится, что исключение ВЫХОДИТ из диспетчера.
     */
    @Test
    @DisplayName("Отказ по правам уходит наружу диспетчера, а не разрешается перехватчиком")
    void anAccessDenialLeavesTheDispatcherUnresolved() {
        MockMvc standalone = MockMvcBuilders.standaloneSetup(new DenyingProbe())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        assertThatThrownBy(() -> standalone.perform(get("/api/v1/bff/probe/denied")))
                .as("разрешённый здесь отказ ушёл бы кодом 500, и ответ контура его не написал бы")
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    /**
     * Контроллер пробы. Стереотип обязателен — отдельная сборка диспетчера
     * регистрирует обработчики по нему; в контексты ящика проба не
     * приезжает: вложенный класс тест-класса сканирование исключает
     * ({@code TestTypeExcludeFilter}).
     */
    @RestController
    static class DenyingProbe {

        @GetMapping("/api/v1/bff/probe/denied")
        void deny() {
            throw new AccessDeniedException("нет права");
        }
    }
}
