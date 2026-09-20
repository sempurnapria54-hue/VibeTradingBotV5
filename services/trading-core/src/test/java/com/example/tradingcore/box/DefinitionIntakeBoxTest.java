package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B8} — приём фактов владельца определений.
 *
 * <p><b>Предмет группы — СООБЩЕНИЕ как вход, а выход его лежит в базе:</b>
 * строкой копии, её статусом и отметкой обработанного в inbox. Тика здесь
 * нет вовсе — ход начинается с записи в теме владельца
 * (.claude/tests/cases/trading-core.md §«Чем достаются выходы»).
 *
 * <p><b>Отрицания группы наблюдаются БАРЬЕРОМ, а не паузой</b>
 * ({@link TradingCoreBox#barrier()}): «второй копии не появилось» и
 * «дерево не переписано» суть утверждения о состоянии ПОСЛЕ обработки, а
 * пауза сказала бы только о том, что мы не успели посмотреть.
 *
 * <p><b>Идентичность события назначает кейс.</b> По ней идёт дедуп у
 * читателя, и повторная доставка — предмет отдельной клетки — выражается
 * ровно тем, что вторая запись несёт ТУ ЖЕ идентичность.
 *
 * <p><b>Ветви без строки несомы журналом приложения</b> ({@link AppLog}):
 * пропуск записи без конверта, пропуск неизвестного класса и отказ разбора
 * содержимого поверхности не имеют и строки не заводят.
 */
class DefinitionIntakeBoxTest extends SharedTradingCoreBox {

    /** Идентичность определения, которым ходит большинство клеток. */
    private static final String DEFINITION = "S1";

    /** Второе определение: им наблюдается продолжение обработки. */
    private static final String SECOND_DEFINITION = "S2";

    /** Класс события вне перечня: производитель вправе завести его раньше читателя. */
    private static final String UNKNOWN_EVENT_TYPE = "STRATEGY_REHEARSED";

    @Test
    @DisplayName("B8.1 — активация заводит копию дерева и делает её активной")
    void anActivationCreatesTheWholeTreeAndMarksTheEventConsumed() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withComputations(DEFINITION, ACCOUNT, INSTRUMENT);

        String eventId = activate(definition);

        assertThat(rows.count("strategies")).isEqualTo(1L);
        // Дерево заведено целиком: оба индикаторных объявления и структурное.
        assertThat(rows.count("strategy_indicator_settings")).isEqualTo(2L);
        assertThat(rows.count("strategy_market_structure_settings")).isEqualTo(1L);
        // Отметка обработанного легла ТОЙ ЖЕ транзакцией: копия и отметка
        // существуют вместе, и порознь их не бывает.
        assertThat(rows.countWhere("inbox_events", "event_id", eventId)).isEqualTo(1L);
        assertThat(rows.row("inbox_events", "event_id", eventId).get("event_type"))
                .isEqualTo(STRATEGY_ACTIVATED);
        List<Object> nodes = indicatorNodeIds();

        // Повторная доставка того же сообщения: дедуп по идентичности события.
        redeliverActivation(eventId, definition);
        barrier();

        assertThat(rows.count("strategies")).isEqualTo(1L);
        // Дерево не переписано — иначе закрепление детали у живой сделки
        // оборвалось бы (docs/models/domain/aggregate/Strategy.md).
        assertThat(indicatorNodeIds()).isEqualTo(nodes);
        assertThat(rows.countWhere("inbox_events", "event_id", eventId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("B8.2 — повторная активация двигает только статус")
    void aSecondActivationMovesTheStatusOnlyAndKeepsThePinnedDetail() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);
        activate(definition);
        Long detail = detailId();
        moveDefinition(STRATEGY_DEACTIVATED, definition, Strategy.Status.INACTIVE.name());
        Long deal = pinnedDeal(detail);

        activate(definition);

        assertThat(statusOf(DEFINITION)).isEqualTo(Strategy.Status.ACTIVE.name());
        // Узел дерева тот же: закрепление детали у живой сделки цело.
        assertThat(rows.count("strategy_details")).isEqualTo(1L);
        assertThat(detailId()).isEqualTo(detail);
        assertThat(rows.row("deals", "id", deal).get("strategy_detail_id")).isEqualTo(detail);
    }

    @Test
    @DisplayName("B8.3 — деактивация и удаление двигают статус, строку не удаляя")
    void deactivationAndDeletionMoveTheStatusWithoutRemovingTheRow() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);
        activate(definition);
        Long detail = detailId();
        Long deal = pinnedDeal(detail);

        moveDefinition(STRATEGY_DEACTIVATED, definition, Strategy.Status.INACTIVE.name());
        moveDefinition(STRATEGY_DELETED, definition, Strategy.Status.DELETED.name());

        // Строка на месте: деталь нужна сделке до самого терминала, в том
        // числе на выходе, который удаление и запускает.
        assertThat(rows.count("strategies")).isEqualTo(1L);
        assertThat(rows.count("strategy_details")).isEqualTo(1L);
        assertThat(rows.row("deals", "id", deal).get("strategy_detail_id")).isEqualTo(detail);
    }

    @Test
    @DisplayName("B8.4 — событие о определении без копии — штатный исход")
    void anEventAboutAnAbsentCopyIsConsumedWithoutEffectAndDoesNotBlockTheNext() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy absent = Definitions.withDetail("S-absent", ACCOUNT, INSTRUMENT);
        Strategy other = Definitions.withDetail(SECOND_DEFINITION, ACCOUNT, INSTRUMENT);

        String absentEvent = moveDefinitionWithoutWaiting(STRATEGY_DELETED, absent);
        String activation = activate(other);

        // Первое отмечено обработанным без следствия: отказ на нём занял бы
        // партию и остановил применение следующих.
        assertThat(rows.countWhere("inbox_events", "event_id", absentEvent)).isEqualTo(1L);
        assertThat(rows.countWhere("strategies", "internal_id", "S-absent")).isZero();
        // Второе применено.
        assertThat(rows.countWhere("inbox_events", "event_id", activation)).isEqualTo(1L);
        assertThat(statusOf(SECOND_DEFINITION)).isEqualTo(Strategy.Status.ACTIVE.name());
    }

    @Test
    @DisplayName("B8.5 — сообщение без конверта пропускается с записью")
    void aRecordWithoutEnvelopeHeadersIsSkippedWithAJournalLine() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);
        Integer mark = AppLog.mark();

        // Без заголовка идентичности события.
        Wire.publishStrategyFact(strategyTopic(), Map.of("eventType", STRATEGY_ACTIVATED),
                Definitions.activated(definition));
        // Без заголовка класса события.
        Wire.publishStrategyFact(strategyTopic(), Map.of("eventId", "ev-headerless"),
                Definitions.activated(definition));
        barrier();

        assertThat(rows.count("strategies")).isZero();
        assertThat(AppLog.since(mark)).contains("Strategy fact without envelope headers is skipped offset=");
        // Обработка следующих записей продолжается: барьер её и предъявляет.
        assertThat(rows.count("inbox_events")).isEqualTo(1L);
    }

    @Test
    @DisplayName("B8.6 — неизвестный класс события пропускается, а неразбираемое содержимое — нет")
    void anUnknownEventClassIsSkippedWhileAnUnreadablePayloadBreaksTheProcessing() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);
        activate(definition);
        Integer mark = AppLog.mark();

        Wire.publishStrategyFact(strategyTopic(), "ev-unknown-class", UNKNOWN_EVENT_TYPE,
                Definitions.activated(definition), TENANT);
        barrier();

        // Первая пропущена с записью: производитель вправе завести класс
        // раньше читателя.
        assertThat(AppLog.since(mark)).contains("Strategy fact of an unknown class is skipped eventType="
                + UNKNOWN_EVENT_TYPE);
        assertThat(rows.countWhere("inbox_events", "event_id", "ev-unknown-class")).isZero();

        Integer second = AppLog.mark();
        Wire.publishStrategyFact(strategyTopic(), "ev-unreadable", STRATEGY_ACTIVATED,
                unreadableActivation(), TENANT);
        barrier();

        // Вторая РОНЯЕТ обработку: пропуск оставил бы копию в прежнем
        // состоянии молча, а событие — потерянным без следа.
        assertThat(AppLog.since(second)).contains("Strategy fact payload is not readable as");
        assertThat(rows.countWhere("inbox_events", "event_id", "ev-unreadable")).isZero();
        assertThat(statusOf(DEFINITION)).isEqualTo(Strategy.Status.ACTIVE.name());
    }

    @Test
    @DisplayName("B8.8 — тропа приёма и тропа публикации поднимаются обе")
    void bothSidesOfTheEventPathRiseWithTheContext() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Strategy definition = Definitions.withDetail(DEFINITION, ACCOUNT, INSTRUMENT);

        // Подписка установлена: копия заведена ПРИНЯТЫМ сообщением, а не
        // нашей вставкой.
        activate(definition);
        assertThat(statusOf(DEFINITION)).isEqualTo(Strategy.Status.ACTIVE.name());

        // Публикатор получил свой клиент: строка outbox доехала до темы.
        Wire.Mark mark = Wire.mark();
        freeze(ACCOUNT);
        tick(Tick.OUTBOX_RELAY);

        assertThat(Wire.publishedSince(mark)).isNotEmpty();
    }

    /** Статус копии определения. */
    private String statusOf(String internalId) {
        return String.valueOf(rows.row("strategies", "internal_id", internalId).get("status"));
    }

    /** Числовые ключи индикаторных объявлений копии в порядке заведения. */
    private List<Object> indicatorNodeIds() {
        return rows.all("strategy_indicator_settings").stream().map(row -> row.get("id")).toList();
    }

    /** Числовой ключ единственной детали копии. */
    private Long detailId() {
        return ((Number) rows.all("strategy_details").getFirst().get("id")).longValue();
    }

    /**
     * Кладёт событие жизненного цикла и НЕ ждёт следствия: клетка о
     * событии без копии утверждает ровно то, что следствия нет.
     */
    private String moveDefinitionWithoutWaiting(String eventType, Strategy definition) {
        String eventId = "ev-absent-" + definition.getInternalId();
        Wire.publishStrategyFact(strategyTopic(), eventId, eventType,
                Definitions.lifecycle(definition), TENANT);
        return eventId;
    }

    /**
     * Содержимое знакомого класса, которое в объявленную форму не
     * разбирается: снимок определения приехал строкой вместо дерева.
     */
    private String unreadableActivation() {
        return """
                {"strategyInternalId": "S1", "exchangeAccountInternalId": "A1",
                 "instrumentInternalId": "I1", "actor": "USER", "definition": "дерева нет"}
                """;
    }

    /**
     * Живая сделка, закрепившая деталь копии.
     *
     * <p><b>Прямая запись, и область у неё названа</b> ({@link Rows#put}):
     * тропы «сделка по объявлению» у ящика ещё нет — её строит группа
     * {@code B1}, — а предмет этой клетки есть ЦЕЛОСТНОСТЬ ссылки, не
     * заведение сделки.
     */
    private Long pinnedDeal(Long detail) {
        return rows.insert("""
                insert into deals (internal_id, exchange_account_id, instrument_id, strategy_detail_id,
                                   status, direction, entry_reason)
                values ('D1', ?, ?, ?, 'ACTIVE', 'LONG', 'STRATEGY') returning id
                """, accountId(ACCOUNT), instrumentId(INSTRUMENT), detail);
    }
}
