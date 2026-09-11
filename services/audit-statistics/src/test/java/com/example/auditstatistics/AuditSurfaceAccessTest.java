package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auditstatistics.api.AccessDenialHandler;
import com.example.auditstatistics.config.SecurityConfig;
import com.example.auditstatistics.domain.model.AccessDenial;
import com.example.auditstatistics.domain.service.AccessDenialService;
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
 * Контур доступа к поверхности владельца журнала
 * (docs/rules/api-access-policy.md §«Вся поверхность закрыта; открытое —
 * перечислено»): умолчание закрыто, открытых точек две — проба живости и
 * съём метрик.
 *
 * <p><b>Ответ на вопрос правила живёт здесь, а не строкой в правиле.</b>
 * Общий перечень путей по всем сервисам стареет первым и держится
 * дисциплиной, которую сам же и заменяет; свойство контура проверяется
 * прогоном у того сервиса, чей это контур.
 *
 * <p><b>Проверяется дефолт, а не набор путей.</b> Перечень эндпоинтов
 * тест не повторяет: второй носитель перечня разошёлся бы с контуром
 * первой же правкой. Спрашивается путь, которого в контуре нет вовсе, —
 * он обязан отвечать отказом, иначе незакрытым оказывается всё, что
 * забыли, и ошибка идёт в разрешающую сторону (docs/concept.md П1,
 * следствие 3).
 *
 * <p><b>Читающая поверхность здесь ничего не смягчает</b>, и это
 * несущее: тенант едет операндом вызова, а не токеном
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»), поэтому
 * открытое чтение отдало бы историю любого тенанта кому угодно.
 *
 * <p>Контекст поднимается <b>без БД и брокера</b>: ни от чего из этого
 * конфигурация контура не зависит, а проверять их здесь значило бы мерить
 * чужой предмет.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AuditSurfaceAccessTest.SurfaceConfig.class)
class AuditSurfaceAccessTest {

    private static final String LIVENESS_PROBE = "/actuator/health";
    private static final String METRICS_SCRAPE = "/actuator/prometheus";
    private static final String ARBITRARY_CLOSED_PATH = "/api/v1/audit-statistics/anything-at-all";
    private static final String SURFACE_DESCRIPTION = "/v3/api-docs";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AccessDenialService denialWriter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reset(denialWriter);
    }

    @Test
    @DisplayName("Умолчание закрыто: вызов без предъявленного токена — отказ")
    void aCallWithoutABearerIsDenied() throws Exception {
        mockMvc.perform(get(ARBITRARY_CLOSED_PATH)).andExpect(status().isUnauthorized());
    }

    /**
     * Описание поверхности исключением не объявлено, а значит закрыто
     * умолчанием: оно рассказывает о составе точек тому, кто себя не
     * предъявил (docs/rules/api-access-policy.md, строка `/v3/api-docs`).
     */
    @Test
    @DisplayName("Описание поверхности закрыто наравне с прочим")
    void theSurfaceDescriptionIsClosed() throws Exception {
        mockMvc.perform(get(SURFACE_DESCRIPTION)).andExpect(status().isUnauthorized());
    }

    /**
     * Отказ доступа отвечает ТЕМ ЖЕ error-DTO, что и всякая ошибка
     * поверхности (docs/rules/error-handling-policy.md §«Отказ доступа —
     * тот же контракт, что и прочие ошибки»).
     *
     * <p>Умолчание ресурс-сервера отвечает пустым телом — вторым
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
     * Отвергнутый вызов оставляет <b>персистентный</b> след, а не только
     * строку лога: лог не запрашивается, не агрегируется и не переживает
     * ротацию (docs/concept.md П3). Писать её положено этому сервису —
     * отказ произошёл у него, и база у него есть
     * (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого
     * есть база»).
     */
    @Test
    @DisplayName("Отвергнутый вызов оставляет журнальную строку, а не только лог")
    void aDeniedCallLeavesAJournalRow() throws Exception {
        mockMvc.perform(get(ARBITRARY_CLOSED_PATH)).andExpect(status().isUnauthorized());

        verify(denialWriter).record(eq("GET " + ARBITRARY_CLOSED_PATH),
                eq(AccessDenial.Outcome.PRINCIPAL_ABSENT), isNull());
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
        verify(denialWriter, never()).record(any(), any(), any());
    }

    /**
     * Съём метрик — второе исключение.
     *
     * <p>Спрашивается <b>контур</b>, как и у пробы живости: актуатора в
     * этом минимальном контексте нет. Несущее — что вызов БЕЗ токена не
     * отвергается доступом: наблюдатель окружения токена не носит, и
     * закрытая тропа оставила бы оба правила алерта без входа — молча
     * (docs/rules/api-access-policy.md §«Съём метрик — второе
     * исключение»).
     */
    @Test
    @DisplayName("Съём метрик открыт — второе исключение")
    void theMetricsScrapeIsOpen() throws Exception {
        int statusCode = mockMvc.perform(get(METRICS_SCRAPE)).andReturn().getResponse().getStatus();

        assertThat(statusCode)
                .as("наблюдатель окружения токена не носит: отвергнутый съём оставил бы алерты без входа")
                .isNotIn(401, 403);
        verify(denialWriter, never()).record(any(), any(), any());
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
        verify(denialWriter, never()).record(any(), any(), any());
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
     * Минимальный контекст: конфигурация контура и подменённый разборщик
     * токена. Контроллеров, репозиториев и джоб здесь нет — предмет теста
     * контур, и авто-конфигурация Boot тянула бы за собой БД, Flyway и
     * Kafka, которых предмету не требуется. Поэтому контекст собирается
     * <b>spring-test</b>, а не {@code @SpringBootTest}.
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
        AccessDenialHandler accessDenialHandler(AccessDenialService denialService, ObjectMapper objectMapper) {
            return new AccessDenialHandler(denialService, objectMapper);
        }

        /**
         * Писатель подменён: его предмет — запись в базу журнала, у
         * которой своя проверка ({@code AccessDenialRowTest}). Здесь
         * меряется контур — какие вызовы до писателя доходят, а какие
         * нет.
         */
        @Bean
        AccessDenialService accessDenialService() {
            return mock(AccessDenialService.class);
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
