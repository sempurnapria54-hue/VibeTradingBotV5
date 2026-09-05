package com.example.connector.okx.snapshot;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот ставки комиссионной группы — выход
 * маппера из client-модели биржи до материализации {@code TradeFeeRate}.
 * Один ответ источника даёт N снапшотов, по числу групп в ответе. Сырой
 * OKX DTO за IntegrationService не выходит. См.
 * docs/models/mapping/TradeFeeRate.md.
 *
 * <p><b>Знак источника снят уже здесь:</b> ниже маппинга ставка есть
 * издержка (комиссия положительна, ребейт отрицателен), и {@code abs} в
 * формулах не появляется.
 */
@Value
@Builder
public class TradeFeeRateExternalSnapshot {

    /** Ось группы: сырой тип инструмента (OKX instType). */
    String externalInstrumentType;

    /** Ось группы: сырой идентификатор комиссионной группы (OKX feeGroup[].groupId). */
    String externalFeeGroupId;

    /** Ставка taker как издержка (OKX feeGroup[].taker со снятым знаком). */
    String externalTakerFeeRate;

    /** Ставка maker как издержка (OKX feeGroup[].maker со снятым знаком). */
    String externalMakerFeeRate;

    /** Комиссионный уровень счёта (OKX level) — датчик оси тира. */
    String externalFeeLevel;

    /** Время данных источника (OKX ts). */
    OffsetDateTime externalModifiedAt;
}
