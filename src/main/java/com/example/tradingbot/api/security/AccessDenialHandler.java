package com.example.tradingbot.api.security;

import static java.util.Objects.isNull;

import com.example.tradingbot.api.ErrorApiResponseFactory;
import com.example.tradingbot.domain.security.AccessDenial;
import com.example.tradingbot.domain.security.AccessDenialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * внешней поверхности (docs/rules/error-handling-policy.md).
 *
 * <p><b>Почему энфорсер свой, а не глобальный обработчик.</b> Отказ доступа
 * возникает <b>до</b> контроллера, и {@code @RestControllerAdvice} его не
 * видит по построению. Единый error-DTO при этом сохраняется: обе точки
 * делегируют в тот же {@link ErrorApiResponseFactory}, что и обработчик.
 *
 * <p><b>Два исхода, а не один.</b> Принятого принципала нет — одно; принципал
 * принят, но операция не разрешена — другое. Второй не достижим при посылке
 * фазы 1 «к поверхности обращается один субъект»
 * (docs/rules/api-access-policy.md), и заведён он потому, что это контракт
 * внешней поверхности: слить исходы дешевле сейчас и дороже потом.
 *
 * <p><b>Порядок обязателен: строка заводится ДО ответа.</b> Ответ вызывающему —
 * последнее, что делает тропа; строка, заведённая после, терялась бы ровно на
 * том исходе, ради которого заводится.
 *
 * <p><b>Наружу не уходит ничего сверх класса</b> — ни существования объекта,
 * ни его состояния, ни причины отказа: текст исключения в ответ не попадает.
 * Коды (401/403) — часть провизорного набора внешней поверхности.
 */
@Component
@RequiredArgsConstructor
public class AccessDenialHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AccessDenialService denialService;
    private final ErrorApiResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    /** Принципал не предъявлен либо предъявленный не принят. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        denialService.record(surfaceOf(request), AccessDenial.Outcome.PRINCIPAL_ABSENT, null);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"tradingbot\"");
        respond(request, response, HttpStatus.UNAUTHORIZED);
    }

    /** Принципал принят, но операция ему не разрешена. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        denialService.record(surfaceOf(request), AccessDenial.Outcome.OPERATION_FORBIDDEN, acceptedPrincipal());
        respond(request, response, HttpStatus.FORBIDDEN);
    }

    /**
     * Имя <b>принятого</b> принципала. Заявленное, но не удостоверенное имя
     * сюда не попадает: на этой тропе аутентификация уже прошла.
     */
    private String acceptedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return isNull(authentication) ? null : authentication.getName();
    }

    /** Поверхность, к которой обратились: метод и путь — без query-строки. */
    private String surfaceOf(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    private void respond(HttpServletRequest request, HttpServletResponse response, HttpStatus status)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                responseFactory.build(status, status.getReasonPhrase(), request.getRequestURI()));
    }
}
