package com.example.tradingbot.api;

import static java.util.Objects.isNull;

import com.example.tradingbot.api.model.response.ErrorApiResponse;
import com.example.tradingbot.integration.service.ControlledExchangeException;
import com.example.tradingbot.integration.service.ExchangeIntegrationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Единый глобальный обработчик ошибок внешней поверхности
 * (docs/rules/error-handling-policy.md): все исключения контроллеров
 * маппятся в {@link ErrorApiResponse} в одном месте, не per-endpoint.
 * FSM/оркестрация наружу не транслируются. Набор HTTP-кодов — провизорный
 * (хвост пользователя), фиксируется по мере закрытия ретро-майоров; здесь
 * — форма (единый advice + единый DTO) и разумный дефолтный маппинг.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Не найдено (getRequiredBy* кидает IllegalArgumentException). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> handleNotFound(IllegalArgumentException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    /** Нарушение валидации тела запроса. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApiResponse> handleBodyValidation(MethodArgumentNotValidException e,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    /** Нарушение валидации параметров/constraint'ов. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorApiResponse> handleConstraint(ConstraintViolationException e,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    /** Недопустимое состояние/переход (например, illegal FSM transition). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorApiResponse> handleConflict(IllegalStateException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    /** Контролируемый внешний факт биржи (not found / status / invariant). */
    @ExceptionHandler(ControlledExchangeException.class)
    public ResponseEntity<ErrorApiResponse> handleControlled(ControlledExchangeException e,
                                                             HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), request);
    }

    /** Транспорт/API-сбой взаимодействия с биржей. */
    @ExceptionHandler(ExchangeIntegrationException.class)
    public ResponseEntity<ErrorApiResponse> handleExchange(ExchangeIntegrationException e,
                                                           HttpServletRequest request) {
        log.error("Exchange integration error on {}", request.getRequestURI(), e);
        return build(HttpStatus.BAD_GATEWAY, e.getMessage(), request);
    }

    /**
     * Явно проброшенный контроллером/сервисом статус (validator → 400,
     * activate-семантика → 422): статус и reason сохраняются, иначе
     * catch-all Exception ниже превратил бы их в 500.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorApiResponse> handleResponseStatus(ResponseStatusException e,
                                                                 HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        return build(isNull(status) ? HttpStatus.INTERNAL_SERVER_ERROR : status, e.getReason(), request);
    }

    /** Непредвиденная ошибка — наружу тонко (без утечки внутренностей), в лог — полностью. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApiResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", request);
    }

    private ResponseEntity<ErrorApiResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorApiResponse body = ErrorApiResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
