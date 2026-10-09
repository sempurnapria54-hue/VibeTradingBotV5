package com.example.tradingcore.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingcore.config.TradeFeeRateSyncProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingcore.persistence.service.TradeFeeRateDataService;
import com.example.tradingcore.util.Constants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Синк ставок комиссии счетов
 * (docs/models/domain/other/TradeFeeRate.md) и детектор их несвежести
 * (docs/rules/instrument-hold.md §«Несвежесть ставки комиссии»).
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
 *
 * <p><b>Детектор несвежести — здесь же, и радиус он не назначает, а
 * выводит.</b> После записи наблюдений счёта каждый инструмент контура
 * проверяется по свежести ставки СВОЕЙ группы: группа, пропавшая из
 * ответа, устаревает одна, а тип, чей вызов не прошёл, — всеми группами
 * сразу. Так оба режима отказа из таблицы дома дают свой радиус одним
 * операндом. Сам вопрос «несвежа ли ставка» задаётся модели
 * ({@link TradeFeeRate#isStaleAt}): детектор передаёт ей момент проверки и
 * порог конфигурации, а предиката своего не держит.
 *
 * <p><b>Ступень поднимается через общий исполнитель блокировки, а не
 * мимо ребра подъёма.</b> Мягкий сигнал наследует анкер, доминирование
 * биржевой ступени, отчёт по каждому инструменту и факт подъёма; писатель
 * ступени вне ребра взял бы всё это на себя и выпал бы из числа подъёмов
 * (docs/components/HoldService.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeFeeRateSyncService {

    private final ExchangeAccountDataService accountDataService;
    private final InstrumentDataService instrumentDataService;
    private final InstrumentExternalRulesDataService rulesDataService;
    private final TradeFeeRateDataService feeRateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;
    private final HoldService holdService;
    private final TradeFeeRateSyncProperties properties;

    /**
     * Проходит по торгующим счетам и типам инструментов проекции каталога,
     * затем проверяет свежесть ставок контура счёта.
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
            holdStaleRates(account);
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

    /**
     * Детектор несвежести по контуру счёта. Строка ставки читается один раз
     * на группу, а не на инструмент: инструментов группы много, строка у
     * неё одна.
     *
     * <p><b>Контур обходится постранично и ЦЕЛИКОМ</b> — весь каталог
     * площадки в проекции; страница есть единица чтения и пачка навесов
     * правил, а не предел. Прежнее окно отрезало хвост каталога по ключу, и
     * одни и те же инструменты за его краем не проверялись никогда. Раскладка
     * «группа → ставка» общая на все страницы: группа читается один раз за
     * тик, на какой бы странице ни встретился её инструмент.
     *
     * <p><b>Инструмент, чья группа не наблюдалась вовсе, сюда не
     * попадает:</b> несвежего числа у него нет, а действие с пустой ставкой
     * отвергает преконтроль кодом {@code FEE_RATE_UNAVAILABLE}
     * (docs/models/domain/other/TradeFeeRate.md §«Аксессор, который читают
     * спеки»). Отказ детектора синк не роняет — записанное остаётся
     * записанным.
     */
    private void holdStaleRates(ExchangeAccount account) {
        try {
            OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
            Map<List<String>, Optional<TradeFeeRate>> currentByGroup = new HashMap<>();
            instrumentDataService.forEachContourPage(account.getExchangeCode(), properties.getContourPageSize(),
                    page -> holdStaleRatesOf(account, page, checkedAt, currentByGroup));
        } catch (RuntimeException failure) {
            log.error("Trade fee rate staleness check failed for account={}", account.getInternalId(), failure);
        }
    }

    /**
     * Одна страница контура: навесы правил её инструментов — одной пачкой,
     * ставка группы — из общей на тик раскладки.
     */
    private void holdStaleRatesOf(ExchangeAccount account, List<Instrument> page, OffsetDateTime checkedAt,
                                  Map<List<String>, Optional<TradeFeeRate>> currentByGroup) {
        Map<Long, InstrumentExternalRules> rules = rulesDataService.findByInstrumentIds(page.stream()
                .map(Instrument::getId)
                .collect(Collectors.toList()));
        for (Instrument instrument : page) {
            InstrumentExternalRules instrumentRules = rules.get(instrument.getId());
            if (isNull(instrumentRules) || isBlank(instrumentRules.getExternalInstrumentType())
                    || isBlank(instrumentRules.getExternalFeeGroupId())) {
                continue;
            }
            String instrumentType = instrumentRules.getExternalInstrumentType();
            String feeGroupId = instrumentRules.getExternalFeeGroupId();
            Optional<TradeFeeRate> current = currentByGroup.computeIfAbsent(
                    List.of(instrumentType, feeGroupId),
                    group -> feeRateDataService.findCurrent(account.getId(), instrumentType, feeGroupId));
            if (current.isPresent()
                    && isTrue(current.get().isStaleAt(checkedAt, properties.getFreshnessThreshold()))) {
                holdEntries(account, instrument, current.get());
            }
        }
    }

    /**
     * Мягкая ступень пары «счёт, инструмент» через общий исполнитель
     * блокировки. Повтор каждым тиком, пока ставка несвежа, безопасен:
     * ступень поглощает анкер, отчёт — ключ состояния в окне наблюдения.
     *
     * <p>Отказ одного инструмента обход не обрывает: остальные несвежие
     * пары этого тика обязаны получить свою ступень.
     */
    private void holdEntries(ExchangeAccount account, Instrument instrument, TradeFeeRate rate) {
        log.warn("Trade fee rate is stale account={} instrument={} feeGroup={} confirmedAt={}",
                account.getInternalId(), instrument.getExternalId(), rate.getExternalFeeGroupId(),
                rate.getExternalModifiedAt());
        try {
            holdService.raise(HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_FEE_RATE_STALE),
                    DealContext.builder()
                            .exchangeAccount(account)
                            .instrument(instrument)
                            .externalObservation(observedRate(instrument, rate))
                            .build());
        } catch (RuntimeException failure) {
            log.error("Stale fee rate hold failed account={} instrument={}",
                    account.getInternalId(), instrument.getExternalId(), failure);
        }
    }

    /**
     * Внешний снимок отчёта: ставка группы, какой её видел детектор, и
     * порог, против которого она признана несвежей. Позиции и заявки
     * инструмента к этому основанию отношения не имеют, и площадку за ними
     * отчёт не читает.
     */
    private Map<String, Object> observedRate(Instrument instrument, TradeFeeRate rate) {
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("instrumentExternalId", instrument.getExternalId());
        observed.put("externalInstrumentType", rate.getExternalInstrumentType());
        observed.put("externalFeeGroupId", rate.getExternalFeeGroupId());
        observed.put("externalTakerFeeRate", rate.getExternalTakerFeeRate());
        observed.put("externalModifiedAt", String.valueOf(rate.getExternalModifiedAt()));
        observed.put("refreshCount", rate.getRefreshCount());
        observed.put("freshnessThreshold", String.valueOf(properties.getFreshnessThreshold()));
        return observed;
    }
}
