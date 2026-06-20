package com.example.tradingbot.domain.service.market;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_price.external_snapshot.MarketPriceDataExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.MarketPriceDataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Отдаёт {@link MarketPriceData} — runtime-данные текущих цен инструмента
 * (last/bid/ask + время тикера). Не persisted, историю тикеров не ведёт,
 * кэш на первом этапе не использует: цена нужна прямо перед расчётом
 * параметров действия и получается по REST ticker через
 * {@link IntegrationService}. См. docs/components/MarketPriceDataService.md.
 */
@Service
@RequiredArgsConstructor
public class MarketPriceDataService {

    private final IntegrationService integrationService;
    private final MarketPriceDataMapper mapper;

    /** Текущие цены инструмента; {@code null} — тикера на бирже нет. */
    public MarketPriceData getMarketPriceData(Long instrumentId, String externalInstrumentId) {
        MarketPriceDataExternalSnapshot snapshot = integrationService.getMarketPriceData(externalInstrumentId);
        if (isNull(snapshot)) {
            return null;
        }
        return mapper.snapshotToDomain(snapshot, instrumentId);
    }
}
