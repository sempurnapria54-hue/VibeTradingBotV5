package com.example.bff;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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
}
