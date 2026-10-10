package com.example.tradingcore.exception.handler;

import static java.util.Objects.isNull;

import com.example.platform.exception.PeerServiceUnavailableException;
import com.example.tradingbot.api.model.ErrorApiResponse;
import jakarta.validation.ConstraintViolationException;
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
 * <p><b>Внутренняя градация наружу не торчит.</b> Ступени, строки
 * исполнения и статусы сделок остаются внутри: снаружи видны класс отказа
 * и пояснение.
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

    /** Класс отказа контейнера и равного ему отказа валидации вызова. */
    private static final String REQUEST_NOT_ACCEPTED = "REQUEST_NOT_ACCEPTED";

    /**
     * Отказ при запуске сервисной операции: недопустимая пара, объект вне
     * множества входа, невыполненное предусловие, неизвестная
     * идентичность. Все они — негодный ВХОД вызова, и отвечать на них
     * пятисотым значило бы сказать «чини сервер» тому, кто чинить обязан
     * запрос.
     *
     * <p><b>Пояснение — постоянный текст ветви, а не текст исключения</b>
     * (docs/rules/error-handling-policy.md §«Пояснение отказа пишет наша
     * сторона, а не платформа»). Класс платформенный: его бросает и наш код,
     * и разбор перечня каркасом, кладущий в текст полное имя доменного
     * класса, — по классу автора текста обработчик не различает. Текст
     * исключения уходит в лог: там он нужен тому, кто разбирает отказ.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> onIllegalArgument(IllegalArgumentException failure) {
        log.info("Request is refused as invalid input: {}", failure.getMessage());
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Негодный вход вызова");
    }

    /**
     * Сосед по ярусу недоступен: наша сторона исправна, и повтор имеет
     * смысл позже (docs/rules/runtime-error-classification.md §«Отказ
     * соседа по ярусу»).
     *
     * <p><b>Слово класса — общее с прочими поверхностями</b>
     * (docs/rules/error-handling-policy.md §«Класс отказа — значение поля
     * {@code code}, и перечень его закрыт»): периметр пересылает ответ
     * владельца как есть, и второе слово у одного класса доехало бы до
     * браузера рядом с первым.
     */
    @ExceptionHandler(PeerServiceUnavailableException.class)
    public ResponseEntity<ErrorApiResponse> onPeerUnavailable(PeerServiceUnavailableException failure) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PEER_UNAVAILABLE", failure.getMessage());
    }

    /**
     * Нарушенное ограничение параметра вызова, проверенное прокси валидации
     * метода, — тот же отказ контейнера, что и непредъявленный параметр.
     *
     * <p><b>Почему он не доходит до унаследованных обработчиков.</b>
     * Контроллер, помеченный {@code @Validated} классом, встроенную
     * валидацию метода Spring MVC выключает
     * ({@code HandlerMethod#shouldValidateArguments} отдаёт ложь), и
     * ограничение параметра проверяет прокси валидации метода — он бросает
     * {@link ConstraintViolationException}, которого
     * {@link ResponseEntityExceptionHandler} не знает. Без этого обработчика
     * пустое значение обязательного параметра уходило в последний
     * обработчик пятисотым — «чини сервер» на негодный вход вызова.
     *
     * <p><b>Число — то же, что контейнер ставит встроенной валидации
     * параметра</b>, а класс — тот же, что у прочих отказов контейнера
     * (docs/rules/error-handling-policy.md §«Класс отказа — значение поля
     * {@code code}, и перечень его закрыт», строка
     * {@code REQUEST_NOT_ACCEPTED}). Пояснение постоянное: текст исключения
     * несёт имя метода контроллера и уходит в лог.
     *
     * <p><b>Охват назван по коду, а не по замыслу:</b> ограничения валидации
     * в сервисе стоят только на api-моделях и параметрах контроллера, и
     * другого бросающего этот класс на тропе запроса нет. Ограничение,
     * заведённое на сущности, бросало бы его на записи — дефектом нашей
     * стороны, — и этот обработчик назвал бы его отказом входа.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorApiResponse> onConstraintViolation(ConstraintViolationException failure) {
        log.info("Request is refused by method validation: {}", failure.getMessage());
        return response(HttpStatus.BAD_REQUEST, REQUEST_NOT_ACCEPTED, "Нарушено ограничение валидации вызова");
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
        log.error("Unhandled failure on the trading-core surface", failure);
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
        return new ResponseEntity<>(errorBody(REQUEST_NOT_ACCEPTED, detailOf(failure, body)),
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
