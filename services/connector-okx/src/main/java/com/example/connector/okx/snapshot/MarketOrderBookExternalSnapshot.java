package com.example.connector.okx.snapshot;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Граничный снапшот книги заявок: ответ площадки, разобранный и
 * проверенный, но ещё не переведённый в общую модель.
 *
 * <p>Уровни здесь уже разобраны из массивов строк в величины: разбор —
 * работа границы, и дальше форма источника не едет
 * ({@code docs/rules/raw-exchange-dto-boundary.md}).
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketOrderBookExternalSnapshot {

    /** Идентификатор инструмента на площадке. */
    private String externalInstrumentId;

    /** Время книги у площадки, миллисекунды эпохи. */
    private Long externalTimestamp;

    /** Уровни покупки, от лучшего к худшему. */
    private List<OrderBookLevelExternalSnapshot> bids;

    /** Уровни продажи, от лучшего к худшему. */
    private List<OrderBookLevelExternalSnapshot> asks;
}
