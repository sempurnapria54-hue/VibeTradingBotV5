package com.example.auditstatistics.api;

import static java.util.Objects.isNull;

import com.example.auditstatistics.domain.service.ReadQueryRejectedException;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Единая внешняя поверхность ошибок: один {@code @RestControllerAdvice},
 * один error-DTO (docs/rules/error-handling-policy.md; конвенция —
 * .claude/rules/codestyle.md §«Обработка ошибок»).
 *
 * <p><b>Родов отказа здесь три, и приходят они из разных мест.</b>
 * Неизвестный путь и неподдержанный метод собирает контейнер, всё
 * непредусмотренное ловит последний обработчик, а <b>отказ собственной
 * операции</b> — отвергнутый вопрос выборки чтения — приезжает от
 * прикладного кода. Третий род приехал с первой выборкой чтения, как и
 * было названо при заведении носителя, и <b>второй выборкой он не
 * удвоился</b>: класс отказа у обеих один, потому что один и ответ
 * (.claude/rules/policy-home.md). Первые два наблюдались у соседей
 * отвечающими <b>пустым телом</b> (.claude/work/backlog.md §«Единый
 * error-DTO у поверхностей соседних сервисов»), и клейм «единый
 * error-DTO» без них не держится.
 *
 * <p>Статусы контейнера наследуются, а не переписываются: они уже
 * написаны в {@link ResponseEntityExceptionHandler}, и второй их носитель
 * разошёлся бы с первым.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Вопрос читателя не принят — любой из двух выборок чтения
     * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»;
     * docs/rules/statistics-aggregates.md §«Что это за числа и кто их
     * читает»).
     *
     * <p><b>Текст отказа НАЗЫВАЕТ повод, и это не противоречит запрету на
     * рассказ о внутреннем устройстве.</b> Предмет здесь — вопрос
     * вызывающего, а не наше устройство: не сказав, чем вопрос не принят,
     * поверхность оставила бы читателя перебирать поводы
     * (docs/rules/error-handling-policy.md §«Что отказ НЕ сообщает»).
     * Перечня поводов здесь нет намеренно — он живёт у выборок, и копия
     * старела бы первой.
     */
    @ExceptionHandler(ReadQueryRejectedException.class)
    public ResponseEntity<ErrorApiResponse> onQueryRejected(ReadQueryRejectedException rejection) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("QUERY_NOT_ACCEPTED", rejection.getMessage()));
    }

    /**
     * <b>Отказ по правам сюда не попадает, и это несущее исключение из
     * области последнего обработчика.</b> Ответ на него пишет контур —
     * {@code AccessDenialHandler}, — а он стои́т СНАРУЖИ
     * {@code DispatcherServlet}, в {@code ExceptionTranslationFilter}.
     * Перехваченное здесь, {@code AccessDeniedException} ушло бы
     * вызывающему кодом {@code 500} вместо {@code 403}, строки отказа не
     * завелось бы, и в журнале следа не осталось бы: контракт объявлен,
     * обработчик написан, и не работает ни один.
     *
     * <p><b>Проброс — рабочая форма, а не обход.</b> Резолвер
     * {@code @ExceptionHandler}, получив из обработчика ТО ЖЕ исключение,
     * отказ не разрешает и возвращает обработку контейнеру — тот
     * пробрасывает исходное исключение дальше по цепочке фильтров.
     *
     * <p><b>Достижимость сегодня нулевая, и именно поэтому пассаж
     * нужен:</b> пер-операционных проверок права нет ни одной, и дефект
     * этой тропы не обнаружился бы прогоном — он ждал бы первой такой
     * проверки (docs/rules/api-access-policy.md).
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
        log.error("Unhandled failure on the audit-statistics surface", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("INTERNAL_FAILURE", "Внутренний отказ сервиса"));
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

    private ErrorApiResponse errorBody(String code, String message) {
        return ErrorApiResponse.builder()
                .code(code)
                .message(isNull(message) ? "Запрос отвергнут" : message)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
