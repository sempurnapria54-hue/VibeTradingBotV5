package com.example.tradingbot.client.model.okx.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionResponse {

    // -------------------- Идентификация и режимы --------------------

    /**
     * Тип инструмента: MARGIN | SWAP | FUTURES | OPTION.
     */
    private String instType;

    /**
     * Id инструмента (например ETH-USDT-SWAP).
     */
    private String instId;

    /**
     * Режим маржи: cross (кросс) или isolated (изолированная).
     */
    private String mgnMode;

    /**
     * Id позиции.
     * Важно: после полного закрытия posId живёт ограниченное время (в доке: 30 дней),
     * потом позиция/posId «очищаются».
     */
    private String posId;

    /**
     * “Сторона позиции”: net или long/short (зависит от режима позиций).
     */
    private String posSide;

    // -------------------- Размер позиции --------------------

    /**
     * Размер позиции.
     * В net-режиме для деривативов знак может означать long/short; в long/short-режиме обычно положительное число.
     */
    private String pos;

    /**
     * Сколько можно закрыть прямо сейчас (актуально для margin/options).
     */
    private String availPos;

    /**
     * “Хеджирующий объём” (в основном про delta-neutral/особые режимы).
     */
    private String hedgedPos;

    // -------------------- Цены --------------------

    /**
     * Средняя цена входа.
     */
    private String avgPx;

    /**
     * “Средняя без влияния расчётов/settlement” (в доке: для cross-futures, где есть settlement).
     */
    private String nonSettleAvgPx;

    /**
     * Mark price (биржевая “контрольная” цена риска).
     */
    private String markPx;

    /**
     * Последняя цена сделки (last).
     */
    private String last;

    /**
     * Индексная цена (index).
     */
    private String idxPx;

    /**
     * “USD-цена” залоговой валюты (для деривативов/опционов, когда залог не USD).
     */
    private String usdPx;

    /**
     * Breakeven price (цена безубытка).
     */
    private String bePx;

    // -------------------- Unrealized PnL --------------------

    /**
     * Unrealized PnL по mark price (главное значение).
     */
    private String upl;

    /**
     * Unrealized PnL в относительном виде (доля/процент).
     */
    private String uplRatio;

    /**
     * Unrealized PnL по last price (в доке: “в основном для отображения”).
     */
    private String uplLastPx;

    /**
     * Unrealized PnL по last price (относительный).
     */
    private String uplRatioLastPx;

    // -------------------- Маржа, плечо, риск, ликвидация --------------------

    /**
     * Плечо (не для опционов и некоторых PM-режимов).
     */
    private String lever;

    /**
     * Расчётная цена ликвидации (“примерная/оценочная”).
     */
    private String liqPx;

    /**
     * Initial margin requirement (в доке: для cross).
     */
    private String imr;

    /**
     * Maintenance margin (сколько нужно держать, чтобы не ликвиднуло).
     */
    private String mmr;

    /**
     * Margin ratio (насколько близко к риску).
     */
    private String mgnRatio;

    /**
     * Сколько маржи “лежит в позиции” (в доке: для isolated можно увеличивать/уменьшать).
     */
    private String margin;

    /**
     * Номинал позиции в USD (размер позиции в деньгах).
     */
    private String notionalUsd;

    // -------------------- Реализованный PnL и комиссии --------------------

    /**
     * Реализованный PnL (в доке дана формула, что туда входит).
     */
    private String realizedPnl;

    /**
     * Settled PnL (в доке: для cross-futures при settlement).
     */
    private String settledPnl;

    /**
     * Суммарный PnL по закрывающим ордерам без комиссий.
     */
    private String pnl;

    /**
     * Суммарная комиссия (плюс — rebate, минус — списание).
     */
    private String fee;

    /**
     * Суммарный funding (актуально для SWAP).
     */
    private String fundingFee;

    /**
     * Штрафы при ликвидации (в доке: значение отрицательное).
     */
    private String liqPenalty;

    // -------------------- Обеспечение/долги --------------------

    /**
     * Валюта, в которой “занята маржа/обеспечение”.
     */
    private String ccy;

    /**
     * Проценты по долгу (для margin-режимов).
     */
    private String interest;

    /**
     * Долг (только margin).
     */
    private String liab;

    /**
     * Валюта долга (только margin).
     */
    private String liabCcy;

    /**
     * “Объём долга под ордера на закрытие” (в доке: для isolated margin).
     */
    private String pendingCloseOrdLiabVal;

    // -------------------- ADL --------------------

    /**
     * Шкала ADL (auto-deleveraging), 0..5: чем меньше, тем слабее сигнал ADL.
     */
    private String adl;

    // -------------------- Времена и последнее событие --------------------

    /**
     * Id последней сделки.
     */
    private String tradeId;

    /**
     * Время создания позиции (ms).
     */
    private String cTime;

    /**
     * Время последнего обновления позиции (ms).
     */
    private String uTime;

    // -------------------- TP/SL, привязанные к позиции --------------------

    /**
     * Список “стратегий закрытия” (появляется при определённых условиях).
     */
    private List<CloseOrderAlgo> closeOrderAlgo;

    // -------------------- Portfolio margin / spot offset --------------------

    /**
     * Поле про “spot-hedge/offset” в PM.
     */
    private String spotInUseAmt;

    /**
     * Валюта spot offset (PM).
     */
    private String spotInUseCcy;

    /**
     * Пользовательский лимит spot offset (PM).
     */
    private String clSpotInUseAmt;

    /**
     * Максимально возможный spot offset (PM).
     */
    private String maxSpotInUseAmt;

    // -------------------- Опционы (греки) --------------------

    /**
     * Стоимость позиции опциона.
     */
    private String optVal;

    /**
     * Delta опциона: BS (USD-база).
     */
    private String deltaBS;

    /**
     * Delta опциона: PA (coin-база).
     */
    private String deltaPA;

    /**
     * Gamma опциона: BS (USD-база).
     */
    private String gammaBS;

    /**
     * Gamma опциона: PA (coin-база).
     */
    private String gammaPA;

    /**
     * Theta опциона: BS (USD-база).
     */
    private String thetaBS;

    /**
     * Theta опциона: PA (coin-база).
     */
    private String thetaPA;

    /**
     * Vega опциона: BS (USD-база).
     */
    private String vegaBS;

    /**
     * Vega опциона: PA (coin-база).
     */
    private String vegaPA;

    // -------------------- Deprecated поля --------------------

    /**
     * Устаревшее поле для margin (deprecated).
     */
    private String baseBal;

    /**
     * Устаревшее поле для margin (deprecated).
     */
    private String quoteBal;

    /**
     * Устаревшее поле для margin (deprecated).
     */
    private String baseBorrowed;

    /**
     * Устаревшее поле для margin (deprecated).
     */
    private String quoteBorrowed;

    /**
     * Устаревшее поле для margin (deprecated).
     */
    private String baseInterest;

    /**
     * Устаревшее поле для margin (deprecated).
     */
    private String quoteInterest;

    // -------------------- Прочее --------------------

    /**
     * “Валюта позиции” (в основном для margin-позиций).
     */
    private String posCcy;

    /**
     * Внешняя бизнес-ссылка (например купоны/промо и т.п.).
     */
    private String bizRefId;

    /**
     * Тип внешней бизнес-ссылки.
     */
    private String bizRefType;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloseOrderAlgo {

        /**
         * Id algo-ордера.
         */
        private String algoId;

        /**
         * Цена-триггер SL.
         */
        private String slTriggerPx;

        /**
         * Тип цены для SL: last | index | mark.
         */
        private String slTriggerPxType;

        /**
         * Цена-триггер TP.
         */
        private String tpTriggerPx;

        /**
         * Тип цены для TP: last | index | mark.
         */
        private String tpTriggerPxType;

        /**
         * Доля закрытия при срабатывании (в доке: 1 = 100%).
         */
        private String closeFraction;
    }
}
