package com.example.tradingbot.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.other.external_snapshot.DealCashFlowExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.integration.model.okx.response.AccountBillOkxResponse;
import com.example.tradingbot.persistence.model.deal.DealCashFlowEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Маппинг строки разбивки domain ↔ persistence: енумы (категория,
 * состояние курса, таймфрейм свечи курса) ходят строкой по имени и
 * возвращаются без потерь; пустые опциональные поля остаются пустыми
 * (пустота нулём не подменяется — docs/models/domain/other/DealCashFlow.md).
 */
class DealCashFlowMappingTest {

    private final DealCashFlowMapper mapper = buildMapper();

    @Test
    void roundTripKeepsEnumsAndValues() {
        DealCashFlow flow = new DealCashFlow();
        flow.setDealId(7L);
        flow.setCategory(DealCashFlow.CashFlowCategory.FUNDING);
        flow.setAmount(new BigDecimal("-0.0158"));
        flow.setExternalFee(new BigDecimal("-0.001"));
        flow.setCcy("USDT");
        flow.setAppliedRate(new BigDecimal("2380.93"));
        flow.setRateStatus(DealCashFlow.RateStatus.APPLIED);
        flow.setAppliedRateCandleInstrument("ETH-USDT");
        flow.setAppliedRateCandleTimeframe(TimeFrame.ONE_MINUTE);
        flow.setAppliedRateCandleOpenTime(OffsetDateTime.of(2026, 9, 2, 16, 0, 0, 0, ZoneOffset.UTC));
        flow.setExchangeId(1L);
        flow.setExternalInstrumentId("ETH-USDT-SWAP");
        flow.setExternalBillId("bill-1");
        flow.setExternalType("8");
        flow.setExternalSubType("173");
        flow.setExternalOrderId("ord-9");
        flow.setExternalCreatedAt(OffsetDateTime.of(2026, 9, 2, 16, 0, 1, 0, ZoneOffset.UTC));

        DealCashFlowEntity entity = mapper.domainToPersistence(flow);
        assertThat(entity.getCategory()).isEqualTo("FUNDING");
        assertThat(entity.getRateStatus()).isEqualTo("APPLIED");
        assertThat(entity.getAppliedRateCandleTimeframe()).isEqualTo("ONE_MINUTE");

        DealCashFlow restored = mapper.persistenceToDomain(entity);
        assertThat(restored).usingRecursiveComparison().isEqualTo(flow);
    }

    @Test
    void emptyOptionalFieldsStayEmpty() {
        DealCashFlow flow = new DealCashFlow();
        flow.setCategory(DealCashFlow.CashFlowCategory.OTHER);
        flow.setAmount(BigDecimal.ONE);
        flow.setCcy("USDT");
        flow.setRateStatus(DealCashFlow.RateStatus.NOT_REQUIRED);
        flow.setExchangeId(1L);
        flow.setExternalBillId("bill-2");
        flow.setExternalType("290");

        DealCashFlow restored = mapper.persistenceToDomain(mapper.domainToPersistence(flow));
        assertThat(restored.getExternalFee()).isNull();
        assertThat(restored.getDealId()).isNull();
        assertThat(restored.getAppliedRate()).isNull();
        assertThat(restored.getAppliedRateCandleTimeframe()).isNull();
        assertThat(restored.getExternalSubType()).isNull();
    }

    @Test
    void rawBillParsesIntoSnapshot() {
        AccountBillOkxResponse bill = new AccountBillOkxResponse();
        bill.setBillId("bill-9");
        bill.setType("8");
        bill.setSubType("173");
        bill.setTs("1788364800786");
        bill.setBalChg("0");
        bill.setPosBalChg("0.0103373078764316");
        bill.setFee("");
        bill.setCcy("USDT");
        bill.setOrdId("");
        bill.setInstId("ETH-USDT-SWAP");

        DealCashFlowExternalSnapshot snapshot = mapper.integrationToSnapshot(bill);

        assertThat(snapshot.getExternalBillId()).isEqualTo("bill-9");
        assertThat(snapshot.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getPositionBalanceChange())
                .as("isolated-финансирование: расчёт лежит в марже позиции при нулевом балансе")
                .isEqualByComparingTo(new BigDecimal("0.0103373078764316"));
        assertThat(snapshot.getExternalFee()).as("пустая строка комиссии не подменяется нулём").isNull();
        assertThat(snapshot.getExternalCreatedAt())
                .isEqualTo(OffsetDateTime.of(2026, 9, 2, 16, 0, 0, 786_000_000, ZoneOffset.UTC));
        assertThat(snapshot.getExternalType()).isEqualTo("8");
        assertThat(snapshot.getExternalSubType()).isEqualTo("173");
        assertThat(snapshot.getExternalInstrumentId()).isEqualTo("ETH-USDT-SWAP");

        DealCashFlow domain = mapper.snapshotToDomain(snapshot);
        assertThat(domain.getPositionBalanceChange()).isEqualByComparingTo(new BigDecimal("0.0103373078764316"));
        assertThat(domain.getCategory()).as("категорию маппер не резолвит — её пишет вызывающий").isNull();
        assertThat(domain.getExchangeId()).isNull();
        assertThat(domain.getRateStatus()).isNull();
    }

    private DealCashFlowMapper buildMapper() {
        DealCashFlowMapperImpl impl = new DealCashFlowMapperImpl();
        ReflectionTestUtils.setField(impl, "okxResponseConverter", new OkxResponseConverter());
        return impl;
    }
}
