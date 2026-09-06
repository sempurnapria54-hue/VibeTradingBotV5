package com.example.bff.api;

import static java.util.Objects.isNull;

import com.example.bff.domain.TicketRejectedException;
import com.example.bff.integration.PeerServiceUnavailableException;
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
 * Единая внешняя поверхность ошибок периметра: один
 * {@code @RestControllerAdvice}, один error-DTO
 * (docs/rules/error-handling-policy.md §«Внешняя поверхность»).
 *
 * <p><b>Перечень классов закрыт с обеих сторон, и это несущее.</b> Клейм
 * «единый error-DTO» держится, только когда свой формат получают и те
 * отказы, которых обработчики не называют поимённо: отказы самого
 * контейнера (неразбираемое тело, непредъявленный заголовок, неизвестный
 * путь, неподдержанный метод) и всё непредусмотренное. Оба рода
 * наблюдались отвечающими ПУСТЫМ телом у соседнего сервиса, и оба здесь
 * закрыты с первого дня.
 *
 * <p><b>Отказ билета отвечает как отказ доступа.</b> Форм предъявления
 * две — токен и билет, — а формат отказа один: иначе у поверхности
 * появился бы второй формат ровно на той тропе, которую периметр и
 * заводит.
 *
 * <p><b>Отказ владельца не выдаётся за отказ автора:</b> недоступность —
 * {@code 503}, повторить осмысленно
 * (docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу —
 * свой класс»).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Билет не предъявлен, испорчен либо просрочен — тот же класс, что отказ доступа. */
    @ExceptionHandler(TicketRejectedException.class)
    public ResponseEntity<ErrorApiResponse> onTicketRejected(TicketRejectedException failure) {
        log.info("A subscription ticket has been rejected");
        return response(HttpStatus.UNAUTHORIZED, "ACCESS_UNAUTHENTICATED",
                HttpStatus.UNAUTHORIZED.getReasonPhrase());
    }

    /** Отказы резолва контекста несут свой статус и свой код. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorApiResponse> onResponseStatus(ResponseStatusException failure) {
        return response(HttpStatus.valueOf(failure.getStatusCode().value()),
                "PERIMETER_REQUEST_REJECTED", failure.getReason());
    }

    /** Негодный вход вызова: неизвестный глагол, неразобранное значение. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> onIllegalArgument(IllegalArgumentException failure) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", failure.getMessage());
    }

    /** Владелец недоступен: ответа нет, и выдавать это за свой отказ нечестно. */
    @ExceptionHandler(PeerServiceUnavailableException.class)
    public ResponseEntity<ErrorApiResponse> onPeerUnavailable(PeerServiceUnavailableException failure) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PEER_UNAVAILABLE", failure.getMessage());
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
        log.error("Unhandled failure on the perimeter surface", failure);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_FAILURE", "Внутренний отказ сервиса");
    }

    /**
     * Отказы контейнера отвечают нашим телом при своём статусе.
     *
     * <p>Пояснение берётся из {@code ProblemDetail}, который контейнер
     * уже собрал: своё написать было бы вторым носителем того же текста,
     * а пустое оставило бы вызывающего без причины отказа.
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
