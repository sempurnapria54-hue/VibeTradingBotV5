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
 * Группа {@code E4} дыма: заявка выставлена и снята
 * (.claude/tests/cases/smoke-live.md §«E4 — Заявка выставлена и снята»).
 *
 * <p><b>Отрезок начинается состоянием, которое поставила {@code E3}</b>, и
 * определение заводится поверхностью владельца ({@link SmokeDefinition}).
 *
 * <p><b>Зеркала заявки поверхность ядра не отдаёт</b>: выдача сделки несёт
 * сделку и транши, а заявок — ни идентичности, ни биржевого идентификатора.
 * Идентичность заявки (она же клиентский идентификатор на площадке —
 * docs/models/mapping/Order.md) прогон берёт из строки журнала о решении по
 * заявке ({@link SmokeRun#orderOf(String)}); половина ожиданий «зеркало несёт
 * биржевой идентификатор» наружу не наблюдается и не ассертится.
 *
 * <p><b>{@code E4.4} кодом не покрыт</b> — перечень остатка в документе кейсов:
 * живая сделка на счёте одна, и реджект ставится только после сворачивания
 * {@code E4.5}; заявка выше бэнда при выключенном у инструмента бэнде
 * исполнилась бы, а отказ площадки переводит сделку в ошибку и тропу защиты,
 * чью уборку документ не называет.
 */
@Tag("smoke")
@DisplayName("E4 — Заявка выставлена и снята")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E4OrderRestingSmokeTest {

    private static SmokeRun run;

    @BeforeAll
    static void requirePreconditions() {
        run = SmokeRun.get();
        run.requireCommonPreconditions();
    }

    @Test
    @Order(1)
    @DisplayName("E4.1 — Определение заведено и активировано, копия доехала до ядра")
    void e4_1_theDefinitionIsActivatedAndItsCopyReachesTheCore() {
        Reply scanBefore = run.perimeter().post("/api/v1/trading-core/jobs/entry-scanner", null);
        assertThat(scanBefore.status()).as("E4.1: тик сканера не принят: %s", scanBefore).isEqualTo(202);
        assertThat(run.liveSmokeDeal()).as("E4.1: до активации определения сделка по паре дыма завелась").isNull();

        String definition = run.activateDefinition(SmokeDefinition.resting(run.account(), run.instrument()));
        run.restingDefinition(definition);
        Reply read = run.perimeter().get("/api/v1/strategies/" + definition);
        run.relayUntil("строка журнала об активации определения дыма", run.receptionTimeout(),
                () -> run.journal(run.startedAt()).records().stream().anyMatch(row ->
                        "STRATEGY_ACTIVATED".equals(row.path("eventType").asString(""))
                                && Objects.equals(definition, row.path("strategyInternalId").asString(""))));

        assertThat(read.json().path("status").asString("")).as("E4.1: определение дыма не ACTIVE: %s", read)
                .isEqualTo("ACTIVE");
        assertThat(run.liveSmokeDeal()).as("E4.1: активация сама завела сделку — сделку заводит сканер").isNull();
    }

    @Test
    @Order(2)
    @DisplayName("E4.2 — Проход оркестратора ставит заявку заведомо неисполнимой ценой")
    void e4_2_theOrchestratorPlacesAnOrderThatCannotFill() {
        require(nonNull(run.restingDefinition()), "состояние после E4.1 не достигнуто");

        run.driveUntil("сделка дыма заведена сканером", run.exchangeTimeout(), () -> {
            run.perimeter().post("/api/v1/trading-core/jobs/entry-scanner", null);
            return nonNull(run.liveSmokeDeal());
        });
        String deal = run.liveSmokeDeal().path("internalId").asString();
        run.restingDeal(deal);
        run.driveUntil("заявка дыма в книге площадки", run.exchangeTimeout(),
                () -> run.okx().pendingOrders(run.externalInstrument()).size() > 0);
        run.relayUntil("строки журнала о сделке и решении по заявке", run.receptionTimeout(),
                () -> nonNull(run.orderOf(deal)));

        JsonNode detail = run.deal(deal);
        JsonNode book = run.okx().pendingOrders(run.externalInstrument());
        JsonNode rules = run.perimeter().get("/api/v1/market-data/instruments/" + run.instrument() + "/rules").json();
        JsonNode prices = run.perimeter().get("/api/v1/market-data/instruments/" + run.instrument() + "/prices").json();
        JournalRead journal = run.journal(run.startedAt());
        run.restingClientOrderId(run.orderOf(deal));

        assertThat(detail.path("tranches")).as("E4.2: у сделки не ровно один транш: %s", detail).hasSize(1);
        assertThat(SmokeRun.TERMINAL_DEAL).as("E4.2: сделка дыма уже терминальна: %s", detail)
                .doesNotContain(detail.path("status").asString(""));
        assertThat(book).as("E4.2: в книге площадки не ровно одна заявка по инструменту дыма").hasSize(1);
        JsonNode order = book.get(0);
        assertThat(order.path("clOrdId").asString(""))
                .as("E4.2: в книге не заявка системы — клиентский идентификатор не тот, что в журнале")
                .isEqualTo(run.restingClientOrderId());
        assertThat(order.path("state").asString("")).as("E4.2: заявка не стои́т в книге: %s", order)
                .isEqualTo("live");
        assertThat(decimal(order.path("accFillSz"))).as("E4.2: у неисполнимой заявки есть наполнение: %s", order)
                .isZero();
        assertThat(decimal(order.path("sz"))).as("E4.2: размер заявки ниже минимального лота")
                .isGreaterThanOrEqualTo(decimal(rules.path("externalMinSize")));
        BigDecimal price = decimal(order.path("px"));
        assertThat(price.remainder(decimal(rules.path("externalTickSize"))).signum())
                .as("E4.2: цена заявки %s не кратна шагу цены", price).isZero();
        assertThat(price).as("E4.2: цена покупки не ниже рынка — заявка исполнима")
                .isLessThan(decimal(prices.path("externalLastPrice")));
        assertThat(journal.of("DEAL_OPENED", deal)).as("E4.2: нет строки журнала об открытии сделки").isNotEmpty();
        assertThat(journal.of("ORDER_DECIDED", deal)).as("E4.2: нет строки журнала о решении по заявке")
                .isNotEmpty();
        assertThat(run.perimeter().get("/api/v1/strategies/" + run.restingDefinition()).json().path("status")
                .asString("")).as("E4.2: состояние определения тронуто ходом ядра").isEqualTo("ACTIVE");
    }

    /**
     * Ненайденная заявка — отказ прогона, а не красный кейс: без тождества
     * счетов группа-конец читала бы, возможно, чужой счёт, и пустой ответ там
     * был бы неотличим от возврата.
     */
    @Test
    @Order(3)
    @DisplayName("E4.3 — Клиентский идентификатор ядра доехал до площадки и вернулся тем же")
    void e4_3_theCoreClientOrderIdReachesTheExchangeUnchanged() {
        require(nonNull(run.restingClientOrderId()), "состояние после E4.2 не достигнуто");

        JsonNode deal = run.deal(run.restingDeal());
        JsonNode order = run.okx().order(run.externalInstrument(), run.restingClientOrderId());
        require(order.has("ordId"), "заявку системы " + run.restingClientOrderId()
                + " ключ прогона не нашёл — ключ прогона и ключ счёта системы не одного счёта");
        run.accountIdentityProven(Boolean.TRUE);

        assertThat(deal.path("instrumentInternalId").asString(""))
                .as("E4.3: сделка ядра не на инструменте дыма").isEqualTo(run.instrument());
        assertThat(order.path("clOrdId").asString(""))
                .as("E4.3: клиентский идентификатор изменён площадкой либо коннектором")
                .isEqualTo(run.restingClientOrderId());
        assertThat(order.path("ordId").asString("")).as("E4.3: биржевого идентификатора у заявки нет").isNotBlank();
        assertThat(order.path("tag").asString(""))
                .as("E4.3: маркер принадлежности заявки системе площадкой не читается").isNotBlank();
        assertThat(order.path("instId").asString("")).as("E4.3: заявка не на инструменте дыма")
                .isEqualTo(run.externalInstrument());
    }

    @Test
    @Order(4)
    @DisplayName("E4.5 — Снятие: ступень сворачивания убирает заявку с площадки, зеркало в терминале")
    void e4_5_theTeardownRungRemovesTheOrderAndTheDealIsTerminal() {
        require(nonNull(run.restingDeal()), "состояние после E4.2 не достигнуто");

        Reply halt = run.fullHalt();
        run.driveUntil("сделка дыма в терминале после полной ступени", run.exchangeTimeout(), () ->
                SmokeRun.TERMINAL_DEAL.contains(run.deal(run.restingDeal()).path("status").asString("")));
        run.relayUntil("строки журнала о ступени и терминале сделки", run.receptionTimeout(), () -> {
            JournalRead journal = run.journal(run.startedAt());
            return isPresent(journal.of("DEAL_CLOSED", run.restingDeal()))
                    && journal.records().stream().anyMatch(row ->
                    "HOLD_RAISED".equals(row.path("eventType").asString(""))
                            && Objects.equals(run.instrument(), row.path("instrumentInternalId").asString("")));
        });

        assertThat(halt.status()).as("E4.5: полная ступень не принята асинхронно: %s", halt).isEqualTo(202);
        JsonNode closed = run.deal(run.restingDeal());
        assertThat(closed.path("closeReason").asString("")).as("E4.5: сделка закрыта без причины: %s", closed)
                .isNotBlank();
        assertThat(run.liveSmokeDeal()).as("E4.5: на паре дыма остался живой риск").isNull();
        assertThat(run.okx().pendingOrders(run.externalInstrument())).as("E4.5: заявка дыма осталась в книге")
                .isEmpty();
        assertThat(run.okx().pendingAlgoOrders(run.externalInstrument())).as("E4.5: на площадке осталась условная"
                + " заявка").isEmpty();
        assertThat(run.okx().openPositions(run.externalInstrument())).as("E4.5: на площадке открылась позиция")
                .isEmpty();
        assertThat(run.perimeter().get("/api/v1/strategies/" + run.restingDefinition()).json().path("status")
                .asString("")).as("E4.5: ступень ядра тронула состояние определения").isEqualTo("ACTIVE");
    }

    private static Boolean isPresent(List<JsonNode> rows) {
        return rows.size() > 0;
    }
}
