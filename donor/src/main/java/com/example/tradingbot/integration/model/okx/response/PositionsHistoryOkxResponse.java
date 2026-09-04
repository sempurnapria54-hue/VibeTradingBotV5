package com.example.tradingbot.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ответ OKX по истории позиций (GET /account/positions-history).
 * За adapter не выходит; нормализуется маппером в
 * {@code PositionCloseResultExternalSnapshot}. Состав ограничен полями с
 * названным потребителем — инвентарь used/unused держит
 * docs/models/integrations/okx/PositionsHistoryOkxResponse.md.
 *
 * <p>{@code cTime}/{@code uTime} несут явный {@link JsonProperty} по той
 * же причине, что и у ответа по живой позиции: Lombok-аксессоры
 * {@code getcTime()} дают Jackson 3 имя свойства, не матчащее ключ.
 */
@Getter
@Setter
public class PositionsHistoryOkxResponse {

    /** Биржевой id позиции — половина оси адресации записи. */
    private String posId;

    /** Биржевой идентификатор инструмента записи — операнд структурной валидации. */
    private String instId;

    /** Направление закрытой позиции (long/short) — резолвится в доменное в слое интеграции. */
    private String direction;

    /** Готовый net realized P&amp;L, посчитанный биржей. */
    private String realizedPnl;

    /** Валюта, в которой посчитан net. */
    private String ccy;

    /** Средняя цена фактического выхода. */
    private String closeAvgPx;

    /** Реализованный P&amp;L до издержек. */
    private String pnl;

    /** Знаковая комиссионная компонента (сырой знак). */
    private String fee;

    /** Накопленный funding закрытой позиции (сырой знак источника). */
    private String fundingFee;

    /** Ликвидационный штраф (сырой знак). */
    private String liqPenalty;

    /** Тип последнего закрытия (1..6). */
    private String type;

    /** Время создания записи (epoch ms). */
    @JsonProperty("cTime")
    private String cTime;

    /** Время обновления записи (epoch ms) — ось окна и пагинации. */
    @JsonProperty("uTime")
    private String uTime;
}
