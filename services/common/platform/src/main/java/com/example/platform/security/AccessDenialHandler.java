package com.example.platform.security;

import static java.util.Objects.isNull;

import com.example.tradingbot.api.model.ErrorApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
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
 * <p><b>Писатель объявлен {@code Optional}, а не обязательным бином:</b>
 * его отсутствие — законное состояние сервиса без своей базы, а не
 * недостающая настройка. Пустой писатель так читается по признаку,
 * а не по умолчанию (docs/rules/absent-value-semantics.md).
 */
@Component
@RequiredArgsConstructor
public class AccessDenialHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Схема предъявления: контур принимает bearer-токен провайдера идентичности. */
    private static final String BEARER_CHALLENGE = "Bearer";

    /** Класс отказа: принятого принципала нет. */
    private static final String UNAUTHENTICATED_CODE = "ACCESS_UNAUTHENTICATED";

    /** Класс отказа: принципал принят, операция не разрешена. */
    private static final String FORBIDDEN_CODE = "ACCESS_FORBIDDEN";

    private final Optional<AccessDenialRecorder> recorder;
    private final ObjectMapper objectMapper;

    /** Токен не предъявлен либо предъявленный не принят. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException failure) throws IOException {
        recorder.ifPresent(writer -> writer.recordPrincipalAbsent(surfaceOf(request)));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        respond(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_CODE);
    }

    /** Токен принят, но операция вызывающему не разрешена. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException failure) throws IOException {
        recorder.ifPresent(writer -> writer.recordOperationForbidden(surfaceOf(request), acceptedPrincipal()));
        respond(response, HttpStatus.FORBIDDEN, FORBIDDEN_CODE);
    }

    /**
     * Имя <b>принятого</b> принципала: на этой тропе аутентификация уже
     * прошла, и контур личность удостоверил.
     */
    private String acceptedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return isNull(authentication) ? null : authentication.getName();
    }

    /**
     * Куда стучались: метод и путь <b>без query-строки</b>. Параметры
     * запроса в след не идут — их содержимое под контролем вызывающего, и
     * туда попадают предъявленные секреты.
     */
    private String surfaceOf(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    private void respond(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorApiResponse.builder()
                .code(code)
                .message(status.getReasonPhrase())
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build()));
    }
}
