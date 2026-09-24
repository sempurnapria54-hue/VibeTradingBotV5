package com.example.marketdata.exception.handler;

import static java.util.Objects.isNull;

import com.example.marketdata.exception.ExchangeAccessException;
import com.example.marketdata.exception.ExchangeReadException;
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
 * Единая внешняя поверхность ошибок: один advice, один error-DTO
 * (docs/rules/error-handling-policy.md; конвенция —
 * .claude/rules/codestyle.md §«Обработка ошибок»).
 *
 * <p><b>Отказ площадки наружу переезжает как отказ ЗАВИСИМОСТИ, а не как
 * наша ошибка.</b> Читатель рыночных данных не виноват в том, что
 * площадка отказала, и {@code 500} сказал бы ему «чини запрос»;
 * {@code 502}/{@code 503} говорят «повтори позже» — единственная реакция,
 * которая здесь уместна.
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

    /** Площадка отказала в доступе либо исчерпан лимит: повтор имеет смысл не сразу. */
    @ExceptionHandler(ExchangeAccessException.class)
    public ResponseEntity<ErrorApiResponse> onExchangeAccess(ExchangeAccessException failure) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "EXCHANGE_ACCESS_REFUSED", failure.getMessage());
    }

    /** Прочий отказ чтения площадки через коннектор. */
    @ExceptionHandler(ExchangeReadException.class)
    public ResponseEntity<ErrorApiResponse> onExchangeRead(ExchangeReadException failure) {
        return response(HttpStatus.BAD_GATEWAY, "EXCHANGE_READ_FAILED", failure.getMessage());
    }

    /** Неизвестный инструмент, неизвестная идентичность и прочий негодный вход. */
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
        log.error("Unhandled failure on the market-data surface", failure);
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
