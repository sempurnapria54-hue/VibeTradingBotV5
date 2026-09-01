package com.example.tradingbot.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ответ OKX по standalone algo-order (GET /trade/order-algo,
 * orders-algo-pending/history). За adapter не выходит; нормализуется в
 * AlgoOrderExternalSnapshot (плоские поля → дерево condition). См.
 * docs/models/mapping/AlgoOrder.md.
 *
 * <p>{@code cTime}/{@code uTime} несут явный {@link JsonProperty}: Lombok
 * (beanspec) даёт аксессоры {@code getcTime()}/{@code getuTime()}, чьё
 * выводимое имя свойства Jackson 3 (дефолт RestClient в SB4) НЕ матчит с
 * ключом → поле биндилось в null (находка F4). Явное имя фиксирует бинд.
 */
@Getter
@Setter
public class AlgoOrderOkxResponse {

    /** stable client id. */
    private String algoClOrdId;

    /** Биржевой algo id. */
    private String algoId;

    /** Raw статус (live/pause/effective/...). */
    private String state;

    /** Внешний код ошибки. */
    private String failCode;

    /** Диагностика отказа (в колонку не садится, идёт в лог). */
    private String failReason;

    /** Объявленный размер записи — операнд покрытия у материализованной встроенной защиты. */
    private String sz;

    /** Фактический размер срабатывания. */
    private String actualSz;

    /** Фактическая цена срабатывания. */
    private String actualPx;

    /** Время срабатывания (epoch ms). */
    private String triggerTime;

    /** Связанные ordinary order ids. */
    private List<String> ordIdList;

    /** SL trigger price. */
    private String slTriggerPx;

    /** Тип цены SL trigger (last/index/mark). */
    private String slTriggerPxType;

    /** TP trigger price. */
    private String tpTriggerPx;

    /** Тип цены TP trigger. */
    private String tpTriggerPxType;

    /** Цена активации trailing. */
    private String activePx;

    /** Текущее значение trailing. */
    private String moveTriggerPx;

    /** Время создания (epoch ms). */
    @JsonProperty("cTime")
    private String cTime;

    /** Время обновления (epoch ms). */
    @JsonProperty("uTime")
    private String uTime;
}
