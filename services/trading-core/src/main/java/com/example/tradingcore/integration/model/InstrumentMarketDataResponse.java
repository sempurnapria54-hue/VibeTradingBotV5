package com.example.tradingcore.integration.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Строка каталога в ответе {@code market-data} — сырая форма соседа.
 *
 * <p>Справочных правил здесь нет: владелец отдаёт их отдельным ресурсом,
 * потому что навес может быть ещё не материализован, и «правил нет»
 * отличается от «правила пусты». Тик синка читает их вторым вызовом.
 *
 * <p>Свечей, групп сбора и производных в проекции не бывает вовсе — их
 * ядро читает у владельца по месту
 * (docs/models/domain/core/Instrument.md §«Проекция у торгового ядра»).
 */
@Getter
@Setter
@NoArgsConstructor
public class InstrumentMarketDataResponse {

    /** Идентичность инструмента у владельца каталога. */
    private String internalId;

    /** Код площадки. */
    private String exchangeCode;

    /** Идентификатор инструмента на площадке. */
    private String externalId;

    /** Тип инструмента на площадке. */
    private String externalType;

    /** Статус онбординга инструмента у владельца каталога. */
    private String status;

    /** Расчётная валюта: в ней считается риск. */
    private String externalSettlementCurrency;

    /** Базовая валюта пары. */
    private String externalBaseCurrency;

    /** Котировочная валюта пары. */
    private String externalQuoteCurrency;
}
