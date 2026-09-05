package com.example.connector.okx.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ответ OKX на чтение цен индексов
 * ({@code docs/integrations/okx/contracts/index-data.md}).
 *
 * <p>Чтение агрегатное по расчётной валюте: индексов на порядок меньше,
 * чем инструментов, и одним-двумя запросами покрывается весь листинг.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexTickerOkxResponse {

    /** Индекс, например {@code BTC-USDT}. */
    private String instId;

    /** Цена индекса. */
    private String idxPx;

    /** Время у площадки, миллисекунды эпохи. */
    private String ts;
}
