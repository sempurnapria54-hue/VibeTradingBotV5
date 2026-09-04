package com.example.tradingbot.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tradingbot.api.ErrorApiResponseFactory;
import com.example.tradingbot.config.ApiAccessProperties;
import com.example.tradingbot.config.ApiAccessSecurityConfig;
import com.example.tradingbot.domain.security.AccessDenial;
import com.example.tradingbot.domain.security.AccessDenialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Контур доступа к поверхности (docs/rules/api-access-policy.md): умолчание
 * закрыто, открыта ровно одна точка — проба живости.
 *
 * <p><b>Проверяется клейм, а не набор путей.</b> Перечень закрытых эндпоинтов
 * тест не повторяет: второй носитель перечня разошёлся бы с контуром первой же
 * правкой, и читатель не знал бы, который из двух действует. Проверяется
 * <b>дефолт</b> — произвольный путь, которого в контуре нет вовсе, обязан
 * отвечать отказом: иначе незакрытым оказывается всё, что забыли, и это ошибка
 * в разрешающую сторону (П1 следствие 3).
 *
 * <p>Контекст поднимается <b>без БД и без Vault</b>: конфигурация контура и
 * точки входа отказа от них не зависят, а писатель строки подменён — предмет
 * теста поверхность, а не persistence.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ApiAccessSurfaceTest.SurfaceProbeConfig.class)
class ApiAccessSurfaceTest {

    private static final String LIVENESS_PROBE = "/actuator/health";
    private static final String ARBITRARY_CLOSED_PATH = "/api/anything-at-all";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AccessDenialService denialService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("Умолчание закрыто: произвольный путь без принципала — отказ")
    void unknownPathWithoutPrincipalIsDenied() throws Exception {
        mockMvc.perform(get(ARBITRARY_CLOSED_PATH))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Проба живости — единственное исключение. Тест спрашивает <b>контур</b>,
     * а не сам эндпоинт: в этом минимальном контексте actuator'а нет, поэтому
     * ответ будет 404. Несущее здесь — что он <b>не</b> отказ доступа: значит
     * вызов до маршрутизации дошёл, и правило {@code permitAll} применилось.
     */
    @Test
    @DisplayName("Проба живости открыта — единственное исключение")
    void livenessProbeIsOpen() throws Exception {
        int statusCode = mockMvc.perform(get(LIVENESS_PROBE)).andReturn().getResponse().getStatus();

        assertThat(statusCode)
                .as("проба живости обязана проходить контур доступа, а не отвергаться им")
                .isNotIn(401, 403);
        verify(denialService, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("Отказ заводит строку и отвечает единым error-DTO")
    void denialRecordsRowAndAnswersWithErrorDto() throws Exception {
        mockMvc.perform(post(ARBITRARY_CLOSED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(ARBITRARY_CLOSED_PATH))
                .andExpect(jsonPath("$.timestamp").exists());

        // Принципал НЕ передаётся: заявленное неудостоверённое имя в строку не пишется.
        verify(denialService).record(eq("POST " + ARBITRARY_CLOSED_PATH),
                eq(AccessDenial.Outcome.PRINCIPAL_ABSENT), isNull());
    }

    @Test
    @DisplayName("Отказ не сообщает наружу ничего сверх класса")
    void denialDisclosesNothingBeyondClass() throws Exception {
        mockMvc.perform(get(ARBITRARY_CLOSED_PATH))
                .andExpect(status().isUnauthorized())
                // message несёт только имя класса отказа: ни существования объекта,
                // ни его состояния, ни текста исключения.
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    @DisplayName("Предъявленный принципал проходит: контур не закрыт наглухо")
    void acceptedPrincipalPasses() throws Exception {
        int statusCode = mockMvc.perform(get(ARBITRARY_CLOSED_PATH).with(httpBasic("holder", "probe-secret")))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode)
                .as("вызов под принятым принципалом обязан дойти до маршрутизации")
                .isNotIn(401, 403);
        verify(denialService, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("Неверный секрет принципала — отказ, а не проход")
    void wrongSecretIsDenied() throws Exception {
        mockMvc.perform(get(ARBITRARY_CLOSED_PATH).with(httpBasic("holder", "not-the-secret")))
                .andExpect(status().isUnauthorized());

        verify(denialService).record(any(), eq(AccessDenial.Outcome.PRINCIPAL_ABSENT), isNull());
    }

    /**
     * Минимальный контекст: конфигурация контура, точки входа отказа и сборщик
     * ответа. Контроллеров, репозиториев и джоб здесь нет — предмет теста
     * поверхность, и авто-конфигурация Boot тянула бы за собой БД, Flyway и
     * Vault, которых предмету не требуется. Поэтому контекст собирается
     * <b>spring-test</b>, а не {@code @SpringBootTest}.
     */
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ApiAccessSecurityConfig.class, AccessDenialHandler.class, ErrorApiResponseFactory.class})
    static class SurfaceProbeConfig {

        @Bean
        public ApiAccessProperties apiAccessProperties() {
            ApiAccessProperties properties = new ApiAccessProperties();
            properties.setPrincipal("holder");
            properties.setSecret("probe-secret");
            return properties;
        }

        /**
         * Модули регистрируются поиском по classpath — как это делает Boot в
         * проде. Голый {@code new ObjectMapper()} не умеет сериализовать
         * {@code OffsetDateTime}, и тест мерил бы дефект собственной оснастки,
         * а не поведение контура.
         */
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
