package com.example.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.strategies.api.AccessDenialHandler;
import com.example.strategies.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Контур доступа к поверхности владельца определений
 * (docs/rules/api-access-policy.md §«Перечень точек: что закрыто и что
 * осталось»): умолчание закрыто, открытая точка ровно одна — проба
 * живости.
 *
 * <p><b>Ответ на оба вопроса правила живёт здесь, а не строкой в
 * правиле.</b> Общий перечень путей по всем сервисам стареет первым и
 * держится дисциплиной, которую сам же и заменяет; свойство контура
 * проверяется прогоном у того сервиса, чей это контур.
 *
 * <p><b>Проверяется дефолт, а не набор путей.</b> Перечень эндпоинтов
 * тест не повторяет: второй носитель перечня разошёлся бы с контуром
 * первой же правкой. Спрашивается путь, которого в контуре нет вовсе, —
 * он обязан отвечать отказом, иначе незакрытым оказывается всё, что
 * забыли, и ошибка идёт в разрешающую сторону (docs/concept.md П1,
 * следствие 3).
 *
 * <p>Контекст поднимается <b>без БД, Kafka и соседа</b>: ни от чего из
 * этого конфигурация контура не зависит, а проверять их здесь значило бы
 * мерить чужой предмет.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = StrategySurfaceAccessTest.SurfaceConfig.class)
class StrategySurfaceAccessTest {

    private static final String LIVENESS_PROBE = "/actuator/health";
    private static final String ARBITRARY_CLOSED_PATH = "/api/v1/strategies/anything-at-all";

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
        mockMvc.perform(post("/api/v1/strategies")).andExpect(status().isUnauthorized());
    }

    /**
     * Отказ доступа отвечает ТЕМ ЖЕ error-DTO, что и всякая ошибка
     * поверхности (docs/rules/error-handling-policy.md §«Отказ доступа —
     * тот же контракт, что и прочие ошибки»).
     *
     * <p>Умолчание ресурс-сервера отвечало пустым телом — вторым
     * форматом, существование которого клейм «единый DTO» отрицает.
     */
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
     * Проба живости — единственное исключение.
     *
     * <p>Тест спрашивает <b>контур</b>, а не сам эндпоинт: актуатора в
     * этом минимальном контексте нет, поэтому ответ будет {@code 404}.
     * Несущее здесь — что он <b>не</b> отказ доступа: значит вызов дошёл
     * до маршрутизации и правило {@code permitAll} применилось.
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
     * наглухо.
     *
     * <p>Без этой проверки «умолчание закрыто» доказывалось бы и
     * контуром, отвергающим вообще всех, — то есть клеймом, который
     * держится на неработающем сервисе.
     */
    @Test
    @DisplayName("Предъявленный токен доходит до маршрутизации")
    void anAcceptedBearerReachesRouting() throws Exception {
        int statusCode = mockMvc.perform(get(ARBITRARY_CLOSED_PATH).with(jwt()))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode)
                .as("вызов под принятым токеном обязан дойти до маршрутизации")
                .isNotIn(401, 403);
    }

    /**
     * Сессия не заводится: контур stateless, и второй тропы «кто
     * вызывает» — куки — у поверхности нет.
     */
    @Test
    @DisplayName("Ответ не заводит сессии")
    void noSessionIsCreated() throws Exception {
        assertThat(mockMvc.perform(get(ARBITRARY_CLOSED_PATH).with(jwt()))
                .andReturn().getRequest().getSession(false))
                .as("stateless-контур сессии не создаёт")
                .isNull();
    }

    /**
     * Минимальный контекст: конфигурация контура и подменённый
     * разборщик токена. Контроллеров, репозиториев и джоб здесь нет —
     * предмет теста контур, и авто-конфигурация Boot тянула бы за собой
     * БД, Flyway и Kafka, которых предмету не требуется. Поэтому
     * контекст собирается <b>spring-test</b>, а не
     * {@code @SpringBootTest}.
     */
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class SurfaceConfig {

        /**
         * Подпись токена разбирает провайдер идентичности, которого в
         * контуре теста нет. Бин обязателен самой конфигурацией
         * ресурс-сервера; предъявленный токен подставляет оснастка
         * теста, поэтому разборщик не зовётся ни разу.
         */
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        AccessDenialHandler accessDenialHandler(ObjectMapper objectMapper) {
            return new AccessDenialHandler(objectMapper);
        }

        /**
         * Модули регистрируются поиском по classpath — как это делает
         * Boot в проде. Голый {@code new ObjectMapper()} не умеет
         * сериализовать {@code OffsetDateTime}, и тест мерил бы дефект
         * собственной оснастки, а не поведение контура.
         */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
