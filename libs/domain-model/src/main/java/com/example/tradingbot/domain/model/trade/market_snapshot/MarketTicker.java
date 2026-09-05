package com.example.tradingbot.domain.model.trade.market_snapshot;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Срез цен инструмента на момент.
 *
 * <p>Дом модели — {@code docs/models/domain/other/MarketTicker.md}.
 *
 * <p><b>Это не {@code MarketPriceData}.</b> Тот — runtime-объект входа
 * расчёта «прямо сейчас», не хранится и полей аудита не имеет; этот —
 * ряд строк, история состояния рынка. Слить их значило бы либо навесить
 * хранение на runtime-объект, либо лишить ряд полей, которых у
 * runtime-объекта нет по построению.
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketTicker extends Auditable {

    /** Внутренний идентификатор среза. */
    private Long id;

    /** Инструмент, чьи цены сняты. */
    private Long instrumentId;

    /** Метка времени площадки; с {@code instrumentId} — ключ идентичности. */
    private Long externalTimestamp;

    /** Наша метка приёма. */
    private Long observedTimestamp;

    /** Последняя цена сделки. */
    private BigDecimal lastPrice;

    /** Объём за сутки в форме, которую отдаёт площадка. */
    private BigDecimal volume;

    /**
     * Марк-цена. Приходит НЕ из тикера — отдельным агрегатным чтением,
     * поэтому пустота законна и означает «это чтение не дошло».
     * Подстановка последней цены на её место запрещена: она молча меняет
     * ценовой домен ({@code docs/components/models/MarketPriceData.md}).
     */
    private BigDecimal markPrice;

    /** Цена индекса. Пустота — с тем же смыслом, что у марк-цены. */
    private BigDecimal indexPrice;
}
