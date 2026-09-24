package com.example.connector.okx.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import com.example.tradingbot.domain.exchange.ExchangeFailureClass;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Классы отказа границы — группа {@code B7} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Мерится КЛАСС отказа, а не HTTP-число.</b> Ветвится ядро по полю
 * {@code code} единого error-DTO, а конкретные коды дом объявляет
 * провизорными ({@code docs/components/IntegrationService.md} §«Классы
 * отказа на границе — дом здесь»); пиньнутое число сделало бы кейс
 * красным на исправной системе ровно тем ходом, которым дом и обещает
 * коды выровнять.
 *
 * <p><b>Опознание отказа в кредах идёт по КОДУ, а не по статусу.</b> У
 * всего семейства статус один, и по нему наш собственный дефект сборки
 * запроса неотличим от отвергнутых ключей — а реакции у них
 * противоположные: первое лечится нашей стороной, второе поднимает
 * биржевую ступень.
 */
class FailureClassesBoxTest extends SharedConnectorBox {

    /** Коды, каждый из которых площадка отдаёт на отвергнутых ключах. */
    private static final List<String> CREDENTIALS_REJECTED_CODES =
            List.of("50101", "50105", "50111", "50113", "50119");

    /** Коды того же семейства, означающие НАШ дефект сборки запроса. */
    private static final List<String> OWN_DEFECT_CODES = List.of("50102", "50103");

    private static final String POSITIONS = "/positions";

    @Test
    @DisplayName("B7.1 — каждый код отказа в кредах опознаётся как отказ кредов")
    void b7_1_everyCredentialsRejectionCodeIsRecognised() {
        CREDENTIALS_REJECTED_CODES.forEach(code -> {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH,
                    Okx.failure(code, "credentials rejected"));

            Answer answer = get(account(POSITIONS));

            assertThat(answer.errorCode())
                    .as("код площадки %s", code)
                    .isEqualTo("EXCHANGE_CREDENTIALS_REJECTED");
            assertThat(exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH)).hasSize(1);
        });
    }

    @Test
    @DisplayName("B7.2 — код нашего собственного дефекта отказом кредов не считается")
    void b7_2_ourOwnDefectCodeIsNotACredentialsRejection() {
        OWN_DEFECT_CODES.forEach(code -> {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.failure(code, "our own defect"));

            Answer answer = get(account(POSITIONS));

            assertThat(answer.errorCode()).as("код площадки %s", code).isEqualTo("EXCHANGE_ERROR");
        });
    }

    @Test
    @DisplayName("B7.3 — код вне обоих перечней идёт общей тропой")
    void b7_3_aCodeOutsideBothListsTakesTheCommonPath() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.failure("50199", "неизвестный код"));

        Answer answer = get(account(POSITIONS));

        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_ERROR");
    }

    @Test
    @DisplayName("B7.4 — ответ без поля кода не роняет разбор")
    void b7_4_anAnswerWithoutACodeFieldDoesNotBreakParsing() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.envelopeWithoutCode());

        Answer answer = get(account(POSITIONS));

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_ERROR");
    }

    /**
     * Ожидание взято из дома: «площадка не ответила» и «площадка ответила
     * и объяснила отказ» — разные классы, и повтор осмыслен только у
     * первого.
     */
    @Test
    @DisplayName("B7.5 — площадка не ответила")
    void b7_5_theExchangeDidNotAnswer() {
        exchange.breaks(OkxConstants.INSTRUMENTS_PATH);
        exchange.breaks(OkxConstants.ACCOUNT_POSITIONS_PATH);

        Answer publicRead = get(market("/instruments?externalInstrumentType=SWAP"));
        Answer privateRead = get(account(POSITIONS));

        assertThat(publicRead.carriesErrorDto()).isTrue();
        assertThat(privateRead.carriesErrorDto()).isTrue();
        assertThat(publicRead.errorCode()).isEqualTo("EXCHANGE_UNREACHABLE");
        assertThat(privateRead.errorCode()).isEqualTo("EXCHANGE_UNREACHABLE");
    }

    /** Тело, не читаемое конвертом, — ответа нет, как у {@code B7.5}. */
    @Test
    @DisplayName("B7.6 — неразбираемое тело ответа — тот же класс, что недостижимость")
    void b7_6_anUnparseableBodyIsTheSameClassAsUnreachability() {
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, "{\"code\":\"0\",\"data\":[{\"instId\":");

        Answer answer = get(market("/instruments?externalInstrumentType=SWAP"));

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_UNREACHABLE");
    }

    @Test
    @DisplayName("B7.7 — ошибочный HTTP-статус с телом-конвертом читается по коду тела")
    void b7_7_anErrorHttpStatusWithAnEnvelopeBodyIsReadByTheBodyCode() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, 400,
                Okx.failure("51000", "Parameter instType error"));

        Answer answer = get(account(POSITIONS));

        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_ERROR");
        assertThat(String.valueOf(answer.asObject().get("message")))
                .contains("51000", "Parameter instType error");
    }

    /**
     * Ожидание взято из дома: состав перечисления обязан совпадать с
     * таблицей классов, и каждое значение обязано быть предъявлено.
     *
     * <p><b>Шестое значение предъявляет не этот класс:</b>
     * {@code SECRET_STORE_UNAVAILABLE} требует мёртвого хранилища, то есть
     * другого положения осей контекста, и живёт клеткой {@code B1.7} со
     * своим контекстом. Здесь оно из ожидания вычтено поимённо, а не
     * забыто.
     */
    @Test
    @DisplayName("B7.8 — перечень классов отказа закрыт и совпадает с домом")
    void b7_8_theFailureClassListIsClosedAndMatchesItsHome() {
        Set<String> observed = new LinkedHashSet<>();
        observed.add(codeOfAbsentCredentials());
        observed.add(codeOfRejectedCredentials());
        observed.add(codeOfExchangeError());
        observed.add(codeOfUnknownStatus());
        observed.add(codeOfInvariantViolation());
        observed.add(codeOfUnreachableExchange());

        List<String> declared = Arrays.stream(ExchangeFailureClass.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        List<String> expected = declared.stream()
                .filter(value -> isFalse(ExchangeFailureClass.SECRET_STORE_UNAVAILABLE.name().equals(value)))
                .collect(Collectors.toList());

        assertThat(observed).doesNotContain("INVALID_REQUEST");
        assertThat(declared).containsAll(observed);
        assertThat(observed).containsExactlyInAnyOrderElementsOf(expected);
    }

    private String codeOfAbsentCredentials() {
        String account = "ACC-B7-8";
        secrets.remove(account);
        exchange.reset();
        return get(account(account, POSITIONS)).errorCode();
    }

    private String codeOfRejectedCredentials() {
        exchange.reset();
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.failure("50113", "signature"));
        return get(account(POSITIONS)).errorCode();
    }

    private String codeOfExchangeError() {
        exchange.reset();
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.failure("51000", "parameter"));
        return get(account(POSITIONS)).errorCode();
    }

    private String codeOfUnknownStatus() {
        exchange.reset();
        exchange.answers(OkxConstants.TRADE_ORDERS_PENDING_PATH,
                Okx.ok(Okx.order(INSTRUMENT, "ord-1", "vtb-1", "super_filled").text()));
        return get(account("/orders/pending/instrument?externalInstrumentId=" + INSTRUMENT)).errorCode();
    }

    private String codeOfInvariantViolation() {
        exchange.reset();
        exchange.answers(OkxConstants.PUBLIC_TIME_PATH, Okx.ok());
        return get(market("/time")).errorCode();
    }

    private String codeOfUnreachableExchange() {
        exchange.reset();
        exchange.breaks(OkxConstants.PUBLIC_TIME_PATH);
        return get(market("/time")).errorCode();
    }
}
