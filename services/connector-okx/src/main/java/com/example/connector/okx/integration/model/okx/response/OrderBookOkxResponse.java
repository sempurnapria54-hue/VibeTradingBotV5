package com.example.connector.okx.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ответ OKX на чтение книги заявок
 * ({@code docs/integrations/okx/contracts/order-book.md}).
 *
 * <p><b>Уровень приходит массивом строк</b>, а не объектом:
 * {@code [цена, объём, "0", число заявок]}. Третий элемент — устаревшее
 * поле площадки, всегда {@code "0"}; разбор его пропускает, а не хранит.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBookOkxResponse {

    /** Уровни продажи. */
    private List<List<String>> asks;

    /** Уровни покупки. */
    private List<List<String>> bids;

    /** Время книги у площадки, миллисекунды эпохи. */
    private String ts;
}
