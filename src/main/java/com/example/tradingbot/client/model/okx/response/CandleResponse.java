package com.example.tradingbot.client.model.okx.response;

import java.util.List;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Нативный DTO свечи OKX. На проводе свеча — позиционный массив из 9
 * строк {@code [ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]}
 * (docs/models/integrations/okx/OkxCandleResponse.md). Adapter
 * разбирает массив в именованные поля через {@link #of(List)} с
 * валидацией длины (структурная валидация client-слоя). Числа — сырые
 * строки; конвертация в BigDecimal/boolean — на маппинге в snapshot.
 */
@Getter
public class CandleResponse {

    private static final int EXPECTED_LENGTH = 9;
    private static final int TS_INDEX = 0;
    private static final int OPEN_INDEX = 1;
    private static final int HIGH_INDEX = 2;
    private static final int LOW_INDEX = 3;
    private static final int CLOSE_INDEX = 4;
    private static final int VOLUME_INDEX = 5;
    private static final int VOLUME_CURRENCY_INDEX = 6;
    private static final int VOLUME_CURRENCY_QUOTE_INDEX = 7;
    private static final int CONFIRM_INDEX = 8;

    /** Время открытия свечи, epoch millis (ts). */
    private final String ts;

    /** Цена открытия (o). */
    private final String open;

    /** Максимум за интервал (h). */
    private final String high;

    /** Минимум за интервал (l). */
    private final String low;

    /** Цена закрытия (c). */
    private final String close;

    /** Объём (vol). */
    private final String volume;

    /** Объём в базовой валюте (volCcy) — доменно не используется. */
    private final String volumeCurrency;

    /** Объём в котируемой валюте (volCcyQuote) — доменно не используется. */
    private final String volumeCurrencyQuote;

    /** Признак закрытия: "0" — не закрыта, "1" — закрыта (confirm). */
    private final String confirm;

    private CandleResponse(List<String> raw) {
        this.ts = raw.get(TS_INDEX);
        this.open = raw.get(OPEN_INDEX);
        this.high = raw.get(HIGH_INDEX);
        this.low = raw.get(LOW_INDEX);
        this.close = raw.get(CLOSE_INDEX);
        this.volume = raw.get(VOLUME_INDEX);
        this.volumeCurrency = raw.get(VOLUME_CURRENCY_INDEX);
        this.volumeCurrencyQuote = raw.get(VOLUME_CURRENCY_QUOTE_INDEX);
        this.confirm = raw.get(CONFIRM_INDEX);
    }

    /**
     * Разбирает позиционный массив OKX в именованный DTO. Длина строго
     * 9 — защита от несоответствия формату источника.
     */
    public static CandleResponse of(List<String> raw) {
        if (CollectionUtils.isEmpty(raw) || raw.size() != EXPECTED_LENGTH) {
            throw new IllegalArgumentException(
                    "OKX candle array must have exactly " + EXPECTED_LENGTH + " elements, got: "
                            + (CollectionUtils.isEmpty(raw) ? 0 : raw.size()));
        }
        return new CandleResponse(raw);
    }
}
