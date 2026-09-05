package com.example.connector.okx.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ответ OKX по позиции (GET /account/positions). За adapter не
 * выходит; нормализуется маппером в PositionExternalSnapshot.
 * posSide/mgnMode/lever — adapter validation, в snapshot не маппятся;
 * instId маппится — срез по множеству инструментов иначе не адресуем.
 * См. docs/models/mapping/Position.md.
 *
 * <p>{@code cTime}/{@code uTime} несут явный {@link JsonProperty}: Lombok
 * (beanspec) даёт аксессоры {@code getcTime()}/{@code getuTime()}, чьё
 * выводимое имя свойства Jackson 3 (дефолт RestClient в SB4) НЕ матчит с
 * ключом → поле биндилось в null (находка F4). Явное имя фиксирует бинд.
 */
@Getter
@Setter
public class PositionOkxResponse {

    /** Биржевой id позиции. */
    private String posId;

    /** Размер позиции со знаком (>0 long, <0 short). */
    private String pos;

    /** Средняя цена входа. */
    private String avgPx;

    /** Mark price. */
    private String markPx;

    /** Цена ликвидации. */
    private String liqPx;

    /** Маржа позиции. */
    private String margin;

    /** Нереализованный PnL. */
    private String upl;

    /** Время создания (epoch ms). */
    @JsonProperty("cTime")
    private String cTime;

    /** Время обновления (epoch ms). */
    @JsonProperty("uTime")
    private String uTime;

    /** Инструмент (adapter validation). */
    private String instId;

    /** Тип инструмента (adapter validation). */
    private String instType;

    /** Сторона позиции (adapter validation: net). */
    private String posSide;

    /** Режим маржи (adapter validation: isolated). */
    private String mgnMode;

    /** Плечо (adapter validation: ≤ максимум). */
    private String lever;
}
