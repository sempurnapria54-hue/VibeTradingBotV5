package com.example.bff.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Точки входа отказа фильтр-цепочки — <b>энфорсер класса «отказ
 * доступа»</b> внешней поверхности периметра
 * (docs/rules/error-handling-policy.md §«Отказ доступа — тот же контракт,
 * что и прочие ошибки»).
 *
 * <p><b>Почему энфорсер свой, а не глобальный обработчик.</b> Отказ
 * доступа возникает <b>до</b> контроллера, в фильтр-цепочке, и
 * {@code @RestControllerAdvice} его не видит по построению. Единый
 * error-DTO при этом сохраняется: обе точки собирают то же
 * {@link ErrorApiResponse}. Пустое тело было бы вторым форматом — тем
 * самым, существование которого клейм «единый DTO» и отрицает.
 *
 * <p><b>Два исхода, а не один.</b> Принятого принципала нет — одно;
 * принципал принят, но операция не разрешена — другое. Второй сегодня не
 * достижим ни одной тропой (пер-операционных проверок права нет ни у
 * одного сервиса — docs/rules/api-access-policy.md), и заведён он
 * потому, что это контракт внешней поверхности.
 *
 * <p><b>Журнальной строки отказа здесь нет, и это не пропуск:</b> у
 * периметра базы нет, и след отказа там — лог и метрика
 * (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого есть
 * база»). Запись через соседа поставила бы сетевой вызов и строку в чужой
 * БД на тропу, объём которой задаёт не наш пользователь.
 */
@Component
@RequiredArgsConstructor
public class AccessDenialHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Схема предъявления: контур принимает bearer-токен провайдера идентичности. */
    private static final String BEARER_CHALLENGE = "Bearer";

    private final ObjectMapper objectMapper;

    /** Токен не предъявлен либо предъявленный не принят. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException failure) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        respond(response, HttpStatus.UNAUTHORIZED, "ACCESS_UNAUTHENTICATED");
    }

    /** Токен принят, но операция вызывающему не разрешена. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException failure) throws IOException {
        respond(response, HttpStatus.FORBIDDEN, "ACCESS_FORBIDDEN");
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
