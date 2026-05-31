package com.example.tradingbot.integration.service;

import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import java.util.List;

/**
 * Граница интеграции с биржей / adapter-layer: сервисы ходят на биржу
 * только через него, наружу выходят только нормализованные
 * {@code *ExternalSnapshot} (docs/components/ClientService.md,
 * docs/rules/raw-exchange-dto-boundary.md).
 *
 * <p>Nullable contract read/refresh: snapshot найден → snapshot;
 * успешно, но не найден → {@code null}; ошибка API/parse/invariant →
 * exception. Маршрутизация по биржам (multi-exchange) — за пределами
 * шага 1 (одна биржа OKX).
 */
public interface IntegrationService {

    /**
     * Спецификация инструмента → нормализованный снапшот.
     *
     * @return снапшот; {@code null} — инструмент на бирже не найден.
     */
    InstrumentExternalSnapshot getInstrument(String externalInstrumentId, String externalInstrumentType);

    /**
     * История свечей (пагинация назад).
     *
     * @param afterMillis свечи строго старше этого ts (ms); {@code null} — с самых свежих.
     * @return снапшоты свечей (пустой список — данных нет / достигнуто начало истории).
     */
    List<CandleExternalSnapshot> getHistoryCandles(String externalInstrumentId, String externalBar,
                                                   Long afterMillis, Integer limit);

    /**
     * Последние свечи (докачка хвоста).
     *
     * @return снапшоты свечей (пустой список — данных нет).
     */
    List<CandleExternalSnapshot> getLatestCandles(String externalInstrumentId, String externalBar, Integer limit);
}
