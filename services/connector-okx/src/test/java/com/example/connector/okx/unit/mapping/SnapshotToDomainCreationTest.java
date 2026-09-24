package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.exception.ExternalStatusException;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.BalanceContainerMapper;
import com.example.connector.okx.mapping.CandleMapper;
import com.example.connector.okx.mapping.DealCashFlowMapper;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.snapshot.AlgoOrderExternalSnapshot;
import com.example.connector.okx.snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.connector.okx.snapshot.BalanceContainerExternalSnapshot;
import com.example.connector.okx.snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Снапшот → доменная модель созданием — группа `U21` документа
 * `.claude/tests/cases/okx-mapping.md` (таблицы «snapshot → domain»
 * файлов `docs/models/mapping/` и javadoc каждого перехода).
 *
 * <p><b>Базовая сборка:</b> заполненный граничный снапшот своей формы;
 * вызов перехода материализации. Коллабораторов у перехода нет, кроме
 * конвертера.
 *
 * <p><b>Коннектор ОТДАЁТ модель, а не доливает чужую:</b> переход её
 * создаёт, и пустым остаётся ровно то, чего у источника нет. Доменного
 * статуса ни один переход не ставит — его ставит резолвер, последним
 * шагом чтения, у шлюза.
 *
 * <p>Кейс {@code U21.6} в код не пошёл: дом и код называют разное —
 * `.claude/work/backlog.md` §«Четыре таблицы маппинга отрицают поле,
 * которое их модели несут».
 */
class SnapshotToDomainCreationTest {

    private final OrderMapper orderMapper = Mappers.order();
    private final AlgoOrderMapper algoOrderMapper = Mappers.algoOrder();
    private final PositionMapper positionMapper = Mappers.position();
    private final CandleMapper candleMapper = Mappers.candle();
    private final DealCashFlowMapper cashFlowMapper = Mappers.cashFlow();
    private final BalanceContainerMapper balanceMapper = Mappers.balance();

    /** Снапшот заявки базовой сборки: ответ площадки несёт тип исполнения, как всякий реальный. */
    private OrderExternalSnapshot orderSnapshot() {
        return orderMapper.integrationToSnapshot(OkxFixture.order());
    }

    @Test
    @DisplayName("U21.1 — доменная заявка: сторона переведена, прочее перенесено по имени")
    void u21_1_theDomainOrderIsCreatedFieldByField() {
        Order order = orderMapper.snapshotToDomain(orderSnapshot());

        assertThat(order.getSide()).isEqualTo(Order.Side.BUY);
        assertThat(order.getInternalId()).isEqualTo("tb-1");
        assertThat(order.getExternalId()).isEqualTo("1");
        assertThat(order.getExternalStatus()).isEqualTo("live");
        assertThat(order.getPrice()).isEqualByComparingTo("100");
        assertThat(order.getSize()).isEqualByComparingTo("2");
        assertThat(order.getAccumulatedFillSize()).isEqualByComparingTo("1");
        assertThat(order.getAveragePrice()).isEqualByComparingTo("99.5");
        assertThat(order.getFee()).isEqualByComparingTo("-0.25");
        assertThat(order.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(order.getExternalModifiedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:14:20Z"));
        assertThat(order.getType()).isNull();
    }

    /**
     * Род заявки наш: его ставит создатель ноги, а площадка отдаёт тип
     * исполнения. Перенос по имени разбирал бы {@code limit} доменным
     * перечнем и ронял чтение каждой заявки.
     */
    @ParameterizedTest
    @ValueSource(strings = {"limit", "market", "post_only"})
    @DisplayName("U21.18 — тип исполнения площадки в бизнес-тип заявки не переводится")
    void u21_18_theSourceExecutionTypeDoesNotBecomeTheBusinessType(String ordType) {
        var source = OkxFixture.order();
        source.setOrdType(ordType);

        Order order = orderMapper.snapshotToDomain(orderMapper.integrationToSnapshot(source));

        assertThat(order.getType()).isNull();
        assertThat(order.getInternalId()).isEqualTo("tb-1");
    }

    @Test
    @DisplayName("U21.2 — доменный статус здесь не проставляется")
    void u21_2_theDomainStatusIsNotSetHere() {
        assertThat(orderMapper.snapshotToDomain(orderSnapshot()).getStatus()).isNull();
    }

    /** Это наши величины, источник их не отдаёт, и эхо рефреша их не трогает. */
    @Test
    @DisplayName("U21.3 — поля планового риска, запас до ликвидации, ёмкость и эпизод пусты")
    void u21_3_ourOwnValuesStayEmpty() {
        Order order = orderMapper.snapshotToDomain(orderSnapshot());

        assertThat(order.getPlannedRiskAmount()).isNull();
        assertThat(order.getPlannedRiskCurrency()).isNull();
        assertThat(order.getPlannedEntryPrice()).isNull();
        assertThat(order.getPlannedSizeContracts()).isNull();
        assertThat(order.getPlannedContractValue()).isNull();
        assertThat(order.getPlannedStopPrice()).isNull();
        assertThat(order.getLiquidationDistanceRatio()).isNull();
        assertThat(order.getBookDepthAtPlacement()).isNull();
        assertThat(order.getPositionId()).isNull();
    }

    /** Идентичность защиты живёт на элементе списка. */
    @Test
    @DisplayName("U21.4 — клиентского идентификатора защиты у доменной заявки нет")
    void u21_4_theDomainOrderHasNoAttachedClientId() {
        assertThat(orderSnapshot().getAttachedAlgoInternalId()).isEqualTo("tb-p1");
        assertThat(Order.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("attachedAlgoInternalId");
    }

    /** Уровень живёт на элементе защиты. */
    @Test
    @DisplayName("U21.5 — уровней верхнего уровня у доменной заявки нет")
    void u21_5_theDomainOrderHasNoTopLevelTriggers() {
        assertThat(orderSnapshot().getStopLossTriggerPrice()).isEqualByComparingTo("90");
        assertThat(Order.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("stopLossTriggerPrice", "takeProfitTriggerPrice");
    }

    /** Отказ приходит на этом переходе, а не на построении снапшота. */
    @Test
    @DisplayName("U21.7 — сторона вне словаря отказывает на материализации")
    void u21_7_anUnknownSideRefusesOnMaterialization() {
        var source = OkxFixture.order();
        source.setSide("long");
        OrderExternalSnapshot snapshot = orderMapper.integrationToSnapshot(source);

        assertThat(snapshot.getSide()).isEqualTo("long");
        assertThatThrownBy(() -> orderMapper.snapshotToDomain(snapshot))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    /** Статус защиты выводит резолвер состояния по набору фактов, а не маппер. */
    @Test
    @DisplayName("U21.8 — доменная встроенная защита: перенос по имени, статуса нет")
    void u21_8_theDomainAttachedProtectionHasNoStatus() {
        AttachedAlgoOrderExternalSnapshot snapshot =
                orderMapper.integrationToSnapshot(OkxFixture.attachedInParentBody());

        AttachedAlgoOrder attached = orderMapper.snapshotToDomain(snapshot);

        assertThat(attached.getInternalId()).isEqualTo("tb-p1");
        assertThat(attached.getExternalId()).isEqualTo("9");
        assertThat(attached.getExternalAttachedId()).isEqualTo("a1");
        assertThat(attached.getExternalType()).isEqualTo("condition");
        assertThat(attached.getStopLossTriggerPrice()).isEqualByComparingTo("90");
        assertThat(attached.getTriggerPriceType()).isEqualTo(AlgoOrder.TriggerPriceType.MARK);
        assertThat(attached.getFailCode()).isEqualTo("0");
        assertThat(attached.getStatus()).isNull();
    }

    /** Снапшот несёт только внешние значения, наши приезжают с постановкой. */
    @Test
    @DisplayName("U21.9 — доменная условная заявка: внешние значения ноги есть, наши пусты")
    void u21_9_theDomainAlgoOrderCarriesOnlyExternalLegValues() {
        AlgoOrderExternalSnapshot snapshot =
                algoOrderMapper.integrationToSnapshot(OkxFixture.algoOrder());

        AlgoOrder algoOrder = algoOrderMapper.snapshotToDomain(snapshot);

        assertThat(algoOrder.getInternalId()).isEqualTo("tb-9");
        assertThat(algoOrder.getExternalId()).isEqualTo("9");
        assertThat(algoOrder.getExternalStatus()).isEqualTo("live");
        assertThat(algoOrder.getExternalSize()).isEqualByComparingTo("3");
        assertThat(algoOrder.getCondition().getTrigger().getStopLoss().getExternalType())
                .isEqualTo("mark");
        assertThat(algoOrder.getCondition().getTrigger().getStopLoss().getExternalValue())
                .isEqualByComparingTo("90");
        assertThat(algoOrder.getCondition().getTrigger().getStopLoss().getType()).isNull();
        assertThat(algoOrder.getCondition().getTrigger().getStopLoss().getValue()).isNull();
    }

    /** Тип условия есть наше намерение, у источника его нет. */
    @Test
    @DisplayName("U21.10 — типа условия у материализованной условной заявки нет")
    void u21_10_theConditionTypeIsOurIntent() {
        AlgoOrder algoOrder = algoOrderMapper.snapshotToDomain(
                algoOrderMapper.integrationToSnapshot(OkxFixture.algoOrder()));

        assertThat(algoOrder.getCondition().getType()).isNull();
        assertThat(algoOrder.getConditionType()).isNull();
    }

    /** Заявленные параметры трейлинга приходят при постановке, а не с рефрешем. */
    @Test
    @DisplayName("U21.11 — заявленных параметров трейлинга снапшот рефреша не несёт")
    void u21_11_theDeclaredTrailingParametersAreAbsent() {
        AlgoOrder algoOrder = algoOrderMapper.snapshotToDomain(
                algoOrderMapper.integrationToSnapshot(OkxFixture.algoOrder()));

        assertThat(algoOrder.getCondition().getTrailing().getTrailingPercents()).isNull();
        assertThat(algoOrder.getCondition().getTrailing().getTrailingStepValue()).isNull();
        assertThat(algoOrder.getCondition().getTrailing().getExternalPrice()).isEqualByComparingTo("95");
    }

    @Test
    @DisplayName("U21.12 — доменная позиция: перенос по имени, статус резолвит вызывающий")
    void u21_12_theDomainPositionHasNoStatus() {
        Position position = positionMapper.snapshotToDomain(
                positionMapper.integrationToSnapshot(OkxFixture.position()));

        assertThat(position.getExternalId()).isEqualTo("p1");
        assertThat(position.getExternalSize()).isEqualByComparingTo("5");
        assertThat(position.getDirection()).isEqualTo(Position.Direction.LONG);
        assertThat(position.getExternalAverageEntryPrice()).isEqualByComparingTo("100");
        assertThat(position.getExternalMarkPrice()).isEqualByComparingTo("101");
        assertThat(position.getExternalLiquidationPrice()).isEqualByComparingTo("50");
        assertThat(position.getExternalMargin()).isEqualByComparingTo("20");
        assertThat(position.getExternalUnrealizedProfit()).isEqualByComparingTo("5");
        assertThat(position.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(position.getStatus()).isNull();
    }

    /** Признак закрытия — фильтр, а не признак свечи. */
    @Test
    @DisplayName("U21.13 — доменная свеча признака закрытия не несёт")
    void u21_13_theDomainCandleCarriesNoConfirm() {
        Candle candle = candleMapper.snapshotToDomain(
                candleMapper.integrationToSnapshot(OkxFixture.candle()));

        assertThat(candle.getOpenTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(candle.getOpen()).isEqualByComparingTo("100");
        assertThat(candle.getHigh()).isEqualByComparingTo("110");
        assertThat(candle.getLow()).isEqualByComparingTo("90");
        assertThat(candle.getClose()).isEqualByComparingTo("105");
        assertThat(candle.getVolume()).isEqualByComparingTo("12");
        assertThat(Candle.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("confirm");
    }

    /** Категорию, биржу, курс и ссылку на сделку производит вызывающий. */
    @Test
    @DisplayName("U21.14 — доменное движение: только факты ответа")
    void u21_14_theDomainCashFlowCarriesOnlyResponseFacts() {
        DealCashFlow flow = cashFlowMapper.snapshotToDomain(
                cashFlowMapper.integrationToSnapshot(OkxFixture.cashFlow()));

        assertThat(flow.getExternalBillId()).isEqualTo("b1");
        assertThat(flow.getAmount()).isEqualByComparingTo("12.5");
        assertThat(flow.getExternalFee()).isEqualByComparingTo("-0.25");
        assertThat(flow.getCategory()).isNull();
        assertThat(flow.getExchangeAccountId()).isNull();
        assertThat(flow.getAppliedRate()).isNull();
        assertThat(flow.getRateStatus()).isNull();
        assertThat(flow.getDealId()).isNull();
    }

    /** Строки разбираются здесь; счёт проставляет владелец. */
    @Test
    @DisplayName("U21.15 — доменный контейнер: три равновесные величины числами, счёт пуст")
    void u21_15_theDomainContainerParsesTheEquities() {
        BalanceContainer container = balanceMapper.snapshotToDomain(
                balanceMapper.integrationToSnapshot(OkxFixture.balance()));

        assertThat(container.getExternalTotalEquity()).isEqualByComparingTo("1000");
        assertThat(container.getExternalAdjustedEquity()).isEqualByComparingTo("1000");
        assertThat(container.getExternalAvailableEquity()).isEqualByComparingTo("900");
        assertThat(container.getExternalUpdatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(container.getBalances()).hasSize(1);
        assertThat(container.getBalances().getFirst().getExternalCurrency()).isEqualTo("USDT");
        assertThat(container.getBalances().getFirst().getExternalEquity()).isEqualByComparingTo("900");
        assertThat(container.getExchangeAccountId()).isNull();
    }

    @Test
    @DisplayName("U21.16 — пустая строка равновесной величины даёт пустоту")
    void u21_16_anEmptyEquityStringBecomesEmptiness() {
        BalanceContainerExternalSnapshot snapshot =
                balanceMapper.integrationToSnapshot(OkxFixture.balance());
        BalanceContainerExternalSnapshot blanked = BalanceContainerExternalSnapshot.builder()
                .externalUpdatedAt(snapshot.getExternalUpdatedAt())
                .externalTotalEquity("")
                .externalAdjustedEquity(snapshot.getExternalAdjustedEquity())
                .externalAvailableEquity(snapshot.getExternalAvailableEquity())
                .balances(snapshot.getBalances())
                .build();

        BalanceContainer container = balanceMapper.snapshotToDomain(blanked);

        assertThat(container.getExternalTotalEquity()).isNull();
        assertThat(container.getExternalAdjustedEquity()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("U21.17 — пустота вместо снапшота у любого перехода группы даёт пустоту")
    void u21_17_emptinessInIsEmptinessOutEverywhere() {
        assertThat(orderMapper.snapshotToDomain((OrderExternalSnapshot) null)).isNull();
        assertThat(orderMapper.snapshotToDomain((AttachedAlgoOrderExternalSnapshot) null)).isNull();
        assertThat(algoOrderMapper.snapshotToDomain(null)).isNull();
        assertThat(positionMapper.snapshotToDomain(null)).isNull();
        assertThat(candleMapper.snapshotToDomain(null)).isNull();
        assertThat(cashFlowMapper.snapshotToDomain(null)).isNull();
        assertThat(balanceMapper.snapshotToDomain(null)).isNull();
    }
}
