package com.example.auth.exception.handler;

import static java.util.Objects.isNull;

import com.example.auth.exception.ContourNotAdmittedException;
import com.example.tradingbot.api.model.ErrorApiResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Единая внешняя поверхность ошибок: один `@RestControllerAdvice`, один
 * error-DTO (docs/rules/error-handling-policy.md; конвенция —
 * .claude/rules/codestyle.md §«Обработка ошибок»).
 *
 * <p><b>Заведён вместе с первым исключением, а не позже.</b> Контроллер
 * объявлял `422` на недопустимый контур, но без обработчика запрос
 * оканчивался бы `500`: объявленный код отличался бы от отдаваемого, и
 * потребитель поверхности верил бы контракту, которого нет.
 *
 * <p><b>Перечень классов закрыт с обеих сторон.</b> Свой формат получают
 * и отказы, которых обработчики не называют поимённо: отказы самого
 * контейнера (неразбираемое тело, непредъявленный параметр, неизвестный
 * путь, неподдержанный метод) наследуются из
 * {@link ResponseEntityExceptionHandler} со своими статусами, а
 * подменяется только тело; всё непредусмотренное ловит последний
 * обработчик. Без них такие отказы отвечали пустым телом — вторым
 * форматом (docs/rules/error-handling-policy.md §«Внешняя поверхность»).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

    /**
     * Всё непредусмотренное — наш дефект, а не вход вызывающего.
     *
     * <p><b>Текст исключения наружу не идёт</b>
     * (docs/rules/error-handling-policy.md §«Что отказ НЕ сообщает»); в лог
     * он идёт целиком.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApiResponse> onUnexpected(Exception failure) {
        log.error("Unhandled failure on the auth surface", failure);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_FAILURE", "Внутренний отказ сервиса");
    }

    /**
     * Отказы контейнера отвечают нашим телом при своём статусе; пояснение
     * берётся из {@code ProblemDetail}, который контейнер уже собрал.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception failure, Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        return new ResponseEntity<>(errorBody("REQUEST_NOT_ACCEPTED", detailOf(failure, body)),
                headers, statusCode);
    }

    /** Пояснение контейнера; пусто — его не было, и выдумывать его нечем. */
    private String detailOf(Exception failure, Object body) {
        if (body instanceof ProblemDetail problem) {
            return problem.getDetail();
        }
        if (failure instanceof ErrorResponse errorResponse) {
            return errorResponse.getBody().getDetail();
        }
        return null;
    }

    private ResponseEntity<ErrorApiResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(errorBody(code, message));
    }

    private ErrorApiResponse errorBody(String code, String message) {
        return ErrorApiResponse.builder()
                .code(code)
                .message(isNull(message) ? "Запрос отвергнут" : message)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
