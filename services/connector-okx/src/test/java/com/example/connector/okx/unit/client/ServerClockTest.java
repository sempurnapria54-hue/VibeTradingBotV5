package com.example.connector.okx.unit.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.integration.external.api.client.OkxServerClock;
import com.example.connector.okx.integration.external.api.client.OkxSigningInterceptor;
import com.example.connector.okx.util.OkxConstants;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/**
 * Часы площадки: смещение, его перемер и метка подписи — группа {@code U2}
 * документа `.claude/tests/cases/connector-okx.md`
 * (docs/integrations/okx/contracts/server-time.md — дом механизма).
 *
 * <p><b>Базовая сборка:</b> часы площадки собраны конструктором на
 * публичный клиент, привязанный к {@code MockRestServiceServer}, и на часы
 * процесса, которые тест ведёт сам — неподвижные либо шагающие заданной
 * последовательностью. Смещение есть разность моментов, и мерить его на
 * часах, сдвигающихся между чтениями сами, значило бы мерить скорость теста.
 *
 * <p><b>Клиент площадки — ввод-вывод предмета</b>, и подменён он на уровне
 * транспорта, а не моком: разбор конверта серверного времени остаётся
 * настоящим.
 */
class ServerClockTest {

    private static final String BASE = "http://okx.test";

    private static final Instant PROCESS_MOMENT = Instant.parse("2026-10-10T03:00:00Z");

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    @DisplayName("U2.1 — до первого замера момент площадки — часы процесса")
    void u2_1_beforeTheFirstMeasurementThePlatformMomentIsTheProcessClock() {
        OkxServerClock clock = new OkxServerClock(builder.build(), fixed(PROCESS_MOMENT));

        assertThat(clock.now()).isEqualTo(PROCESS_MOMENT);
        server.verify();
    }

    /**
     * Запрос ушёл в {@code t0}, ответ пришёл в {@code t0 + 2s}: середина —
     * {@code t0 + 1s}, и серверное время {@code t0 + 91s} даёт смещение
     * {@code +90s}. Момент, спрошенный позже ({@code t0 + 10s}), несёт
     * смещение целиком.
     */
    @Test
    @DisplayName("U2.2 — замер ставит смещение «серверное время минус середина запроса»")
    void u2_2_theMeasurementSetsTheOffsetFromTheRequestMidpoint() {
        OkxServerClock clock = new OkxServerClock(builder.build(), stepping(
                PROCESS_MOMENT, PROCESS_MOMENT.plusSeconds(2), PROCESS_MOMENT.plusSeconds(10)));
        answersServerTime(PROCESS_MOMENT.plusSeconds(91));

        Boolean measured = clock.resync();

        assertThat(measured).as("U2.2: замер состоялся").isTrue();
        assertThat(clock.now()).as("U2.2: момент площадки").isEqualTo(PROCESS_MOMENT.plusSeconds(100));
        server.verify();
    }

    /**
     * Второй замер ЗАМЕНЯЕТ смещение, а не прибавляется к первому; знак у
     * смещения любой — хост бывает и впереди площадки.
     */
    @Test
    @DisplayName("U2.3 — повторный замер заменяет смещение, и знак у него любой")
    void u2_3_aRepeatedMeasurementReplacesTheOffsetWhateverItsSign() {
        OkxServerClock clock = new OkxServerClock(builder.build(), fixed(PROCESS_MOMENT));
        answersServerTime(PROCESS_MOMENT.plusSeconds(90));
        answersServerTime(PROCESS_MOMENT.minusSeconds(45));

        clock.resync();
        Boolean remeasured = clock.resync();

        assertThat(remeasured).isTrue();
        assertThat(clock.now()).as("U2.3: смещение второго замера, не сумма")
                .isEqualTo(PROCESS_MOMENT.minusSeconds(45));
        server.verify();
    }

    /**
     * Неудачный замер прежнего смещения не трогает: подменить его нулём
     * значило бы вернуть подпись к часам хоста, которые и отвергнуты.
     */
    @ParameterizedTest(name = "U2.4 — {0}")
    @ValueSource(strings = {"refusal-code", "no-record", "blank-ts", "non-numeric-ts", "silence"})
    @DisplayName("U2.4 — неудачный замер прежнего смещения не трогает и сообщает о неудаче")
    void u2_4_aFailedMeasurementKeepsThePreviousOffset(String failure) {
        OkxServerClock clock = new OkxServerClock(builder.build(), fixed(PROCESS_MOMENT));
        answersServerTime(PROCESS_MOMENT.plusSeconds(90));
        server.expect(requestTo(BASE + OkxConstants.PUBLIC_TIME_PATH)).andRespond(failedAnswer(failure));
        clock.resync();

        Boolean measured = clock.resync();

        assertThat(measured).as("U2.4 %s: замер не состоялся", failure).isFalse();
        assertThat(clock.now()).as("U2.4 %s: прежнее смещение", failure)
                .isEqualTo(PROCESS_MOMENT.plusSeconds(90));
        server.verify();
    }

    /**
     * Метка подписи — момент площадки, а не часы хоста; подпись считается от
     * той же метки, что стои́т в заголовке.
     */
    @Test
    @DisplayName("U2.5 — метка подписи — момент площадки")
    void u2_5_theSignatureTimestampIsThePlatformMoment() throws Exception {
        OkxServerClock clock = new OkxServerClock(builder.build(), fixed(PROCESS_MOMENT));
        answersServerTime(PROCESS_MOMENT.plusSeconds(90));
        clock.resync();
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("https://example.test/api/v5/account/positions"));

        new OkxSigningInterceptor(new ExchangeCredentials("key-1", "secret-1", "pass-1",
                ExchangeAccount.Contour.LIVE), clock)
                .intercept(request, new byte[0], (executed, body) -> new MockClientHttpResponse(new byte[0],
                        HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(OkxConstants.ACCESS_TIMESTAMP_HEADER))
                .isEqualTo(TIMESTAMP_FORMAT.format(PROCESS_MOMENT.plusSeconds(90)));
    }

    private void answersServerTime(Instant serverTime) {
        server.expect(requestTo(BASE + OkxConstants.PUBLIC_TIME_PATH))
                .andRespond(withSuccess("{\"code\":\"0\",\"msg\":\"\",\"data\":[{\"ts\":\""
                        + serverTime.toEpochMilli() + "\"}]}", MediaType.APPLICATION_JSON));
    }

    private static ResponseCreator failedAnswer(String failure) {
        return switch (failure) {
            case "refusal-code" -> withSuccess("{\"code\":\"50011\",\"msg\":\"Too Many Requests\",\"data\":[]}",
                    MediaType.APPLICATION_JSON);
            case "no-record" -> withSuccess("{\"code\":\"0\",\"msg\":\"\",\"data\":[]}", MediaType.APPLICATION_JSON);
            case "blank-ts" -> withSuccess("{\"code\":\"0\",\"msg\":\"\",\"data\":[{\"ts\":\"\"}]}",
                    MediaType.APPLICATION_JSON);
            case "non-numeric-ts" -> withSuccess("{\"code\":\"0\",\"msg\":\"\",\"data\":[{\"ts\":\"soon\"}]}",
                    MediaType.APPLICATION_JSON);
            case "silence" -> withException(new IOException("connection reset"));
            default -> throw new IllegalArgumentException("Неизвестный вариант отказа: " + failure);
        };
    }

    private static Clock fixed(Instant moment) {
        return Clock.fixed(moment, ZoneOffset.UTC);
    }

    private static Clock stepping(Instant... moments) {
        return new SteppingClock(new ArrayDeque<>(List.of(moments)));
    }

    /**
     * Часы, отдающие заданные моменты по очереди; последний повторяется. Ими
     * разводятся три чтения часов процесса — отправка, приём, спрошенный
     * момент, — и середина запроса становится проверяемой.
     */
    private static final class SteppingClock extends Clock {

        private final Deque<Instant> moments;

        private SteppingClock(Deque<Instant> moments) {
            this.moments = moments;
        }

        @Override
        public Instant instant() {
            return moments.size() > 1 ? moments.pollFirst() : moments.peekFirst();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
