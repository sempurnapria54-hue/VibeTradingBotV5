package com.example.strategies.api;

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
 * доступа»</b> внешней поверхности владельца определений
 * (docs/rules/error-handling-policy.md §«Отказ доступа — тот же контракт,
 * что и прочие ошибки»).
 *
 * <p><b>Почему энфорсер свой, а не глобальный обработчик.</b> Отказ
 * доступа возникает <b>до</b> контроллера, в фильтр-цепочке, и
 * {@code @RestControllerAdvice} его не видит по построению. Единый
 * error-DTO при этом сохраняется: обе точки собирают то же
 * {@link ErrorApiResponse}. Без них контур отвечал ПУСТЫМ телом, то есть
 * вторым форматом — тем самым, существование которого клейм «единый DTO»
 * и отрицает.
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
 * <p><b>Журнальной строки отказа здесь нет, и это названный долг, а не
 * чужой предмет.</b> Строку отказа заводит сервис, у которого отказ
 * произошёл <b>и</b> есть своя база
 * (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого есть
 * база»); своя база у владельца определений есть, значит строка положена и
 * здесь. Прежняя редакция этого абзаца отсылала к {@code auth} — то есть к
 * сервису, у которого таблицы тоже нет, — и читалась как «предмет чужой».
 * Что построено, а чего нет, читается прогоном, названным в дому долга
 * (.claude/work/backlog.md §«Таблица отказов доступа у сервисов со своей
 * базой»); до его закрытия след отказа здесь — лог контура, а не строка.
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
