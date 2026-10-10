package com.example.connector.okx.integration.external.api.client;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.connector.okx.integration.external.api.model.okx.response.OkxApiResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.ServerTimeOkxResponse;
import com.example.connector.okx.util.OkxConstants;
import com.example.connector.okx.util.OkxParse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Часы площадки на стороне коннектора: момент, которым подписывается
 * приватный запрос.
 *
 * <p><b>Подпись берёт момент площадки, а не часы хоста.</b> Метку, разошедшуюся
 * с часами источника больше чем на 30 секунд, площадка отвергает кодом
 * {@code 50102}; часы хоста при этом расходятся с ней штатно — виртуальная
 * машина, проспавшая сон хоста, отстаёт на минуты. Момент площадки — часы
 * процесса плюс смещение, измеренное эндпоинтом её серверного времени
 * (docs/integrations/okx/contracts/server-time.md — дом механизма).
 *
 * <p><b>Смещение мерится по отказу, а не по расписанию.</b> До первого отказа
 * оно нулевое; перемер зовёт граница отправки, получив {@code 50102}
 * ({@link OkxRestClient#dispatch}). Тика синхронизации нет: дрейф наблюдается
 * только отказом, и мерить его без отказа значило бы тратить лимит эндпоинта
 * впустую.
 *
 * <p><b>Одно смещение на процесс, и это не нарушение стейтлесса.</b> Часы хоста
 * у всех счетов общие, и смещение — свойство процесса, а не счёта; истиной о
 * площадке оно не является и переживает вызов ровно как кэш ключей — как
 * ускорение, которое отказ площадки поправит.
 *
 * <p><b>Часы процесса — операнд, а не {@code Instant.now()}</b>: смещение есть
 * разность двух моментов, и проверяется оно только при часах, которые не
 * сдвигаются между чтениями.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OkxServerClock {

    private static final ParameterizedTypeReference<OkxApiResponse<ServerTimeOkxResponse>> SERVER_TIME_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /** Публичный клиент площадки: время источника ключей не требует. */
    private final RestClient okxRestClientHttp;

    /** Часы процесса. */
    private final Clock processClock;

    /** Смещение часов площадки от часов процесса; нулевое до первого замера. */
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    /** Момент площадки: часы процесса плюс последнее измеренное смещение. */
    public Instant now() {
        return processClock.instant().plus(offset.get());
    }

    /**
     * Перемерить смещение по серверному времени площадки.
     *
     * <p>Смещение — серверное время минус середина собственного запроса: половина
     * круга сети отнесена к каждой стороне. <b>Неудачный замер прежнего смещения
     * не трогает</b> — подменить его нулём значило бы вернуть подпись к часам
     * хоста, которые и отвергнуты.
     *
     * @return {@code true}, если смещение перемерено; {@code false}, если
     *         серверного времени добыть не удалось
     */
    public Boolean resync() {
        Instant sentAt = processClock.instant();
        Instant serverTime = readServerTime();
        if (isNull(serverTime)) {
            return false;
        }
        Instant receivedAt = processClock.instant();
        Instant localMidpoint = sentAt.plus(Duration.between(sentAt, receivedAt).dividedBy(2));
        Duration measured = Duration.between(localMidpoint, serverTime);
        Duration previous = offset.getAndSet(measured);
        log.warn("OKX clock offset re-measured [public-time] previous={} measured={}", previous, measured);
        return true;
    }

    /**
     * Серверное время площадки; пусто — его не добыть: источник молчит, ответил
     * отказом, без записи либо меткой, которая не разбирается числом.
     */
    private Instant readServerTime() {
        try {
            OkxApiResponse<ServerTimeOkxResponse> response = okxRestClientHttp.get()
                    .uri(OkxConstants.PUBLIC_TIME_PATH)
                    .retrieve()
                    .body(SERVER_TIME_TYPE);
            if (isNull(response) || isFalse(Objects.equals(OkxConstants.SUCCESS_CODE, response.getCode()))
                    || isEmpty(response.getData()) || isNull(response.getData().getFirst())) {
                log.warn("OKX clock offset not re-measured [public-time]: no server time in the answer code={}",
                        isNull(response) ? null : response.getCode());
                return null;
            }
            return OkxParse.instant(response.getData().getFirst().getTs());
        } catch (RestClientException | NumberFormatException e) {
            log.warn("OKX clock offset not re-measured [public-time]: {}", e.getMessage());
            return null;
        }
    }
}
