package com.example.connector.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.connector.okx.credentials.ExchangeCredentialsResolver;
import com.example.connector.okx.gateway.OkxExchangeGateway;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.BalanceContainerMapper;
import com.example.connector.okx.mapping.CandleMapper;
import com.example.connector.okx.mapping.CandleMapperImpl;
import com.example.connector.okx.mapping.DealCashFlowMapper;
import com.example.connector.okx.mapping.InstrumentExternalRulesMapper;
import com.example.connector.okx.mapping.InstrumentMapper;
import com.example.connector.okx.mapping.MarketPriceDataMapper;
import com.example.connector.okx.mapping.MarketSnapshotMapper;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.mapping.TimeFrameMapper;
import com.example.connector.okx.mapping.TimeFrameMapperImpl;
import com.example.connector.okx.mapping.TradeFeeRateMapper;
import com.example.connector.okx.snapshot.CandleExternalSnapshot;
import com.example.connector.okx.source.OkxSourceReader;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Наружу уходят только ЗАКРЫТЫЕ бары.
 *
 * <p>Признак закрытия виден только на границе: доменная свеча его не
 * несёт (docs/models/domain/other/Candle.md). Пропусти незакрытый бар — и
 * читатель запишет его в историю неотличимым от закрытого, а всякий
 * расчёт по этой истории получит look-ahead. Ошибка была бы в разрешающую
 * сторону, а такие запрещены (docs/concept.md, П1).
 *
 * <p>Здесь же проверяется, что таймфрейм переводится в словарь площадки
 * на этой границе: читатель называет его доменным перечнем и строк
 * площадки не знает (.claude/rules/codestyle.md §Слои).
 */
class ClosedCandleBoundaryTest {

    private static final String INSTRUMENT = "BTC-USDT-SWAP";

    private final OkxSourceReader reader = mock(OkxSourceReader.class);
    private final CandleMapper candleMapper = new CandleMapperImpl();
    private final TimeFrameMapper timeFrameMapper = new TimeFrameMapperImpl();

    private final OkxExchangeGateway gateway = new OkxExchangeGateway(
            reader, mock(ExchangeCredentialsResolver.class),
            mock(OrderMapper.class), mock(AlgoOrderMapper.class), mock(PositionMapper.class),
            mock(InstrumentMapper.class), mock(InstrumentExternalRulesMapper.class),
            mock(BalanceContainerMapper.class), candleMapper, timeFrameMapper,
            mock(DealCashFlowMapper.class), mock(TradeFeeRateMapper.class),
            mock(MarketPriceDataMapper.class), mock(MarketSnapshotMapper.class));

    /** Незакрытый бар в ответ не попадает — ни в последних свечах, ни в истории. */
    @Test
    void openBarNeverLeavesTheBoundary() {
        when(reader.getLatestCandles(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(snapshot(1L, true), snapshot(2L, false)));
        when(reader.getHistoryCandles(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of(snapshot(1L, true), snapshot(2L, false)));

        List<Candle> latest = gateway.getLatestCandles(INSTRUMENT, TimeFrame.ONE_HOUR, 100);
        List<Candle> history = gateway.getHistoryCandles(INSTRUMENT, TimeFrame.ONE_HOUR, 0L, 100);

        assertThat(latest).extracting(Candle::getOpenTimestamp).containsExactly(1L);
        assertThat(history).extracting(Candle::getOpenTimestamp).containsExactly(1L);
    }

    /** Доменный таймфрейм переводится в бар площадки здесь, а не у читателя. */
    @Test
    void timeframeIsTranslatedAtTheBoundary() {
        when(reader.getLatestCandles(INSTRUMENT, "1Dutc", 10)).thenReturn(List.of(snapshot(1L, true)));

        assertThat(gateway.getLatestCandles(INSTRUMENT, TimeFrame.ONE_DAY, 10)).hasSize(1);
    }

    private CandleExternalSnapshot snapshot(Long openTimestamp, Boolean confirm) {
        return CandleExternalSnapshot.builder()
                .openTimestamp(openTimestamp)
                .open(BigDecimal.ONE)
                .high(BigDecimal.ONE)
                .low(BigDecimal.ONE)
                .close(BigDecimal.ONE)
                .confirm(confirm)
                .build();
    }
}
