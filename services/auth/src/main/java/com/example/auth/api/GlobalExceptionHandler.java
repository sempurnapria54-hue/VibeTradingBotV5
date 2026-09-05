package com.example.auth.api;

import com.example.auth.domain.service.ContourNotAdmittedException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Единая внешняя поверхность ошибок: один `@RestControllerAdvice`, один
 * error-DTO (docs/rules/error-handling-policy.md; конвенция —
 * .claude/rules/codestyle.md §«Обработка ошибок»).
 *
 * <p><b>Заведён вместе с первым исключением, а не позже.</b> Контроллер
 * объявлял `422` на недопустимый контур, но без обработчика запрос
 * оканчивался бы `500`: объявленный код отличался бы от отдаваемого, и
 * потребитель поверхности верил бы контракту, которого нет.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Контур счёта не допускается окружением.
     *
     * <p>`422`, а не `400`: запрос синтаксически корректен и понят —
     * отвергнуто его СОДЕРЖАНИЕ по правилу окружения.
     */
    @ExceptionHandler(ContourNotAdmittedException.class)
    public ResponseEntity<ErrorApiResponse> onContourNotAdmitted(ContourNotAdmittedException failure) {
        return response(HttpStatus.UNPROCESSABLE_CONTENT, "CONTOUR_NOT_ADMITTED", failure.getMessage());
    }

    /** Неизвестный тенант, неразобранное значение перечня и прочий негодный вход. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> onIllegalArgument(IllegalArgumentException failure) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", failure.getMessage());
    }

    private ResponseEntity<ErrorApiResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorApiResponse.builder()
                .code(code)
                .message(message)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}
