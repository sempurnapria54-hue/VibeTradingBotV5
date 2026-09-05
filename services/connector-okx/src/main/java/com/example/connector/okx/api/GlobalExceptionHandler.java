package com.example.connector.okx.api;

import com.example.connector.okx.credentials.CredentialsUnavailableException;
import com.example.connector.okx.integration.CredentialsRejectedException;
import com.example.connector.okx.integration.ExchangeIntegrationException;
import com.example.connector.okx.integration.ExternalInvariantViolationException;
import com.example.connector.okx.integration.ExternalStatusException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.vault.VaultException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

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
 * <p><b>Конкретные HTTP-коды провизорны</b> — этот набор объявлен
 * хвостом пользователя ({@code .claude/rules/codestyle.md} §«Обработка
 * ошибок»): выравнивание кодов по всей платформе идёт одним ходом, а не
 * поэндпоинтно.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Ключей счёта нет в хранилище.
     *
     * <p>{@code 422}, а не {@code 404} и не {@code 500}: запрос понят,
     * счёт назван, но содержание запроса неисполнимо — подписать нечем, и
     * повтор не поможет.
     */
    @ExceptionHandler(CredentialsUnavailableException.class)
    public ResponseEntity<ErrorApiResponse> onCredentialsUnavailable(CredentialsUnavailableException failure) {
        return response(HttpStatus.UNPROCESSABLE_CONTENT, "CREDENTIALS_UNAVAILABLE", failure.getMessage());
    }

    /** Площадка отвергла наши ключи: исходящий отказ доступа, реакция — у ядра. */
    @ExceptionHandler(CredentialsRejectedException.class)
    public ResponseEntity<ErrorApiResponse> onCredentialsRejected(CredentialsRejectedException failure) {
        return response(HttpStatus.BAD_GATEWAY, "EXCHANGE_CREDENTIALS_REJECTED", failure.getMessage());
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
                .code("EXTERNAL_STATUS")
                .reason(failure.getReasonCode().name())
                .message(failure.getMessage())
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    /** Ответ получен, но нарушает инвариант, на котором стои́т наша торговля. */
    @ExceptionHandler(ExternalInvariantViolationException.class)
    public ResponseEntity<ErrorApiResponse> onInvariantViolation(ExternalInvariantViolationException failure) {
        return response(HttpStatus.BAD_GATEWAY, "EXTERNAL_INVARIANT_VIOLATION", failure.getMessage());
    }

    /** Ошибка API площадки, разбора ответа либо транспорта: ретраится ядром. */
    @ExceptionHandler(ExchangeIntegrationException.class)
    public ResponseEntity<ErrorApiResponse> onIntegrationFailure(ExchangeIntegrationException failure) {
        return response(HttpStatus.BAD_GATEWAY, "EXCHANGE_ERROR", failure.getMessage());
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
        return response(HttpStatus.BAD_GATEWAY, "EXCHANGE_UNREACHABLE", failure.getMessage());
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
        return response(HttpStatus.SERVICE_UNAVAILABLE, "SECRET_STORE_UNAVAILABLE", failure.getMessage());
    }

    /** Негодный вход: неразобранное значение перечня, отсутствующий обязательный операнд. */
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
