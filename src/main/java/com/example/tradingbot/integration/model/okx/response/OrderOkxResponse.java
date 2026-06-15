package com.example.tradingbot.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ответ OKX по ordinary order (GET /trade/order, orders-pending/
 * history). За IntegrationService/adapter не выходит
 * (docs/rules/raw-exchange-dto-boundary.md). Все скаляры — строки OKX;
 * нормализация (empty→null, ms→время) — в маппере. См.
 * docs/models/mapping/Order.md.
 *
 * <p>{@code cTime}/{@code uTime} несут явный {@link JsonProperty}: Lombok
 * (beanspec) даёт аксессоры {@code getcTime()}/{@code getuTime()}, чьё
 * выводимое имя свойства Jackson 3 (дефолт RestClient в SB4) НЕ матчит с
 * ключом → поле биндилось в null (находка F4). Явное имя фиксирует бинд.
 */
@Getter
@Setter
public class OrderOkxResponse {

    /** stable client id. */
    private String clOrdId;

    /** Биржевой id ордера. */
    private String ordId;

    /** Тип ордера OKX (limit/market/...). */
    private String ordType;

    /** Сторона (buy/sell). */
    private String side;

    /** Raw статус OKX (live/partially_filled/filled/canceled/...). */
    private String state;

    /** Цена. */
    private String px;

    /** Размер. */
    private String sz;

    /** Исполнено накопленно. */
    private String accFillSz;

    /** Средняя цена исполнения. */
    private String avgPx;

    /** Комиссия. */
    private String fee;

    /** Время создания (epoch ms). */
    @JsonProperty("cTime")
    private String cTime;

    /** Время обновления (epoch ms). */
    @JsonProperty("uTime")
    private String uTime;

    /** Top-level attached client id. */
    private String attachAlgoClOrdId;

    /** Top-level TP trigger price. */
    private String tpTriggerPx;

    /** Top-level SL trigger price. */
    private String slTriggerPx;

    /** Embedded attached protection. */
    private List<AttachAlgoOrdOkxResponse> attachAlgoOrds;
}
