package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.core.fill.external_snapshot.FillExternalSnapshot;
import com.example.tradingbot.integration.model.okx.response.FillOkxResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг OKX fill response → FillExternalSnapshot. Числовые поля
 * парсятся, ts → Instant через {@link OkxResponseConverter}. См.
 * docs/models/integrations/okx/FillOkxResponse.md,
 * docs/models/mapping/TradeFill.md.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = OkxResponseConverter.class)
public interface FillMapper {

    @Mapping(target = "externalTradeId", source = "tradeId")
    @Mapping(target = "externalOrderId", source = "ordId")
    @Mapping(target = "internalOrderId", source = "clOrdId")
    @Mapping(target = "externalBillId", source = "billId")
    @Mapping(target = "fillPrice", source = "fillPx")
    @Mapping(target = "fillSize", source = "fillSz")
    @Mapping(target = "feeCurrency", source = "feeCcy")
    @Mapping(target = "timestamp", source = "ts")
    FillExternalSnapshot integrationToSnapshot(FillOkxResponse response);
}
