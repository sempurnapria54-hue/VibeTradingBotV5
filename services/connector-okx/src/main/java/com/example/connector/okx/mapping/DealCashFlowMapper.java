package com.example.connector.okx.mapping;

import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.connector.okx.snapshot.DealCashFlowExternalSnapshot;
import com.example.connector.okx.integration.model.okx.response.AccountBillOkxResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг движения средств по слоям (docs/models/mapping/DealCashFlow.md):
 * сырая bill-запись OKX → граничный {@link DealCashFlowExternalSnapshot},
 * снапшот → {@link DealCashFlow}, domain ↔ persistence. Енумы хранятся
 * строкой и конвертируются по имени. Категорию, биржу, курс и ссылку на
 * сделку маппер не проставляет — их производит вызывающий
 * (§«Что проставляет вызывающий»); резолв категории — интерпретация
 * факта, маппер доменных решений не принимает.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = OkxResponseConverter.class)
public interface DealCashFlowMapper {

    /** Сырая bill-запись → граничный снапшот; пустые строки числовых полей → null. */
    @Mapping(target = "externalBillId", source = "billId")
    @Mapping(target = "amount", source = "balChg")
    @Mapping(target = "positionBalanceChange", source = "posBalChg")
    @Mapping(target = "externalFee", source = "fee")
    @Mapping(target = "externalType", source = "type")
    @Mapping(target = "externalSubType", source = "subType")
    @Mapping(target = "externalOrderId", source = "ordId")
    @Mapping(target = "externalInstrumentId", source = "instId")
    @Mapping(target = "externalCreatedAt", source = "ts")
    DealCashFlowExternalSnapshot integrationToSnapshot(AccountBillOkxResponse response);

    /** Снапшот → домен: только факты ответа источника, остальное проставляет вызывающий. */
    DealCashFlow snapshotToDomain(DealCashFlowExternalSnapshot snapshot);
}
