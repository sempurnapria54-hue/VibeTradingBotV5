package com.example.connector.okx.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ответ OKX на чтение марк-цен
 * ({@code docs/integrations/okx/contracts/mark-price.md}).
 *
 * <p>Чтение агрегатное: по типу инструмента отдаётся строка на каждый
 * инструмент листинга.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarkPriceOkxResponse {

    /** Инструмент. */
    private String instId;

    /** Марк-цена. */
    private String markPx;

    /** Время у площадки, миллисекунды эпохи. */
    private String ts;
}
