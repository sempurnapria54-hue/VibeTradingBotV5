package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.platform.exception.PeerServiceUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.web.client.UnknownHttpStatusCodeException;

/**
 * Разбор отказа соседа по ярусу: группы `U7`, `U8`, `U12.1` и клетки
 * `U14.3`, `U14.4` документа
 * `.claude/tests/cases/platform-shared-logic.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии.</b> {@code PeerCall} живёт двумя экземплярами —
 * {@code trading-core} и {@code strategies}, — а сличить их на одном
 * classpath нечем: деревья сервисов друг от друга не зависят. Форма
 * ожидания поэтому общая, а порты ниже подставляют СВОЮ копию, СВОЙ класс
 * «наш дефект» и СВОЙ исходник (§«U12 — Тождество копий формы»).
 *
 * <p><b>Класс недоступности у копий один и тот же</b> — он лежит в общем
 * артефакте, и это видно прямо здесь, в сигнатуре ожидания: подставить
 * свой его копия не может.
 *
 * <p><b>Часть ожиданий взята из дома и кодом сегодня не исполнена.</b>
 * Такие кейсы помечены {@code @Tag("debt")} и в умолчание прогона не
 * входят: их красный прогон есть предъявление названного долга, а не
 * сигнал о сломанном дереве (§«Ожидание берётся из дома, даже когда
 * сегодня оно не исполнено»).
 */
public abstract class PeerCallContract {

    /** Сосед и endpoint кейса: оба уезжают в сообщение отказа. */
    protected static final String PEER = "market-data";
    protected static final String ENDPOINT = "features";

    /** Порт к своей копии хелпера. */
    protected abstract <T> T execute(String peer, String endpoint, Supplier<T> read);

    /** Класс «наш дефект» своего дерева: у копий он разный, и это объявлено. */
    protected abstract Class<? extends RuntimeException> peerReadExceptionType();

    /** Исходник своей копии — вход клеток, наблюдаемых текстом, а не прогоном. */
    protected abstract Path peerCallSource();

    // --- U7: ярусы классов ------------------------------------------------

    @Test
    @DisplayName("U7.1 — значение поставщика возвращается как есть")
    void u7_1_theSuppliedValueIsReturnedAsIs() {
        assertThat(execute(PEER, ENDPOINT, () -> "features-payload")).isEqualTo("features-payload");
    }

    @Test
    @DisplayName("U7.2 — 500 у соседа: недоступность, и причина сохранена")
    void u7_2_aServerErrorIsPeerUnavailability() {
        HttpServerErrorException failure = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(failure)))
                .isInstanceOf(PeerServiceUnavailableException.class)
                .hasMessageContaining(PEER)
                .hasMessageContaining(ENDPOINT)
                .hasMessageContaining("500")
                .hasCause(failure);
    }

    @Test
    @DisplayName("U7.3 — 503: весь диапазон 5xx читается одинаково")
    void u7_3_theWholeServerErrorRangeIsUnavailability() {
        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))))
                .isInstanceOf(PeerServiceUnavailableException.class);
    }

    @Test
    @DisplayName("U7.4 — 400: осознанный отказ соседа есть НАШ дефект")
    void u7_4_aBadRequestIsOurOwnDefect() {
        HttpClientErrorException failure = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(failure)))
                .as("повтором такой отказ не лечится — тропа «пропустить проход» была бы вечной")
                .isInstanceOf(peerReadExceptionType())
                .hasMessageContaining(PEER)
                .hasMessageContaining(ENDPOINT)
                .hasCause(failure);
    }

    @Test
    @DisplayName("U7.5 — 404: тот же класс, что у 400")
    void u7_5_aNotFoundIsTheSameClassAsABadRequest() {
        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))))
                .isInstanceOf(peerReadExceptionType());
    }

    @Test
    @DisplayName("U7.6 — 401: ненастроенная идентичность — наш дефект, а не недоступность соседа")
    void u7_6_aRejectedIdentityIsOurOwnDefect() {
        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))))
                .isInstanceOf(peerReadExceptionType());
    }

    @Test
    @DisplayName("U7.7 — таймаут либо обрыв: недоступность, и статуса в сообщении нет")
    void u7_7_aTransportFailureIsUnavailabilityWithoutAStatus() {
        ResourceAccessException failure = new ResourceAccessException("Read timed out");

        Throwable thrown = catchThrowable(() -> execute(PEER, ENDPOINT, throwing(failure)));

        assertThat(thrown)
                .isInstanceOf(PeerServiceUnavailableException.class)
                .hasMessageContaining(PEER)
                .hasMessageContaining(ENDPOINT)
                .hasCause(failure);
        assertThat(thrown.getMessage())
                .as("статуса у транспортного отказа нет — его нечем было бы заполнить")
                .doesNotContainPattern("\\b\\d{3}\\b");
    }

    @Test
    @DisplayName("U7.8 — неизвестный код 599: развилка идёт по диапазону, а не по перечню")
    void u7_8_anUnknownServerRangeCodeIsUnavailability() {
        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(new UnknownHttpStatusCodeException(
                599, "Unknown", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))))
                .isInstanceOf(PeerServiceUnavailableException.class);
    }

    @Test
    @DisplayName("U7.9 — неизвестный код 299: всё, что не 5xx, читается как наш дефект")
    void u7_9_anUnknownCodeOutsideTheServerRangeIsOurOwnDefect() {
        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(new UnknownHttpStatusCodeException(
                299, "Unknown", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))))
                .isInstanceOf(peerReadExceptionType());
    }

    @Test
    @Tag("debt")
    @DisplayName("U7.10 — неразбираемое тело ответа: ожидание из дома — НАШ дефект (долг F2)")
    void u7_10_anUnreadableBodyIsOurOwnDefect() {
        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(
                new RestClientException("Error while extracting response for type [Features]"))))
                .as("недоступностью дом называет таймаут, обрыв и 5xx; расхождение контракта "
                        + "уходит в тропу «пропустить проход и повторить» и повторяется вечно")
                .isInstanceOf(peerReadExceptionType());
    }

    @Test
    @Tag("debt")
    @DisplayName("U7.11 — неизвестный тип содержимого: тот же долг, что у U7.10")
    void u7_11_anUnknownContentTypeIsOurOwnDefect() {
        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(new UnknownContentTypeException(
                String.class, org.springframework.http.MediaType.TEXT_HTML, HttpStatus.OK.value(), "OK",
                HttpHeaders.EMPTY, new byte[0]))))
                .isInstanceOf(peerReadExceptionType());
    }

    @Test
    @DisplayName("U7.12 — отказ нашего маппера внутри лямбды уходит нетронутым")
    void u7_12_aFailureOutsideTheRestClientFamilyPassesThrough() {
        NullPointerException ours = new NullPointerException("маппер ответа соседа");

        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(ours)))
                .as("иначе наш дефект уехал бы в тропу «сосед недоступен»")
                .isSameAs(ours);
    }

    @Test
    @DisplayName("U7.13 — пустой ответ соседа отказом яруса не является")
    void u7_13_anEmptyPeerAnswerIsNotATierFailure() {
        String empty = execute(PEER, ENDPOINT, () -> null);

        assertThat(empty).isNull();
    }

    // --- U8: сообщение, команды и границы ---------------------------------

    @Test
    @DisplayName("U8.1 — команда с 500: тот же класс, что у чтения")
    void u8_1_aCommandFailsWithTheSameClassAsARead() {
        assertThatThrownBy(() -> execute(PEER, "demand", throwing(HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))))
                .as("метод вызова хелперу не виден по построению: вход — Supplier (звено Z26)")
                .isInstanceOf(PeerServiceUnavailableException.class);
    }

    @Test
    @DisplayName("U8.2 — команда с 4xx: тот же класс, что у чтения")
    void u8_2_aRefusedCommandIsTheSameClassAsARefusedRead() {
        assertThatThrownBy(() -> execute(PEER, "demand", throwing(HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))))
                .isInstanceOf(peerReadExceptionType());
    }

    @Test
    @DisplayName("U8.3 — тело ответа соседа в наше сообщение не попадает")
    void u8_3_thePeerBodyDoesNotLeakIntoOurMessage() {
        String internals = "stacktrace at com.example.marketdata.SecretJob:42";
        HttpServerErrorException failure = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY,
                internals.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> execute(PEER, ENDPOINT, throwing(failure)))
                .hasMessageContaining(PEER)
                .hasMessageContaining(ENDPOINT)
                .hasMessageContaining("500")
                .hasMessageNotContaining(internals)
                .hasCause(failure);
        assertThat(failure.getResponseBodyAsString())
                .as("тело остаётся у причины — наружу она не транслируется")
                .isEqualTo(internals);
    }

    // --- U12.1: тождество копий по ярусу ----------------------------------

    @Test
    @DisplayName("U12.1 — недоступность общим классом, наш дефект — классом своего дерева")
    void u12_1_theUnavailabilityClassIsSharedAndTheDefectClassIsOwn() {
        assertThat(PeerServiceUnavailableException.class.getName())
                .as("класс недоступности лежит одним экземпляром в общем артефакте")
                .isEqualTo("com.example.platform.exception.PeerServiceUnavailableException");
        assertThat(peerReadExceptionType().getSimpleName())
                .as("имя класса «наш дефект» у копий совпадает — различается только его дерево")
                .isEqualTo("PeerReadException");
        assertThat(peerReadExceptionType().getName())
                .as("объявленное различие: класс «наш дефект» живёт в дереве своего сервиса")
                .doesNotStartWith("com.example.platform.");
    }

    // --- U14: чего предмет не делает --------------------------------------

    @Test
    @DisplayName("U14.3 — поставщик спрошен ровно один раз на любом исходе")
    void u14_3_theSupplierIsAskedExactlyOnceOnEveryOutcome() {
        assertThat(callsUntilOutcome(() -> "ok")).isEqualTo(1);
        assertThat(callsUntilOutcome(throwing(HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)))).isEqualTo(1);
        assertThat(callsUntilOutcome(throwing(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)))).isEqualTo(1);
        assertThat(callsUntilOutcome(throwing(new ResourceAccessException("Read timed out")))).isEqualTo(1);
    }

    @Test
    @DisplayName("U14.4 — хелпер ничего не логирует и никуда не ходит")
    void u14_4_theHelperNeitherLogsNorReachesOut() throws IOException {
        String text = Files.readString(existingSource(peerCallSource()), StandardCharsets.UTF_8);

        assertThat(text)
                .as("наблюдаемость отказа соседа — метрика и алерт у окружения, а не строка лога здесь")
                .doesNotContain("Slf4j", "Logger", "log.")
                .as("ни базы, ни брокера, ни повторного вызова у хелпера нет")
                .doesNotContain("Repository", "DataService", "KafkaTemplate", "retry", "Retry");
    }

    // --- оснастка ---------------------------------------------------------

    /** Поставщик, который вместо значения бросает названный отказ. */
    protected static <T> Supplier<T> throwing(RuntimeException failure) {
        return () -> {
            throw failure;
        };
    }

    /** Сколько раз хелпер спросил поставщика, чем бы вызов ни кончился. */
    private int callsUntilOutcome(Supplier<String> outcome) {
        AtomicInteger calls = new AtomicInteger();
        try {
            execute(PEER, ENDPOINT, () -> {
                calls.incrementAndGet();
                return outcome.get();
            });
        } catch (RuntimeException expected) {
            // исход кейса здесь безразличен — наблюдается число обращений
        }
        return calls.get();
    }

    private static Path existingSource(Path source) {
        assertThat(Files.isRegularFile(source))
                .as("исходника %s нет — проба мерила бы пустоту", source)
                .isTrue();
        return source;
    }
}
