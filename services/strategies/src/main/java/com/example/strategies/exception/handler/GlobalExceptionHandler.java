package com.example.strategies.exception.handler;

import static java.util.Objects.isNull;

import com.example.platform.exception.PeerServiceUnavailableException;
import com.example.strategies.exception.PeerReadException;
import com.example.tradingbot.api.model.ErrorApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    /** Пояснение негодного входа: текст ветви, у платформенного исключения своего текста наружу нет. */
    private static final String INVALID_REQUEST_EXPLANATION =
            "Вход вызова не принят: значение не разобрано прикладным кодом";

    /**
     * Отказы валидации и жизненного цикла несут свой статус и свой код.
     *
     * <p>Пояснение здесь — {@code reason}, то есть причина, которую бросающий
     * задал ЯВНО, а не текст исключения: дом разрешает её наружу
     * (docs/rules/error-handling-policy.md §«Пояснение отказа пишет наша
     * сторона, а не платформа»). Подклассы контейнера, названные
     * унаследованным обработчиком поимённо (отказ валидации параметров
     * метода), сюда не доходят: резолвер выбирает ближайший класс.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorApiResponse> onResponseStatus(ResponseStatusException failure) {
        return response(HttpStatus.valueOf(failure.getStatusCode().value()),
                "STRATEGY_REQUEST_REJECTED", failure.getReason());
    }

    /**
     * Негодный вход вызова, распознанный прикладным кодом: неразобранное
     * значение перечня, негодная строка числа либо момента.
     *
     * <p><b>Пояснение — постоянный текст ветви, а не текст исключения.</b>
     * Класс платформенный: тот же {@code IllegalArgumentException} бросает
     * разбор значения вне перечня, кладя в текст полное имя доменного класса,
     * и автора текста обработчик по классу не различает
     * (docs/rules/error-handling-policy.md §«Пояснение отказа пишет наша
     * сторона, а не платформа»). Текст исключения уходит в лог — там его
     * читает держатель.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> onIllegalArgument(IllegalArgumentException failure,
                                                              HttpServletRequest request) {
        log.warn("Invalid request input operation={} {}: {}",
                request.getMethod(), request.getRequestURI(), failure.getMessage());
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", INVALID_REQUEST_EXPLANATION);
    }

    /**
     * Сосед недоступен: операнд не добыт, и создание отвергается, а не проходит непроверенным.
     *
     * <p><b>Отказ соседа оставляет запись в журнале владельца</b>, а не
     * только ответ: вызывающий видит класс, а держатель без записи не
     * видел бы ничего — ни какой сосед, ни на какой операции
     * (docs/rules/error-handling-policy.md §«Внутренняя градация: четыре
     * уровня»). Соседа и его точку несёт сообщение исключения, нашу
     * операцию — запрос.
     */
    @ExceptionHandler(PeerServiceUnavailableException.class)
    public ResponseEntity<ErrorApiResponse> onPeerUnavailable(PeerServiceUnavailableException failure,
                                                              HttpServletRequest request) {
        log.warn("Peer call failed class=PEER_UNAVAILABLE operation={} {}: {}",
                request.getMethod(), request.getRequestURI(), failure.getMessage());
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PEER_UNAVAILABLE", failure.getMessage());
    }

    /**
     * Сосед отказал осознанно либо тропа к нему не настроена: наш дефект,
     * повтором не лечится. Запись в журнале — по тому же доводу, что у
     * недоступности.
     */
    @ExceptionHandler(PeerReadException.class)
    public ResponseEntity<ErrorApiResponse> onPeerRefused(PeerReadException failure, HttpServletRequest request) {
        log.error("Peer call failed class=PEER_REFUSED operation={} {}: {}",
                request.getMethod(), request.getRequestURI(), failure.getMessage());
        return response(HttpStatus.BAD_GATEWAY, "PEER_REFUSED", failure.getMessage());
    }

    /**
     * <b>Отказ по правам сюда не попадает, и это несущее исключение из
     * области последнего обработчика.</b> Ответ на него пишет контур —
     * {@code AccessDenialHandler}, — а он стои́т СНАРУЖИ
     * {@code DispatcherServlet}, в {@code ExceptionTranslationFilter}.
     * Перехваченное последним обработчиком, {@code AccessDeniedException}
     * ушло бы вызывающему кодом {@code 500} вместо {@code 403}, и следа
     * отказа не осталось бы.
     *
     * <p><b>Проброс — рабочая форма, а не обход.</b> Резолвер
     * {@code @ExceptionHandler}, получив из обработчика ТО ЖЕ исключение,
     * отказ не разрешает и возвращает обработку контейнеру — тот
     * пробрасывает исходное исключение дальше по цепочке фильтров.
     *
     * <p><b>Достижимость сегодня нулевая</b>: пер-операционных проверок
     * права нет ни одной (docs/rules/api-access-policy.md), и дефект этой
     * тропы прогоном не обнаружился бы — он ждал бы первой такой проверки.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void onAccessDenied(AccessDeniedException denial) throws AccessDeniedException {
        throw denial;
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
