package com.example.platform.exception.handler;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.api.model.ErrorApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Точки входа отказа фильтр-цепочки — <b>энфорсер класса «отказ доступа»</b>
 * внешней поверхности (docs/rules/error-handling-policy.md §«Отказ доступа
 * — тот же контракт, что и прочие ошибки»).
 *
 * <p><b>Почему энфорсер свой, а не глобальный обработчик.</b> Отказ доступа
 * возникает <b>до</b> контроллера, в фильтр-цепочке, и
 * {@code @RestControllerAdvice} его не видит по построению. Единый
 * error-DTO при этом сохраняется: обе точки собирают то же
 * {@link ErrorApiResponse}. Без них контур отвечал бы ПУСТЫМ телом, то есть
 * вторым форматом — тем самым, существование которого клейм «единый DTO» и
 * отрицает.
 *
 * <p><b>Два исхода, а не один.</b> Принятого принципала нет — одно;
 * принципал принят, но операция не разрешена — другое. Второй сегодня не
 * достижим ни одной тропой (пер-операционных проверок права нет —
 * docs/rules/api-access-policy.md), и заведён он потому, что это контракт
 * внешней поверхности: слить исходы дешевле сейчас и дороже потом.
 *
 * <p><b>Наружу не уходит ничего сверх класса</b> — ни существования
 * объекта, ни его состояния, ни текста исключения.
 *
 * <p><b>Порядок обязателен: след заводится ДО ответа.</b> Ответ вызывающему
 * — последнее, что делает тропа; строка, заведённая после, терялась бы
 * ровно на том исходе, ради которого заводится
 * (docs/models/domain/other/AccessDenial.md §Инварианты). Писателя может и
 * не быть: у сервиса без своей базы след отказа — лог и метрика, и тогда
 * точка входа отвечает тем же отказом, ничего не записывая
 * ({@link AccessDenialRecorder}).
 *
 * <p><b>Строку лога точка входа пишет на КАЖДОМ отказе, при любом
 * писателе.</b> У сервиса без своей базы лог — половина следа
 * (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого есть
 * база»), и ветвь «писателя нет» пустой не бывает; у сервиса с базой лог
 * носителем не является, но остаётся единственным следом, если строку
 * завести не удалось. Что сверх лога — строку, счётчик, — добавляет
 * писатель своего сервиса.
 *
 * <p><b>Отказ писателя ответа не отменяет.</b> Контракт порта кладёт
 * поглощение сбоя записи на реализацию, но наблюдателя у этой обязанности
 * нет: реализация, нарушившая её, унесла бы тропу до установки статуса, и
 * класс ответа выбрал бы контейнер. Поэтому точка входа охраняет себя
 * сама — сбой писателя уходит в лог, а вызывающий получает тот же отказ
 * (docs/concept.md П1, следствие 3).
 *
 * <p><b>Класс отказа выбирается по ПРИНЯТОМУ принципалу, а не по тропе,
 * которой пришёл вызов.</b> Неудостоверённое — пустой контекст,
 * непринятая аутентификация, аноним — признаётся тем же признаком, что у
 * {@code ActorProvider}; на тропе авторизации оно отвечает как отказ
 * аутентификации, и пары «операция не разрешена без принципала» не
 * возникает (docs/models/domain/other/AccessDenial.md §Инварианты).
 *
 * <p><b>Третья точка — отказ самого звена цепочки, и отказом доступа он не
 * является.</b> Звено, проверяющее токен, может не суметь его проверить —
 * провайдер ключей недоступен, — и тогда отказ приходит
 * {@link AuthenticationServiceException}: это наш сбой, а не непринятый
 * принципал. Умолчание звена такое исключение перебрасывает мимо точки
 * входа, контейнер ставит {@code 500} и диспетчит страницу ошибки, а на
 * ней цепочка отвечает {@code 401} без тела поверх — серверный класс
 * снаружи неотличим от отказа доступа. Поэтому звено получает этот
 * обработчик ({@link BearerTokenFailureInstaller}; отказ сборки декодера
 * доводит до него {@link JwtDecoderAssemblyGuard}), и сбой отвечает
 * {@code 500} тем же error-DTO, что и последний обработчик поверхности,
 * без текста исключения и без следа отказа доступа
 * (docs/rules/error-handling-policy.md §«Отказ, произведённый контейнером,
 * — тот же контракт»).
 *
 * <p><b>Писатель объявлен {@code Optional}, а не обязательным бином:</b>
 * его отсутствие — законное состояние сервиса без своей базы, а не
 * недостающая настройка. Пустой писатель так читается по признаку,
 * а не по умолчанию (docs/rules/absent-value-semantics.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDenialHandler implements AuthenticationEntryPoint, AccessDeniedHandler,
        AuthenticationFailureHandler {

    /** Схема предъявления: контур принимает bearer-токен провайдера идентичности. */
    private static final String BEARER_CHALLENGE = "Bearer";

    /** Класс отказа: принятого принципала нет. */
    private static final String UNAUTHENTICATED_CODE = "ACCESS_UNAUTHENTICATED";

    /** Класс отказа: принципал принят, операция не разрешена. */
    private static final String FORBIDDEN_CODE = "ACCESS_FORBIDDEN";

    /** Класс отказа: сбой сервиса — тот же, что у последнего обработчика поверхности. */
    private static final String INTERNAL_FAILURE_CODE = "INTERNAL_FAILURE";

    /** Пояснение сбоя сервиса — то же, что у последнего обработчика поверхности. */
    private static final String INTERNAL_FAILURE_MESSAGE = "Внутренний отказ сервиса";

    private final Optional<AccessDenialRecorder> recorder;
    private final ObjectMapper objectMapper;

    /** Токен не предъявлен либо предъявленный не принят. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException failure) throws IOException {
        String surface = surfaceOf(request);
        leaveTrail(UNAUTHENTICATED_CODE, surface, writer -> writer.recordPrincipalAbsent(surface));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        respond(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_CODE, HttpStatus.UNAUTHORIZED.getReasonPhrase());
    }

    /**
     * Токен принят, но операция вызывающему не разрешена. Принятого
     * принципала нет — это отказ аутентификации, а не авторизации, и
     * отвечает он тем же исходом.
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException failure) throws IOException {
        String principal = acceptedPrincipal();
        if (isNull(principal)) {
            commence(request, response, new InsufficientAuthenticationException(failure.getMessage(), failure));
            return;
        }
        String surface = surfaceOf(request);
        leaveTrail(FORBIDDEN_CODE, surface, writer -> writer.recordOperationForbidden(surface, principal));
        respond(response, HttpStatus.FORBIDDEN, FORBIDDEN_CODE, HttpStatus.FORBIDDEN.getReasonPhrase());
    }

    /**
     * Звено цепочки не приняло токен. Сбой самого звена —
     * {@link AuthenticationServiceException} — есть сбой сервиса и отвечает
     * {@code 500}; всякий иной отказ есть непринятый принципал и идёт в
     * точку входа.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException failure) throws IOException {
        if (failure instanceof AuthenticationServiceException) {
            log.error("Authentication could not be completed: surface={}", surfaceOf(request), failure);
            respond(response, HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_FAILURE_CODE, INTERNAL_FAILURE_MESSAGE);
            return;
        }
        commence(request, response, failure);
    }

    /**
     * След отказа — до ответа: строка лога всегда, запись писателя — когда
     * он есть. Писатель отказал — строка лога об отказе, а ответ остаётся
     * тем же отказом.
     */
    private void leaveTrail(String code, String surface, Consumer<AccessDenialRecorder> write) {
        log.warn("Access denied: code={}, surface={}", code, surface);
        try {
            recorder.ifPresent(write);
        } catch (RuntimeException failure) {
            log.error("Access denial trail not left: code={}, surface={}, cause={}",
                    code, surface, failure.getMessage(), failure);
        }
    }

    /**
     * Имя <b>принятого</b> принципала либо пусто, когда контур личность не
     * удостоверил: пустой контекст, непринятая аутентификация, аноним.
     */
    private String acceptedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isNull(authentication)
                || isFalse(authentication.isAuthenticated())
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * Куда стучались: метод и путь <b>без query-строки</b>. Параметры
     * запроса в след не идут — их содержимое под контролем вызывающего, и
     * туда попадают предъявленные секреты.
     */
    private String surfaceOf(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    private void respond(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorApiResponse.builder()
                .code(code)
                .message(message)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build()));
    }
}
