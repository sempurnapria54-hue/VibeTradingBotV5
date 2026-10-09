package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.smokelive.Perimeter.Reply;
import com.example.tests.e2e.smokelive.SmokeRun.JournalRead;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.smokelive.SmokeRun.decimal;
import static com.example.tests.e2e.smokelive.SmokeRun.require;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E5} дыма: заявка исполнилась и позиция закрыта
 * (.claude/tests/cases/smoke-live.md §«E5 — Заявка исполнилась и позиция
 * закрыта»). Здесь дым двигает деньги демо-счёта и возвращает его сам.
 *
 * <p><b>Не ассертится — наружу не наблюдается.</b> Зеркало заявки ядра
 * (наполненный размер, средняя цена, биржевой идентификатор защиты) поверхность
 * сделки не отдаёт; порядок «сначала снята защита, потом закрыта позиция»
 * ({@code E5.3}) снаружи не виден — видно только, что по окончании нет ни
 * того, ни другого; «команды ушли по одной» — {@code G6}. Средних цен входа и
 * выхода ({@code E5.4}) не отдаёт ни поверхность сделки, ни строка журнала о
 * закрытии: их половина ожидания кодом не покрыта. Издержки {@code E5.4}
 * читаются содержимым строки журнала о закрытии сделки.
 */
@Tag("smoke")
@DisplayName("E5 — Заявка исполнилась и позиция закрыта")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E5OrderFilledSmokeTest {

    private static SmokeRun run;

    @BeforeAll
    static void requirePreconditions() {
        run = SmokeRun.get();
        run.requireCommonPreconditions();
    }

    @Test
    @Order(1)
    @DisplayName("E5.1 — Рыночный вход минимального лота исполняется, наполнение доезжает до зеркала")
    void e5_1_aMarketEntryOfTheMinimalLotFills() {
        require(nonNull(run.restingDeal()) && SmokeRun.TERMINAL_DEAL.contains(
                run.deal(run.restingDeal()).path("status").asString("")), "состояние после E4.5 не достигнуто");
        Reply retired = run.deactivateDefinition(run.restingDefinition());
        require(retired.status() == 200, "прежнее определение не переведено в неактивный статус: " + retired);
        Reply cleared = run.pairHaltClearance();
        require(cleared.status() == 204, "полная ступень пары не снята обратным ходом: " + cleared);
        require(run.okx().openPositions(run.externalInstrument()).isEmpty(), "до входа на счёте есть позиция");

        run.marketDefinition(run.activateDefinition(SmokeDefinition.market(run.account(), run.instrument())));
        run.driveUntil("сделка рыночного входа заведена сканером", run.exchangeTimeout(), () -> {
            run.perimeter().post("/api/v1/trading-core/jobs/entry-scanner", null);
            return nonNull(run.liveSmokeDeal());
        });
        String deal = run.liveSmokeDeal().path("internalId").asString();
        run.marketDeal(deal);
        run.driveUntil("позиция открыта и транш несёт экспозицию", run.exchangeTimeout(), () ->
                run.okx().openPositions(run.externalInstrument()).size() > 0 && exposureOf(deal).signum() > 0);
        run.relayUntil("строка журнала о решении по заявке входа", run.receptionTimeout(),
                () -> nonNull(run.orderOf(deal)));

        JsonNode order = run.okx().order(run.externalInstrument(), run.orderOf(deal));
        JsonNode position = run.okx().openPositions(run.externalInstrument()).getFirst();
        JsonNode mirror = run.deal(deal);
        JournalRead journal = run.journal(run.startedAt());

        assertThat(order.path("state").asString("")).as("E5.1: заявка входа не наполнена: %s", order)
                .isEqualTo("filled");
        assertThat(decimal(order.path("accFillSz"))).as("E5.1: заявка наполнена не целиком: %s", order)
                .isEqualByComparingTo(decimal(order.path("sz")));
        assertThat(decimal(position.path("pos")).abs()).as("E5.1: позиция не равна наполнению заявки")
                .isEqualByComparingTo(decimal(order.path("accFillSz")));
        assertThat(mirror.path("status").asString("")).as("E5.1: сделка не активна: %s", mirror)
                .isEqualTo("ACTIVE");
        assertThat(exposureOf(deal)).as("E5.1: транш не несёт экспозиции").isPositive();
        assertThat(journal.of("DEAL_OPENED", deal)).as("E5.1: нет строки журнала об открытии сделки").isNotEmpty();
        assertThat(journal.of("ORDER_DECIDED", deal)).as("E5.1: нет строки журнала о решении по заявке")
                .isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("E5.2 — Защита живого риска выставлена на площадке, а не только в зеркале")
    void e5_2_liveRiskProtectionStandsOnTheExchange() {
        require(nonNull(run.marketDeal()) && run.okx().openPositions(run.externalInstrument()).size() > 0,
                "состояние после E5.1 не достигнуто");

        run.driveUntil("защитная заявка транша на площадке", run.exchangeTimeout(),
                () -> run.okx().pendingAlgoOrders(run.externalInstrument()).size() > 0);
        run.relayUntil("строка журнала о решении по условной заявке", run.receptionTimeout(),
                () -> isPresent(run.journal(run.startedAt()).of("ALGO_ORDER_DECIDED", run.marketDeal())));

        List<JsonNode> protections = run.okx().pendingAlgoOrders(run.externalInstrument());
        assertThat(protections).as("E5.2: живая экспозиция без защиты на площадке").isNotEmpty();
        protections.forEach(protection -> {
            assertThat(protection.path("instId").asString("")).as("E5.2: защита не на инструменте позиции")
                    .isEqualTo(run.externalInstrument());
            assertThat(protection.path("algoId").asString("")).as("E5.2: у защиты нет биржевого идентификатора")
                    .isNotBlank();
        });
    }

    @Test
    @Order(3)
    @DisplayName("E5.3 — Сворачивание закрывает позицию, и сделка приходит в терминал")
    void e5_3_theTeardownClosesThePositionAndTheDealIsTerminal() {
        require(nonNull(run.marketDeal()), "состояние после E5.2 не достигнуто");

        Reply halt = run.fullHalt();
        run.driveUntil("сделка рыночного входа в терминале", run.exchangeTimeout(), () ->
                SmokeRun.TERMINAL_DEAL.contains(run.deal(run.marketDeal()).path("status").asString("")));
        run.relayUntil("строка журнала о терминале сделки", run.receptionTimeout(),
                () -> isPresent(run.journal(run.startedAt()).of("DEAL_CLOSED", run.marketDeal())));

        assertThat(halt.status()).as("E5.3: полная ступень не принята асинхронно: %s", halt).isEqualTo(202);
        assertThat(run.okx().pendingOrders(run.externalInstrument())).as("E5.3: на площадке осталась заявка")
                .isEmpty();
        assertThat(run.okx().pendingAlgoOrders(run.externalInstrument())).as("E5.3: на площадке осталась защита")
                .isEmpty();
        assertThat(run.okx().openPositions(run.externalInstrument())).as("E5.3: позиция закрыта не целиком")
                .isEmpty();
        assertThat(run.liveSmokeDeal()).as("E5.3: на паре дыма остался живой риск").isNull();
    }

    @Test
    @Order(4)
    @DisplayName("E5.4 — Цена, размер и комиссия взяты с площадки, а не вычислены")
    void e5_4_feeAndResultComeFromTheExchange() {
        require(nonNull(run.marketDeal()) && SmokeRun.TERMINAL_DEAL.contains(
                run.deal(run.marketDeal()).path("status").asString("")), "состояние после E5.3 не достигнуто");

        JsonNode deal = run.deal(run.marketDeal());
        List<JsonNode> closed = run.journal(run.startedAt()).of("DEAL_CLOSED", run.marketDeal());

        assertThat(closed).as("E5.4: нет строки журнала о закрытии сделки").hasSize(1);
        JsonNode content = closed.getFirst().path("content");
        assertThat(content.path("fee").isNull() || content.path("fee").isMissingNode())
                .as("E5.4: комиссии у закрытой сделки нет: %s", content).isFalse();
        assertThat(decimal(content.path("fee")).signum()).as("E5.4: комиссия не отрицательна по знаку издержки: %s",
                content).isNegative();
        assertThat(content.path("resultCurrency").asString("")).as("E5.4: валюта расчёта не названа").isNotBlank();
        assertThat(deal.path("resultProfitCurrency").asString(""))
                .as("E5.4: валюта результата сделки ядра расходится с журналом")
                .isEqualTo(content.path("resultCurrency").asString(""));
        assertThat(Objects.equals(run.instrument(), content.path("instrumentInternalId").asString("")))
                .as("E5.4: строка закрытия не на инструменте дыма").isTrue();
    }

    private static BigDecimal exposureOf(String deal) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode tranche : run.deal(deal).path("tranches")) {
            if (tranche.path("exposure").isNumber()) {
                sum = sum.add(decimal(tranche.path("exposure")));
            }
        }
        return sum;
    }

    private static Boolean isPresent(List<JsonNode> rows) {
        return rows.size() > 0;
    }
}
