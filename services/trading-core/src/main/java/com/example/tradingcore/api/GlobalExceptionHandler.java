package com.example.tradingcore.api;

import com.example.tradingcore.integration.internal.api.PeerServiceUnavailableException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Единая внешняя поверхность ошибок: один advice, один error-DTO
 * (docs/rules/error-handling-policy.md; конвенция —
 * .claude/rules/codestyle.md §«Обработка ошибок»).
 *
 * <p><b>Внутренняя градация наружу не торчит.</b> Ступени, строки
 * исполнения и статусы сделок остаются внутри: снаружи видны класс отказа
 * и пояснение.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Отказ при запуске сервисной операции: недопустимая пара, объект вне
     * множества входа, невыполненное предусловие, неизвестная
     * идентичность. Все они — негодный ВХОД вызова, и отвечать на них
     * пятисотым значило бы сказать «чини сервер» тому, кто чинить обязан
     * запрос.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> onIllegalArgument(IllegalArgumentException failure) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", failure.getMessage());
    }

    /**
     * Сосед по ярусу недоступен: наша сторона исправна, и повтор имеет
     * смысл позже (docs/rules/runtime-error-classification.md §«Отказ
     * соседа по ярусу»).
     */
    @ExceptionHandler(PeerServiceUnavailableException.class)
    public ResponseEntity<ErrorApiResponse> onPeerUnavailable(PeerServiceUnavailableException failure) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PEER_SERVICE_UNAVAILABLE", failure.getMessage());
    }

    private ResponseEntity<ErrorApiResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorApiResponse.builder()
                .code(code)
                .message(message)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}
