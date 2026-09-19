package com.example.platform.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.tradingbot.api.model.ErrorApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Точки входа отказа фильтр-цепочки: группы `U4`, `U5`, `U6` и клетка
 * `U14.7` документа `.claude/tests/cases/platform-shared-logic.md`.
 *
 * <p><b>Запрос и ответ собираются в памяти</b>
 * ({@code MockHttpServletRequest} / {@code MockHttpServletResponse}):
 * контейнер не поднимается, а наблюдаются статус, заголовки, тип
 * содержимого, кодировка и тело как строка. Это не подмена предмета —
 * точка входа получает от контейнера ровно эти два объекта и ничего сверх.
 *
 * <p><b>Писатель следа подменён, и это признак предмета.</b> Он — порт,
 * реализуемый сервисом со своей базой; что именно кладётся в строку, живёт
 * у владельца модели и проверяется ящиками {@code audit} и
 * {@code statistics}. Здесь наблюдаются факт, число, порядок и аргументы
 * вызова — и ОТСУТСТВИЕ вызова там, где его быть не должно.
 *
 * <p><b>Момент мерится границей и смещением, а не точным значением:</b>
 * часы процесса у предмета читает ровно один метод (звено Z12), и точное
 * значение пиниться не может.
 *
 * <p><b>Два кейса помечены {@code @Tag("debt")}:</b> их ожидание взято из
 * дома, а код несёт иначе (§«Ожидание берётся из дома, даже когда сегодня
 * оно не исполнено»). Их красный прогон есть предъявление долга, и в
 * умолчание прогона они не входят.
 */
class AccessDenialHandlerTest {

    private static final String SURFACE_PATH = "/api/audit/events";
    private static final String SURFACE = "GET " + SURFACE_PATH;
    private static final String PRESENTED_PRINCIPAL = "holder";

    private final AccessDenialRecorder recorder = mock(AccessDenialRecorder.class);
    private final ObjectMapper objectMapper = consumerMapper();
    private final AccessDenialHandler handler =
            new AccessDenialHandler(Optional.of(recorder), objectMapper);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", SURFACE_PATH);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- U4: отказ аутентификации -----------------------------------------

    @Test
    @DisplayName("U4.1 — 401, заголовок предъявления, единый DTO и ровно один вызов писателя")
    void u4_1_theUnauthenticatedOutcomeIsComplete() throws Exception {
        handler.commence(request, response, new BadCredentialsException("token rejected"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        MediaType contentType = MediaType.parseMediaType(response.getContentType());
        assertThat(contentType.getType() + "/" + contentType.getSubtype())
                .isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");

        var body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_UNAUTHENTICATED");
        assertThat(body.get("message").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("occurredAt").isNull()).isFalse();
        assertThat(body.get("reason").isNull())
                .as("причина пуста: класс отказа её не различает")
                .isTrue();

        verify(recorder).recordPrincipalAbsent(SURFACE);
        verify(recorder, never()).recordOperationForbidden(anyString(), any());
    }

    @Test
    @DisplayName("U4.2 — строка заводится ДО ответа, а не после")
    void u4_2_theTrailIsWrittenBeforeTheResponse() throws Exception {
        AtomicInteger statusAtWrite = new AtomicInteger(-1);
        doAnswer(invocation -> {
            statusAtWrite.set(response.getStatus());
            return null;
        }).when(recorder).recordPrincipalAbsent(anyString());

        handler.commence(request, response, new BadCredentialsException("token rejected"));

        assertThat(statusAtWrite.get())
                .as("строка, заведённая после ответа, терялась бы ровно на том исходе, ради "
                        + "которого заводится")
                .isNotEqualTo(401);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isNotEmpty();
    }

    @Test
    @DisplayName("U4.3 — писателя нет: тот же отказ и ни одной записи")
    void u4_3_theOutcomeIsTheSameWithoutARecorder() throws Exception {
        AccessDenialHandler withoutRecorder =
                new AccessDenialHandler(Optional.empty(), objectMapper);

        withoutRecorder.commence(request, response, new BadCredentialsException("token rejected"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(objectMapper.readTree(response.getContentAsString()).get("code").asText())
                .as("формат ответа от наличия базы не зависит")
                .isEqualTo("ACCESS_UNAUTHENTICATED");
        verify(recorder, never()).recordPrincipalAbsent(anyString());
    }

    @Test
    @DisplayName("U4.4 — query-строка в след не идёт: туда попадают предъявленные секреты")
    void u4_4_theQueryStringIsStrippedFromTheSurface() throws Exception {
        request.setQueryString("access_token=secret");
        request.setParameter("access_token", "secret");

        handler.commence(request, response, new BadCredentialsException("token rejected"));

        verify(recorder).recordPrincipalAbsent(SURFACE);
    }

    @Test
    @DisplayName("U4.5 — длинный путь уходит писателю целиком: усечение — его обязанность")
    void u4_5_theSurfaceIsNotTruncatedByTheEntryPoint() throws Exception {
        String longPath = "/api/audit/" + "a".repeat(300);
        MockHttpServletRequest longRequest = new MockHttpServletRequest("GET", longPath);

        handler.commence(longRequest, response, new BadCredentialsException("token rejected"));

        verify(recorder).recordPrincipalAbsent("GET " + longPath);
    }

    @Test
    @DisplayName("U4.6 — текст отказа наружу не уходит")
    void u4_6_theFailureTextNeverReachesTheBody() throws Exception {
        handler.commence(request, response,
                new BadCredentialsException("token expired at 12:00 for user root"));

        assertThat(response.getContentAsString())
                .as("ни существования объекта, ни его состояния, ни текста исключения")
                .doesNotContain("token expired", "root", "12:00");
        assertThat(objectMapper.readTree(response.getContentAsString()).get("message").asText())
                .isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("U4.7 — имя принципала на этой тропе писателю не передаётся")
    void u4_7_thePrincipalNameIsNotPassedOnTheUnauthenticatedPath() throws Exception {
        givenAcceptedPrincipal();

        handler.commence(request, response, new BadCredentialsException("token rejected"));

        verify(recorder).recordPrincipalAbsent(SURFACE);
        verify(recorder, never()).recordOperationForbidden(anyString(), any());
    }

    @Test
    @DisplayName("U4.8 — бросающий писатель доступом не становится (пробел G2)")
    void u4_8_aThrowingRecorderNeverTurnsIntoAccess() throws Exception {
        doThrow(new RuntimeException("база недоступна"))
                .when(recorder).recordPrincipalAbsent(anyString());

        assertThatThrownBy(() ->
                handler.commence(request, response, new BadCredentialsException("token rejected")))
                .as("охраны у точки входа нет, и цена нарушения контракта порта — ответ не того "
                        + "класса; доступ при этом не даётся")
                .isInstanceOf(RuntimeException.class);
        assertThat(response.getContentAsString())
                .as("тела ответа не написано: отказ записи доступом не становится")
                .isEmpty();
        assertThat(response.isCommitted()).isFalse();
    }

    // --- U5: отказ авторизации --------------------------------------------

    @Test
    @DisplayName("U5.1 — 403, свой класс отказа и принятый принципал в следе")
    void u5_1_theForbiddenOutcomeIsComplete() throws Exception {
        givenAcceptedPrincipal();

        handler.handle(request, response, new AccessDeniedException("not permitted"));

        assertThat(response.getStatus()).isEqualTo(403);
        var body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_FORBIDDEN");
        assertThat(body.get("message").asText()).isEqualTo("Forbidden");

        verify(recorder).recordOperationForbidden(SURFACE, PRESENTED_PRINCIPAL);
        verify(recorder, never()).recordPrincipalAbsent(anyString());
    }

    @Test
    @DisplayName("U5.2 — заголовка предъявления на этой тропе нет: предъявлять нечего")
    void u5_2_thereIsNoChallengeHeaderOnTheForbiddenPath() throws Exception {
        givenAcceptedPrincipal();

        handler.handle(request, response, new AccessDeniedException("not permitted"));

        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    @DisplayName("U5.3 — писателя нет: тот же 403 и ни одной записи")
    void u5_3_theForbiddenOutcomeIsTheSameWithoutARecorder() throws Exception {
        givenAcceptedPrincipal();
        AccessDenialHandler withoutRecorder =
                new AccessDenialHandler(Optional.empty(), objectMapper);

        withoutRecorder.handle(request, response, new AccessDeniedException("not permitted"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(response.getContentAsString()).get("code").asText())
                .isEqualTo("ACCESS_FORBIDDEN");
        verify(recorder, never()).recordOperationForbidden(anyString(), any());
    }

    @Test
    @Tag("debt")
    @DisplayName("U5.4 — пустой контекст: пары «нет принципала при OPERATION_FORBIDDEN» не бывает")
    void u5_4_anEmptyContextNeverYieldsAForbiddenTrailWithoutAPrincipal() throws Exception {
        handler.handle(request, response, new AccessDeniedException("not permitted"));

        verify(recorder, never()).recordOperationForbidden(anyString(), isNull());
    }

    @Test
    @Tag("debt")
    @DisplayName("U5.5 — аноним: неудостоверённое имя в строку не пишется (долг F4)")
    void u5_5_anAnonymousNameIsNeverRecordedAsAFact() throws Exception {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "probe", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        handler.handle(request, response, new AccessDeniedException("not permitted"));

        verify(recorder, never()).recordOperationForbidden(SURFACE, "anonymousUser");
    }

    @Test
    @DisplayName("U5.6 — бросающий писатель доступом не становится и здесь (пробел G2)")
    void u5_6_aThrowingRecorderNeverTurnsIntoAccessOnTheForbiddenPath() throws Exception {
        givenAcceptedPrincipal();
        doThrow(new RuntimeException("база недоступна"))
                .when(recorder).recordOperationForbidden(anyString(), any());

        assertThatThrownBy(() ->
                handler.handle(request, response, new AccessDeniedException("not permitted")))
                .isInstanceOf(RuntimeException.class);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.isCommitted()).isFalse();
    }

    // --- U6: тело отказа ---------------------------------------------------

    @Test
    @DisplayName("U6.1 — тело несёт ровно четыре имени поля, а классом не восстанавливается (находка F9)")
    void u6_1_theBodyCarriesExactlyFourFieldsAndIsNotReadableIntoTheClass() throws Exception {
        handler.commence(request, response, new BadCredentialsException("token rejected"));
        String body = response.getContentAsString();

        List<String> names = new ArrayList<>();
        objectMapper.readTree(body).fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("code", "reason", "message", "occurredAt");

        assertThatThrownBy(() -> objectMapper.readValue(body, ErrorApiResponse.class))
                .as("читатель единого error-DTO не восстанавливает его вовсе: ноль конструкторов "
                        + "и ни одного creator'а")
                .isInstanceOf(InvalidDefinitionException.class);
    }

    @Test
    @DisplayName("U6.2 — момент лежит в границе прогона и несёт нулевое смещение")
    void u6_2_theMomentIsWithinTheRunAndCarriesNoOffset() throws Exception {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        handler.commence(request, response, new BadCredentialsException("token rejected"));

        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime occurredAt = OffsetDateTime.parse(
                objectMapper.readTree(response.getContentAsString()).get("occurredAt").asText());
        assertThat(occurredAt).isBetween(before, after);
        assertThat(occurredAt.getOffset())
                .as("шкала одна — UTC (docs/rules/time-utc.md)")
                .isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("U6.3 — сборщик без модуля времени: 401 с ПУСТЫМ телом, статус выставлен до сериализации")
    void u6_3_aMapperWithoutTheTimeModuleYieldsAnEmptyBody() throws Exception {
        AccessDenialHandler withBareMapper =
                new AccessDenialHandler(Optional.of(recorder), new ObjectMapper());

        assertThatThrownBy(() ->
                withBareMapper.commence(request, response, new BadCredentialsException("nope")))
                .isInstanceOf(InvalidDefinitionException.class);

        assertThat(response.getStatus())
                .as("исход точки входа зависит от бина потребителя — ось, ради которой предмет "
                        + "стои́т пятым в шестёрке")
                .isEqualTo(401);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("U6.4 — пояснение равно фразе статуса, а не тексту исключения")
    void u6_4_theMessageIsTheStatusReasonPhrase() throws Exception {
        givenAcceptedPrincipal();

        handler.handle(request, response, new AccessDeniedException("роль не та"));

        assertThat(objectMapper.readTree(response.getContentAsString()).get("message").asText())
                .isEqualTo("Forbidden");
        assertThat(response.getContentAsString())
                .doesNotContain("роль не та", PRESENTED_PRINCIPAL);
    }

    @Test
    @DisplayName("U6.5 — два исхода различаются машинно, а не одним")
    void u6_5_theTwoOutcomesAreMachineReadablyDifferent() throws Exception {
        handler.commence(request, response, new BadCredentialsException("token rejected"));
        String unauthenticated = objectMapper.readTree(response.getContentAsString())
                .get("code").asText();

        MockHttpServletResponse forbiddenResponse = new MockHttpServletResponse();
        givenAcceptedPrincipal();
        handler.handle(request, forbiddenResponse, new AccessDeniedException("not permitted"));
        String forbidden = objectMapper.readTree(forbiddenResponse.getContentAsString())
                .get("code").asText();

        assertThat(unauthenticated).isNotEqualTo(forbidden);
        assertThat(List.of(unauthenticated, forbidden))
                .containsExactly("ACCESS_UNAUTHENTICATED", "ACCESS_FORBIDDEN");
    }

    @Test
    @DisplayName("U6.6 — пустая причина означает «различать нечего», а не «причина неизвестна»")
    void u6_6_theAbsentReasonMeansThereIsNothingToDistinguish() throws Exception {
        handler.commence(request, response, new BadCredentialsException("token rejected"));

        assertThat(objectMapper.readTree(response.getContentAsString()).get("reason").isNull())
                .isTrue();
    }

    // --- U14.7: чего точка входа не делает --------------------------------

    @Test
    @DisplayName("U14.7 — на штатных тропах точка входа не бросает")
    void u14_7_theEntryPointNeverThrowsOnItsRegularPaths() {
        assertThatCode(() ->
                handler.commence(request, response, new BadCredentialsException("token rejected")))
                .doesNotThrowAnyException();

        givenAcceptedPrincipal();
        assertThatCode(() ->
                handler.handle(request, new MockHttpServletResponse(),
                        new AccessDeniedException("not permitted")))
                .doesNotThrowAnyException();
    }

    // --- оснастка ---------------------------------------------------------

    /**
     * Сборщик тела — тот же, что каркас даёт потребителю: маппер с модулем
     * времени. Свой собрать нельзя: момент в теле — {@code OffsetDateTime},
     * и на голом маппере сериализация отказывает (`U6.3`).
     */
    private static ObjectMapper consumerMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private static void givenAcceptedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                PRESENTED_PRINCIPAL, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
    }
}
