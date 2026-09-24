package com.example.tradingcore.box;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B3} — команды площадке: жизненный цикл, повторы, классы
 * отказа.
 *
 * <p><b>Предмет группы — ЖИЗНЕННЫЙ ЦИКЛ команды, а не арифметика её
 * параметров.</b> Сколько контрактов взял вход и по какой цене —
 * предмет уровня 2 ({@code trading-core-risk}, {@code trading-core-calc});
 * здесь наблюдается, ЧТО ушло к соседу, в каком порядке, сколько раз и
 * что осталось в базе (.claude/tests/cases/trading-core.md §«Объём»).
 *
 * <p><b>Тропа до команды строится ЦЕЛИКОМ тропами ящика</b>
 * ({@link #openCommandDeal}): проекции — тиком синка, ставка комиссии —
 * тиком её синка, числа риск-аппетита — своей поверхностью, сделка с
 * траншем — тиком отбора входа, снимок средств — первым тиком
 * сопровождения. Прямой записи в предусловиях группы нет ни одной, и это
 * не аккуратность: команду площадке порождает расчёт, а расчёт читает
 * ровно то, что эти тропы и кладут.
 *
 * <p><b>Определение группы объявляет то, чего не объявляло определение
 * отбора входа</b>: встроенную защиту, долю аллокации и четыре потолка
 * риска на детали ({@code Definitions#withEntryCommandOnPhase}). Без них
 * расчёт и преконтроль отвергают действие раньше команды — то есть
 * предмет группы не достигается вовсе.
 *
 * <p><b>Связка фич несёт ЦЕНЫ</b> ({@code Feed#featuresWithPrice}): чтение
 * под расчёт спрашивает их безусловно, и связка без цены даёт отказ
 * {@code NO_REFERENCE_PRICE} на первом же действии.
 *
 * <p><b>Прямой записи в группе нет ни одной.</b>
 *
 * <p><b>Клетки с меткой {@code debt} красны ПО ПОСТРОЕНИЮ</b>: их ожидание
 * взято из дома, который дерево кода на этой тропе не исполняет, и
 * ослаблять его под текущий факт значило бы закрепить дефект
 * (.claude/tests/cases/trading-core.md §«Ожидание берётся из дома, даже
 * когда сегодня оно не исполнено»).
 */
class DealCommandBoxTest extends SharedTradingCoreBox {

    /** Основа идентичности определения группы. */
    private static final String DEFINITION = "S-CMD";

    /** Биржевой момент, который отдаёт коннектор. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /** Последняя цена момента: от неё считаются и уровень, и размер. */
    private static final String LAST_PRICE = "100";

    /** Класс события решения о заявке. */
    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    /** Жёсткая ступень биржевого счёта. */
    private static final String TRADE_BLOCKED = "TRADE_BLOCKED";

    /** Отсутствие ступени: рабочее состояние счёта. */
    private static final String NO_RUNG = "ACTIVE";

    /**
     * Потолок ожидания перевзвода строки: объявленный откат политики
     * экспоненциален, и первая попытка укладывается в него с запасом.
     */
    private static final Duration RETRY_HORIZON = Duration.ofSeconds(60);

    @Test
    @DisplayName("B3.1 — создание заявки на биржу не ходит и пишет решение с событием")
    void theOrderCreationDoesNotReachTheExchangeAndWritesTheDecisionWithItsEvent() {
        openCommandDeal();

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(placementCalls()).isEmpty();
        Map<String, Object> order = rows.all("orders").getFirst();
        assertThat(String.valueOf(order.get("internal_id"))).isNotBlank();
        assertThat(order.get("external_id")).isNull();
        assertThat(eventTypes()).contains(ORDER_DECIDED);
    }

    @Test
    @DisplayName("B3.2 — отправка идёт следующим проходом и переводит транш во вход")
    void theSubmissionGoesOnTheNextPassAndMovesTheTrancheToTheSubmittedEntry() {
        openCommandDeal();
        tick(Tick.DEAL_ORCHESTRATOR);
        String clientId = clientIdOfOrder();
        acceptPlacement(clientId);
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(1);
        assertThat(placementBody()).contains(clientId);
        // Подтверждение приёма состоянием не считается: нога стои́т в
        // ОЖИДАНИИ, а не в исполненном — его подтверждает добыча.
        assertThat(orderStatus()).isEqualTo("PENDING");
        // Ребро транша едет ПРОХОДОМ, на котором отправка уже подтверждена
        // фактом: выходная проверка обработчика читает ногу, а нога
        // отправляется командой ПОСЛЕ того, как переход отдан.
        connector.answers(lookupPath(ACCOUNT), Feed.order("ex-1", clientId, "ACTIVE"));
        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(trancheStatus()).isEqualTo("ENTRY_SUBMITTED");
        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(1);
    }

    @Test
    @Tag("debt")
    @DisplayName("B3.3 — повторная отправка ищет сущность по клиентскому идентификатору, а не шлёт вторую")
    void theRepeatedSubmissionLooksTheEntityUpByItsClientIdentifierInsteadOfSendingASecondOne() {
        openCommandDeal();
        tick(Tick.DEAL_ORCHESTRATOR);
        String clientId = clientIdOfOrder();
        // Отправка состоялась, но ответ до нас не дошёл: площадка объявлена
        // недостижимой, то есть отказ повторяем, — а заявку стаб помнит.
        connector.answers(placementPath(ACCOUNT), 502, Feed.peerFailure("EXCHANGE_UNREACHABLE"));
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(actionState().get("status")).isEqualTo("RETRY_PENDING");
        connector.answers(lookupPath(ACCOUNT), Feed.order("ex-1", clientId, "ACTIVE"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        ticksUntil(() -> nonNull(rows.all("orders").getFirst().get("external_id")));

        assertThat(connector.requests(lookupPath(ACCOUNT))).hasSize(1);
        assertThat(connector.requests(placementPath(ACCOUNT))).isEmpty();
        assertThat(rows.count("orders")).isEqualTo(1L);
        assertThat(rows.all("orders").getFirst().get("external_id")).isEqualTo("ex-1");
        // Факт отправки восстановила ИМЕННО отправка, а не добыча: тот же
        // путь поиска зовут оба звена, и по одному попаданию стаба они
        // неразличимы. Различают их МЕТКИ: восстановление ставит ногу в
        // ожидание и строку исполнения в отправленное, а добыча — ногу в
        // наблюдённый статус источника и строку в завершённое.
        //
        // Красно по построению: на этой тропе ветвь восстановления
        // НЕДОСТИЖИМА — добыча живой ноги адоптирует биржевой
        // идентификатор раньше, чем повтор отправки доходит до своего
        // поиска (находка F4 захода). Ожидание взято из дома
        // (docs/components/SubmitOrderExecutor.md) и под факт не
        // ослабляется.
        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(actionState().get("status")).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("B3.4 — добыча обрывается на первом ответе, несущем искомый факт")
    void theEvidenceCycleStopsAtTheFirstAnswerThatCarriesTheSoughtFact() {
        submitEntry();
        connector.answers(lookupPath(ACCOUNT), Feed.order("ex-1", clientIdOfOrder(), "ACTIVE"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Первая нога лестницы — поиск по идентификатору, и она же обрывает
        // добычу: ни живые заявки, ни история не спрашивались.
        assertThat(connector.requests(lookupPath(ACCOUNT))).hasSize(1);
        assertThat(connector.requests(pendingPath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(historyPath(ACCOUNT))).isEmpty();
        assertThat(actionState().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("B3.7 — контролируемое исключение границы поднимает жёсткую биржевую ступень")
    void theControlledBoundaryFailureRaisesTheHardExchangeRung() {
        submitEntry();
        connector.answers(lookupPath(ACCOUNT), 422, Feed.peerFailure("EXTERNAL_STATUS"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(rows.count("anomaly_reports")).isEqualTo(1L);
        assertThat(dealStatus()).isEqualTo("ERROR");
        // Отметка исхода на ноге ПЕРЕЖИЛА бросок: транзакция звена
        // завершилась отказом, а причина в базе есть.
        assertThat(rows.all("orders").getFirst().get("close_reason"))
                .isEqualTo("UNKNOWN_EXTERNAL_STATUS");
    }

    @Test
    @DisplayName("B3.8 — отказ источника в наших кредах — свой класс, и повтора он не получает")
    void theRejectedCredentialsAreTheirOwnClassAndGetNoRetry() {
        openCommandDeal();
        tick(Tick.DEAL_ORCHESTRATOR);
        connector.answers(placementPath(ACCOUNT), 401, Feed.peerFailure("EXCHANGE_CREDENTIALS_REJECTED"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        // Повтора нет ни одного: класс изъят из повторяемого, и вторая
        // попытка не состоялась даже при пределе три.
        assertThat(connector.requests(placementPath(ACCOUNT))).hasSize(1);
        Map<String, Object> state = actionState();
        assertThat(state.get("attempt_count")).isEqualTo(1);
        assertThat(state.get("status")).isEqualTo("FAILED");
        assertThat(state.get("next_retry_at")).isNull();
    }

    @Test
    @Tag("debt")
    @DisplayName("B3.9 — отказ соседа по ярусу сделку в ошибку не уводит")
    void theTierPeerFailureDoesNotMoveTheDealToError() {
        openCommandDeal();
        marketData.answers(featuresPath(INSTRUMENT), 503, Feed.peerFailure("PEER_SERVICE_UNAVAILABLE"));
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.DEAL_ORCHESTRATOR);

        // Долг: общий перехватчик прохода ловит отказ соседа наравне со
        // всяким неожиданным исключением (.claude/work/backlog.md §«Отказ
        // соседа по ярусу на проходе сопровождения уводит сделку в ERROR»).
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        assertThat(rows.count("orders")).isEqualTo(0L);
        assertThat(rows.count("deal_strategy_action_states")).isEqualTo(0L);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(rows.count("anomaly_reports")).isEqualTo(0L);
    }

    // ------------------------------------------------------------------
    // Предусловия группы
    // ------------------------------------------------------------------

    /**
     * Сделка с траншем в предвходовой проверке, чей вход доходит до
     * команды: все входы расчёта и преконтроля поставлены тропами ящика, а
     * снимок средств снят первым тиком сопровождения.
     *
     * @return снимок определения, которым сделка заведена
     */
    private Strategy openCommandDeal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        assignRiskAppetite();
        assignLeverage(ACCOUNT, INSTRUMENT);
        syncFeeRate();
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithPrice(MarketPhase.Type.BULL_TREND.name(), LAST_PRICE));
        connector.answers(balancePath(ACCOUNT), balanceBody());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        Strategy definition = Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND);
        activate(definition);
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(1L);
        // Первый тик сопровождения снимает снимок средств и работы не
        // делает: предвходовая проверка обеспечивает его ДО работы.
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(rows.count("orders")).isEqualTo(0L);
        PeerStub.all().forEach(PeerStub::forgetRequests);
        return definition;
    }

    /** Числа риск-аппетита тенанта: операнды преконтроля, своей поверхностью. */
    private void assignRiskAppetite() {
        assertThat(put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("5", "10", "4")).status())
                .isEqualTo(200);
    }

    /** Ставка комиссии комиссионного уровня счёта: тиком её синка. */
    private void syncFeeRate() {
        connector.answers(feeRatePath(ACCOUNT), Feed.array(Feed.tradeFeeRate()));
        tick(Tick.TRADE_FEE_RATES);
        assertThat(rows.count("trade_fee_rates")).isEqualTo(1L);
    }

    /** Путь чтения связки фич момента у владельца рыночных данных. */
    private String featuresPath(String instrumentInternalId) {
        return PEER_INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }

    /** Корень путей счёта у коннектора. */
    private String accountPath(String accountInternalId) {
        return "/api/v1/accounts/" + accountInternalId;
    }

    /** Путь чтения снимка средств у коннектора. */
    private String balancePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/balance";
    }

    /** Путь приватного чтения ставок комиссии у коннектора. */
    private String feeRatePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/trade-fee-rates";
    }

    /** Путь размещения обычной заявки у коннектора. */
    private String placementPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders";
    }

    /** Первая нога лестницы добычи: поиск ноги по идентификатору. */
    private String lookupPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/lookup";
    }

    /** Вторая нога: живые заявки инструмента. */
    private String pendingPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/pending/instrument";
    }

    /** Третья нога: история заявок инструмента. */
    private String historyPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/history";
    }

    /**
     * Доводит ногу входа до ОТПРАВЛЕННОЙ: создание, затем подтверждённое
     * площадкой размещение — обоими проходами тропы ящика.
     *
     * <p>Транш при этом ещё в предвходовой проверке: его ребро едет
     * следующим проходом, и предметом оно является у {@code B3.2}, а
     * здесь было бы лишним предусловием.
     */
    private void submitEntry() {
        openCommandDeal();
        tick(Tick.DEAL_ORCHESTRATOR);
        acceptPlacement(clientIdOfOrder());
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(orderStatus()).isEqualTo("PENDING");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Тикает проход сопровождения до наступления названного состояния.
     *
     * <p><b>Число тиков не пиньнуто намеренно.</b> Перевзвод строки,
     * ожидающей повтора, гейтится моментом следующей попытки
     * ({@code StrategyActionOrchestrator#retryDue}), а момент выводится из
     * объявленного отката политики; клетка эту величину не подменяет —
     * она тикает, пока откат не истечёт. Пиньнутое число тиков мерило бы
     * длину отката, а не предмет клетки.
     *
     * @param reached условие, по достижении которого тики прекращаются
     */
    private void ticksUntil(Callable<Boolean> reached) {
        Awaitility.await().atMost(RETRY_HORIZON).pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    tick(Tick.DEAL_ORCHESTRATOR);
                    return reached.call();
                });
    }

    /** Площадка принимает размещение и возвращает свой идентификатор. */
    private void acceptPlacement(String clientId) {
        connector.answers(placementPath(ACCOUNT), Feed.ack("ex-1", clientId));
    }

    /** Клиентский идентификатор единственной ноги входа. */
    private String clientIdOfOrder() {
        return String.valueOf(rows.all("orders").getFirst().get("internal_id"));
    }

    /** Тело единственного запроса размещения. */
    private String placementBody() {
        return connector.single(placementPath(ACCOUNT)).getBodyAsString();
    }

    /** Статус единственной ноги входа, как его видит база. */
    private String orderStatus() {
        return String.valueOf(rows.all("orders").getFirst().get("status"));
    }

    /** Статус единственного транша. */
    private String trancheStatus() {
        return String.valueOf(rows.all("deal_tranches").getFirst().get("status"));
    }

    /** Статус единственной сделки. */
    private String dealStatus() {
        return String.valueOf(rows.all("deals").getFirst().get("status"));
    }

    /** Ступень счёта, как её видит база. */
    private String accountRung() {
        return String.valueOf(rows.row("exchange_accounts", "internal_id", ACCOUNT).get("safety_rung"));
    }

    /** Единственная строка исполнения объявленного действия. */
    private Map<String, Object> actionState() {
        return rows.all("deal_strategy_action_states").getFirst();
    }

    /** Обращения к коннектору, которые размещают сущность на площадке. */
    private List<String> placementCalls() {
        return connector.paths().stream()
                .filter(path -> path.endsWith("/orders") || path.endsWith("/algo-orders")
                        || path.endsWith("/attached-protections"))
                .toList();
    }

    /** Классы событий строк outbox в порядке записи. */
    private List<String> eventTypes() {
        return rows.all("outbox_events").stream()
                .map(row -> String.valueOf(row.get("event_type")))
                .toList();
    }

    /** Снимок средств моментом прогона: возраст ставится В ДАННЫХ. */
    private String balanceBody() {
        String moment = OffsetDateTime.now(ZoneOffset.UTC).toString();
        return """
                {
                  "externalUpdatedAt": "%s",
                  "externalTotalEquity": "100000",
                  "externalAdjustedEquity": "100000",
                  "externalAvailableEquity": "100000",
                  "balances": [
                    {
                      "externalCurrency": "USDT",
                      "externalUpdatedAt": "%s",
                      "externalEquity": "100000",
                      "externalCashBalance": "100000",
                      "externalAvailableBalance": "100000",
                      "externalFrozenBalance": "0"
                    }
                  ]
                }
                """.formatted(moment, moment);
    }
}
