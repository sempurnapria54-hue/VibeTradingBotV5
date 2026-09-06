package com.example.tradingcore.domain.service;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.TradeFeeRateDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Синк ставок комиссии счетов
 * (docs/models/domain/other/TradeFeeRate.md).
 *
 * <p><b>Реестр живёт у владельца счёта, и это не удобство, а граница:</b>
 * чтение ставок — ПРИВАТНАЯ операция площадки, то есть требует ключей
 * счёта, а {@code market-data} ходит к площадке только публичными
 * чтениями (docs/architecture/contracts.md §«Синхронные вызовы»). На
 * навесе правил инструмента остаётся только КЛЮЧ комиссионной группы; сам
 * он приходит публичным чтением спецификации.
 *
 * <p><b>Вызов — один на пару «счёт, тип инструмента», а не на
 * инструмент.</b> Ставка есть атрибут комиссионной группы счёта, и N
 * вызовов на N инструментов размножили бы одно и то же значение. Перечень
 * типов читается ИЗ ДАННЫХ проекции каталога, а не хардкодится: контур
 * фазы 1 бессрочно-контрактный, но перечень от этого не становится
 * константой.
 *
 * <p><b>Отказ одной пары стоит одну пару.</b> Ставки соседних типов и
 * соседних счетов от него не зависят: связывать их значило бы делать
 * недоступность одного счёта причиной устаревания ставок другого.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeFeeRateSyncService {

    private final ExchangeAccountDataService accountDataService;
    private final InstrumentDataService instrumentDataService;
    private final TradeFeeRateDataService feeRateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;

    /**
     * Проходит по торгующим счетам и типам инструментов проекции каталога.
     *
     * @return число записанных наблюдений группы
     */
    public Integer synchronize() {
        List<String> instrumentTypes = instrumentDataService.findDistinctExternalTypes();
        if (instrumentTypes.isEmpty()) {
            log.debug("Trade fee rate sync skipped: instrument projection carries no instrument type");
            return 0;
        }
        int recorded = 0;
        for (ExchangeAccount account : accountDataService.findTradingAccounts()) {
            for (String instrumentType : instrumentTypes) {
                recorded += recordGroup(account, instrumentType);
            }
        }
        return recorded;
    }

    /**
     * Одна пара «счёт, тип инструмента». Счёт-владельца проставляет ядро:
     * числовые ключи баз границу сервиса не пересекают, и коннектор его
     * заполнить не может (docs/architecture/data-ownership.md
     * §Идентификаторы).
     */
    private int recordGroup(ExchangeAccount account, String instrumentType) {
        try {
            List<TradeFeeRate> observed =
                    exchangeOperationsClient.getTradeFeeRates(account.getInternalId(), instrumentType);
            observed.forEach(rate -> {
                rate.setExchangeAccountId(account.getId());
                feeRateDataService.record(rate);
            });
            return observed.size();
        } catch (RuntimeException failure) {
            log.error("Trade fee rate sync failed for account={} instType={}",
                    account.getInternalId(), instrumentType, failure);
            return 0;
        }
    }
}
