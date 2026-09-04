package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Сырая bill-запись движения средств OKX (элемент data ответов
 * /account/bills и /account/bills-archive). За adapter не выходит;
 * нормализуется в DealCashFlowExternalSnapshot. Состав used-полей —
 * инвентарь docs/models/integrations/okx/AccountBillOkxResponse.md;
 * контракт эндпоинтов — docs/integrations/okx/contracts/account-bills.md.
 */
@Getter
@Setter
public class AccountBillOkxResponse {

    /** Id записи; якорь пагинации (after/before) и ключ дедупа. */
    private String billId;

    /** Тип bill-записи (справочник источника). */
    private String type;

    /** Подтип bill-записи; funding: 173 расход / 174 доход. */
    private String subType;

    /** Время bill-события (Unix ms). */
    private String ts;

    /** Изменение баланса (знаковое). */
    private String balChg;

    /**
     * Изменение маржи позиции (знаковое). Несущий факт isolated-маржи:
     * расчёт финансирования ложится сюда при balChg=0 (прогон AG1.7
     * 2026-09-02 — posBalChg равен fundingFee записи закрытия до
     * последнего знака), а у торговых записей здесь идёт перевод маржи.
     */
    private String posBalChg;

    /** Комиссионная компонента записи (знаковая: минус — комиссия, плюс — ребейт). */
    private String fee;

    /** Валюта движения. */
    private String ccy;

    /** Id ордера, если движение связано с ордером. */
    private String ordId;

    /** Инструмент движения (ось предиката линковки). */
    private String instId;
}
