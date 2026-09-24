package com.example.connector.okx.exception.handler;

import static java.util.Objects.isNull;

import com.example.connector.okx.exception.CredentialsRejectedException;
import com.example.connector.okx.exception.CredentialsUnavailableException;
import com.example.connector.okx.exception.ExchangeIntegrationException;
import com.example.connector.okx.exception.ExternalInvariantViolationException;
import com.example.connector.okx.exception.ExternalStatusException;
import com.example.tradingbot.api.model.ErrorApiResponse;
import com.example.tradingbot.domain.exchange.ExchangeFailureClass;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.VaultException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Единая внешняя поверхность ошибок коннектора: один
 * {@code @RestControllerAdvice}, один error-DTO
 * ({@code .claude/rules/codestyle.md} §«Обработка ошибок»).
 *
 * <p><b>Отказ хранилища и отказ площадки разведены, и это несущее.</b>
 * «Ключей нет» означает, что счёт не снабжён ключами, и повтор этого не
 * лечит; «площадка отвергла ключи» — исходящий отказ доступа со своей
 * лестницей реакций ({@code docs/rules/exchange-hold.md}). Слитые в один
 * код, они дали бы ядру одну реакцию на две несравнимые причины.
 *
 * <p><b>Контролируемые отказы переезжают классом.</b> Каждый получает
 * свой {@code code}: реакцию на него выбирает ядро, и подменить класс
 * общим «ошибка интеграции» значило бы решить за ядро
 * ({@code docs/rules/controlled-exchange-exceptions.md}).
 *
 * <p><b>Значение класса берётся из общего перечня</b>
 * ({@link ExchangeFailureClass}), а не из литерала: производит класс
 * коннектор, потребляет ядро, и разойдись копии — ядро молча не узнало бы
 * класс и выбрало бы не ту реакцию. Форма ответа при этом остаётся своей
 * у каждой стороны: api-модель принадлежит тому, кто её отдаёт.
 *
 * <p><b>Негодный вход из перечня выпадает намеренно.</b> {@code 400} тут
 * означает наш собственный дефект сборки запроса, а не отказ ПЛОЩАДКИ; в
 * таблице классов границы его нет, и значения в общем перечне ему тоже не
 * заводится — нераспознанный класс вызывающий и так обязан трактовать как
 * свой дефект.
 *
 * <p><b>Перечень классов закрыт с обеих сторон.</b> Свой формат получают
 * и отказы, которых обработчики не называют поимённо: отказы самого
 * контейнера (неразбираемое тело, непредъявленный параметр, неподдержанный
 * метод) наследуются из {@link ResponseEntityExceptionHandler} со своими
 * статусами, а подменяется только тело; всё непредусмотренное ловит
 * последний обработчик. Без них такие отказы отвечали пустым телом —
 * вторым форматом ({@code docs/rules/error-handling-policy.md}
 * §«Внешняя поверхность»).
 *
 * <p><b>Конкретные HTTP-коды провизорны</b> — этот набор объявлен
 * хвостом пользователя ({@code .claude/rules/codestyle.md} §«Обработка
 * ошибок»): выравнивание кодов по всей платформе идёт одним ходом, а не
 * поэндпоинтно.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Ключей счёта нет в хранилище.
     *
     * <p>{@code 422}, а не {@code 404} и не {@code 500}: запрос понят,
     * счёт назван, но содержание запроса неисполнимо — подписать нечем, и
     * повтор не поможет.
     */
    @ExceptionHandler(CredentialsUnavailableException.class)
    public ResponseEntity<ErrorApiResponse> onCredentialsUnavailable(CredentialsUnavailableException failure) {
        return response(HttpStatus.UNPROCESSABLE_CONTENT, ExchangeFailureClass.CREDENTIALS_UNAVAILABLE.name(), failure.getMessage());
    }

    /** Площадка отвергла наши ключи: исходящий отказ доступа, реакция — у ядра. */
    @ExceptionHandler(CredentialsRejectedException.class)
    public ResponseEntity<ErrorApiResponse> onCredentialsRejected(CredentialsRejectedException failure) {
        return response(HttpStatus.BAD_GATEWAY, ExchangeFailureClass.EXCHANGE_CREDENTIALS_REJECTED.name(), failure.getMessage());
    }

    /**
     * Внешний статус получен, но неизвестен либо означает проблемное
     * состояние.
     *
     * <p><b>Причина едет отдельным полем, а не внутри текста.</b> Ядро
     * ставит её причиной закрытия сущности
     * ({@code docs/rules/controlled-exchange-exceptions.md}: {@code closeReason
     * = reasonCode}); оставь её только в сообщении для человека — и ядру
     * пришлось бы разбирать текст, чтобы назначить исход сделке.
     */
    @ExceptionHandler(ExternalStatusException.class)
    public ResponseEntity<ErrorApiResponse> onExternalStatus(ExternalStatusException failure) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorApiResponse.builder()
                .code(ExchangeFailureClass.EXTERNAL_STATUS.name())
                .reason(failure.getReasonCode().name())
                .message(failure.getMessage())
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    /** Ответ получен, но нарушает инвариант, на котором стои́т наша торговля. */
    @ExceptionHandler(ExternalInvariantViolationException.class)
    public ResponseEntity<ErrorApiResponse> onInvariantViolation(ExternalInvariantViolationException failure) {
        return response(HttpStatus.BAD_GATEWAY, ExchangeFailureClass.EXTERNAL_INVARIANT_VIOLATION.name(), failure.getMessage());
    }

    /** Ошибка API площадки, разбора ответа либо транспорта: ретраится ядром. */
    @ExceptionHandler(ExchangeIntegrationException.class)
    public ResponseEntity<ErrorApiResponse> onIntegrationFailure(ExchangeIntegrationException failure) {
        return response(HttpStatus.BAD_GATEWAY, ExchangeFailureClass.EXCHANGE_ERROR.name(), failure.getMessage());
    }

    /**
     * Площадка недостижима: соединение не установилось, оборвалось либо
     * ответ не разобрался.
     *
     * <p><b>Названо отдельно от ошибки API.</b> Ошибка API означает, что
     * площадка ответила и объяснила отказ; здесь ответа нет вовсе, и
     * повтор осмыслен ровно потому, что причина преходящая. Без этой
     * ветки транспортный сбой уходил бы наружу голым {@code 500} без
     * поля причины — то есть мимо единого error-DTO, который ядро и
     * читает, чтобы выбрать реакцию.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorApiResponse> onTransportFailure(RestClientException failure) {
        return response(HttpStatus.BAD_GATEWAY, ExchangeFailureClass.EXCHANGE_UNREACHABLE.name(), failure.getMessage());
    }

    /**
     * Хранилище секретов недоступно.
     *
     * <p><b>Это не «ключей нет».</b> Там ответ получен и говорит, что
     * ключей не заводили — повтор не поможет. Здесь ответа нет, и повтор
     * поможет, как только хранилище вернётся; отсюда {@code 503}, а не
     * {@code 422}. Разница видна ядру по коду, и она определяет, ретраить
     * или поднимать ступень.
     *
     * <p>Кэш ключей делает эту ветку редкой: недоступность хранилища
     * останавливает торговлю не мгновенно, а по истечении срока кэша.
     */
    @ExceptionHandler(VaultException.class)
    public ResponseEntity<ErrorApiResponse> onSecretStoreUnavailable(VaultException failure) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, ExchangeFailureClass.SECRET_STORE_UNAVAILABLE.name(), failure.getMessage());
    }

    /**
     * Негодный вход ВЫЗЫВАЮЩЕГО: неразобранное значение перечня,
     * отсутствующий обязательный операнд.
     *
     * <p><b>Чужое содержимое сюда не доходит.</b> Ответ площадки,
     * не разобранный формой контракта, переводит в нарушение инварианта
     * сеть разбора шлюза, а испорченное содержимое хранилища — резолвер
     * ключей в «ключей нет»: оба — классы границы, и ядро выбирает по ним
     * реакцию, а не считает отказ своим дефектом.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorApiResponse> onIllegalArgument(IllegalArgumentException failure) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", failure.getMessage());
    }

    /**
     * Всё непредусмотренное — наш дефект, а не вход вызывающего и не отказ
     * площадки.
     *
     * <p><b>Текст исключения наружу не идёт</b>
     * ({@code docs/rules/error-handling-policy.md} §«Что отказ НЕ
     * сообщает»); в лог он идёт целиком.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApiResponse> onUnexpected(Exception failure) {
        log.error("Unhandled failure on the connector surface", failure);
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
