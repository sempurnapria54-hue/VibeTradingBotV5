package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ответ OKX по позиции (GET /account/positions). За adapter не
 * выходит; нормализуется маппером в PositionExternalSnapshot. instId/
 * posSide/mgnMode/lever — adapter validation, в snapshot не маппятся.
 * См. docs/models/mapping/Position.md.
 */
@Getter
@Setter
public class OkxPositionResponse {

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
    private String cTime;

    /** Время обновления (epoch ms). */
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
