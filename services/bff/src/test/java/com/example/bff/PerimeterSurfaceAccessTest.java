package com.example.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Контур доступа периметра
 * (docs/rules/api-access-policy.md §«Перечень точек: что закрыто и что
 * осталось»): умолчание закрыто, открытая точка ровно одна — проба
 * живости, а тропа подписки закрыта БИЛЕТОМ, а не токеном.
 *
 * <p><b>Вторая форма предъявления проверяется отдельно, и это несущее.</b>
 * Точка подписки выведена из bearer-цепочки намеренно — браузерный
 * {@code EventSource} заголовка {@code Authorization} не ставит, — и
 * потому клейм «она не открыта» держится не конфигурацией цепочки, а
 * проверкой билета. Без прогона это утверждение неотличимо от дырки.
 *
 * <p><b>Проверяется дефолт, а не набор путей:</b> перечень эндпоинтов
 * тест не повторяет — второй носитель перечня разошёлся бы с контуром
 * первой же правкой.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PerimeterSurfaceContext.class)
class PerimeterSurfaceAccessTest {

    private static final String LIVENESS_PROBE = "/actuator/health";
    private static final String ARBITRARY_CLOSED_PATH = "/api/v1/trading-core/anything-at-all";
    private static final String CONTEXT_PATH = "/api/v1/bff/context";
    private static final String STREAM_PATH = "/api/v1/bff/stream";
    private static final String TICKETS_PATH = "/api/v1/bff/stream-tickets";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("Умолчание закрыто: вызов без предъявленного токена — отказ")
    void aCallWithoutABearerIsDenied() throws Exception {
        mockMvc.perform(get(ARBITRARY_CLOSED_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CONTEXT_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(TICKETS_PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Отказ доступа отвечает единым error-DTO, а не пустым телом")
    void denialAnswersWithTheSameErrorDto() throws Exception {
        mockMvc.perform(get(ARBITRARY_CLOSED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.occurredAt").exists())
                // Ничего сверх класса: ни пути, ни текста исключения.
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    /**
     * Тропа подписки закрыта билетом. Без него она отвечает ТЕМ ЖЕ
     * форматом и тем же кодом, что отказ фильтр-цепочки: форм
     * предъявления две, а формат отказа один.
     */
    @Test
    @DisplayName("Подписка без билета — отказ тем же error-DTO")
    void theStreamWithoutATicketIsDenied() throws Exception {
        mockMvc.perform(get(STREAM_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAUTHENTICATED"));
    }

    /** Испорченный билет неотличим наружу от непредъявленного. */
    @Test
    @DisplayName("Подписка с негодным билетом — тот же отказ")
    void theStreamWithABadTicketIsDenied() throws Exception {
        mockMvc.perform(get(STREAM_PATH).param("ticket", "not-a-ticket"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAUTHENTICATED"));
    }

    /**
     * Проба живости — единственное исключение. Тест спрашивает
     * <b>контур</b>, а не сам эндпоинт: актуатора в минимальном
     * контексте нет, поэтому ответ будет {@code 404}. Несущее здесь —
     * что он <b>не</b> отказ доступа.
     */
    @Test
    @DisplayName("Проба живости открыта — единственное исключение")
    void theLivenessProbeIsOpen() throws Exception {
        int statusCode = mockMvc.perform(get(LIVENESS_PROBE)).andReturn().getResponse().getStatus();

        assertThat(statusCode)
                .as("проба живости обязана проходить контур доступа, а не отвергаться им")
                .isNotIn(401, 403);
    }

    /**
     * Предъявленный токен проходит: контур закрыт по умолчанию, а не
     * наглухо. Без этой проверки «умолчание закрыто» доказывалось бы и
     * контуром, отвергающим вообще всех.
     */
    @Test
    @DisplayName("Предъявленный токен доходит до собственной точки периметра")
    void anAcceptedBearerReachesThePerimetersOwnPoint() throws Exception {
        mockMvc.perform(get(CONTEXT_PATH).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(PerimeterSurfaceContext.TENANT))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    /** Сессия не заводится: контур stateless, куки как второй тропы нет. */
    @Test
    @DisplayName("Ответ не заводит сессии")
    void noSessionIsCreated() throws Exception {
        assertThat(mockMvc.perform(get(ARBITRARY_CLOSED_PATH).with(jwt()))
                .andReturn().getRequest().getSession(false))
                .as("stateless-контур сессии не создаёт")
                .isNull();
    }
}
