package com.example.strategies.api;

import static java.util.Objects.isNull;

import com.example.strategies.integration.internal.api.PeerReadException;
import com.example.strategies.integration.internal.api.PeerServiceUnavailableException;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Единая внешняя поверхность ошибок: один {@code @RestControllerAdvice},
 * один error-DTO (docs/rules/error-handling-policy.md; конвенция —
 * .claude/rules/codestyle.md §«Обработка ошибок»).
 *
 * <p><b>Отказ соседа не выдаётся за отказ автора.</b> Недоступность ядра
 * — это {@code 503}: повторить запрос осмысленно, и правки определения
 * он не требует. Осознанный отказ соседа — наш дефект и повтором не
 * лечится, поэтому {@code 502}: вызывающий тут ни при чём
 * (docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу —
 * свой класс»).
 *
 * <p><b>Перечень классов закрыт с обеих сторон, и это несущее.</b> Клейм
 * «единый error-DTO» держится, только когда СВОЙ формат получают и те
 * отказы, которых наши обработчики не называют поимённо. Таких классов
 * два рода, и оба наблюдались отвечающими <b>пустым телом</b>:
 * <ul>
 *   <li><b>отказы самого контейнера</b> — неразбираемое тело, непредъявленный
 *       заголовок контекста, неизвестный путь, неподдержанный метод. Их
 *       статусы и разбор уже написаны в {@link ResponseEntityExceptionHandler},
 *       поэтому наследуются они, а подменяется только ТЕЛО ответа:
 *       переписывать статусы значило бы завести второй их носитель;</li>
 *   <li><b>всё непредусмотренное</b> — ловится последним обработчиком и
 *       отвечает {@code 500} без текста исключения.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Отказы валидации и жизненного цикла несут свой статус и свой код. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorApiResponse> onResponseStatus(ResponseStatusException failure) {
        return response(HttpStatus.valueOf(failure.getStatusCode().value()),
                "STRATEGY_REQUEST_REJECTED", failure.getReason());
    }

    /** Негодный вход вызова: неизвестная идентичность, неразобранное значение перечня. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> onIllegalArgument(IllegalArgumentException failure) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", failure.getMessage());
    }

    /** Сосед недоступен: операнд не добыт, и создание отвергается, а не проходит непроверенным. */
    @ExceptionHandler(PeerServiceUnavailableException.class)
    public ResponseEntity<ErrorApiResponse> onPeerUnavailable(PeerServiceUnavailableException failure) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PEER_UNAVAILABLE", failure.getMessage());
    }

    /** Сосед отказал осознанно: наш дефект, повтором не лечится. */
    @ExceptionHandler(PeerReadException.class)
    public ResponseEntity<ErrorApiResponse> onPeerRefused(PeerReadException failure) {
        return response(HttpStatus.BAD_GATEWAY, "PEER_REFUSED", failure.getMessage());
    }

    /**
     * Всё непредусмотренное — наш дефект, а не вход вызывающего.
     *
     * <p><b>Текст исключения наружу не идёт</b>: он рассказывает о
     * внутреннем устройстве тому, кто о нём знать не должен
     * (docs/rules/error-handling-policy.md §«Что отказ НЕ сообщает»). В
     * лог он идёт целиком — там его читает держатель.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApiResponse> onUnexpected(Exception failure) {
        log.error("Unhandled failure on the strategies surface", failure);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_FAILURE",
                "Внутренний отказ сервиса");
    }

    /**
     * Отказы контейнера отвечают нашим телом при своём статусе.
     *
     * <p>Пояснение берётся из {@code ProblemDetail}, который контейнер уже
     * собрал: своё написать было бы вторым носителем того же текста, а
     * пустое оставило бы вызывающего без причины отказа.
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
