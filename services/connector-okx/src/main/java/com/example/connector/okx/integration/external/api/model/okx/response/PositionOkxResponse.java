package com.example.connector.okx.integration.external.api.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ответ OKX по позиции (GET /account/positions). За adapter не
 * выходит; нормализуется маппером в PositionExternalSnapshot.
 * instType/posSide/lever не читаются и не сверяются: посылки, которые
 * они выражают, меряет преконтроль перед действием, а не ответ чтения;
 * instId маппится — срез по множеству инструментов иначе не адресуем.
 * mgnMode переносится доменным режимом маржи, но не сверяется: он операнд
 * ОТБОРА записи контура при живом чтении по инструменту и различитель
 * записей в счёт-широком срезе — площадка держит изолированную и
 * кросс-позицию одного инструмента рядом отдельными записями.
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

    /** Инструмент — адрес снапшота в срезе по множеству инструментов. */
    private String instId;

    /** Тип инструмента — не читается (параметр запроса среза). */
    private String instType;

    /** Сторона позиции — не читается и не сверяется. */
    private String posSide;

    /**
     * Режим маржи записи (isolated / cross) — отбор записи контура и
     * различитель записей в срезе; иное значение — отказ чтения.
     */
    private String mgnMode;

    /** Плечо — не читается и не сверяется. */
    private String lever;
}
