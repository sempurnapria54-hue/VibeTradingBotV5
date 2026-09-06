package com.example.tradingcore.persistence.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.mapping.InstrumentExternalRulesJsonConverter;
import com.example.tradingcore.persistence.repository.InstrumentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отдаёт справочные правила инструмента
 * (docs/components/InstrumentExternalRulesDataService.md). У площадки
 * правила сам не запрашивает: их кладёт синк проекции каталога.
 *
 * <p><b>Ставку в отдаваемый навес наливает этот сервис.</b> На навесе её
 * нет — там только ключ комиссионной группы, а значение живёт своей
 * строкой (docs/models/domain/other/TradeFeeRate.md). Обе тропы чтения
 * навеса — контекст расчёта и преконтроль риска — проходят через эту
 * границу, поэтому гидрирует она, а не каждый читатель: гидрация в
 * фабрике контекста расчёта накрыла бы только тропу калькуляторов, и
 * преконтроль блокировал бы каждый вход отсутствием ставки.
 *
 * <p><b>Счёт приходит аргументом, а не выводится из инструмента.</b>
 * Ставка ключуется биржевым СЧЁТОМ (там же §Персистентность), а
 * инструмент принадлежит площадке и счёта не знает: у одной площадки
 * счетов много, и их комиссионные уровни различны. Прежняя редакция дома
 * называла тройку «биржа, сырой тип, ключ группы» и обещала, что биржу
 * сервис резолвит через инструмент-владельца, — на ключе счёта это
 * неисполнимо.
 *
 * <p>Ставка не резолвится — аксессоры навеса отдают пустоту, и действие
 * блокирует преконтроль. Подставленного значения сервис не выдумывает:
 * заниженная ставка даёт заниженный прогноз комиссии, то есть свободнее
 * бюджет риска и позицию больше положенной.
 */
@Service
@RequiredArgsConstructor
public class InstrumentExternalRulesDataService {

    private final InstrumentRepository repository;
    private final InstrumentExternalRulesJsonConverter converter;
    private final TradeFeeRateDataService tradeFeeRateDataService;

    /**
     * Актуальные правила инструмента с гидрированной ставкой счёта;
     * пусто — строки проекции нет либо навес ещё не материализован.
     */
    @Transactional(readOnly = true)
    public Optional<InstrumentExternalRules> findByInstrumentId(Long instrumentId, Long exchangeAccountId) {
        Optional<InstrumentExternalRules> rules = repository.findById(instrumentId)
                .map(entity -> converter.jsonToRules(entity.getExternalRules()))
                .filter(carried -> nonNull(carried));
        rules.ifPresent(carried -> hydrateFeeRate(carried, exchangeAccountId));
        return rules;
    }

    /**
     * Ставка группы по тройке «счёт, сырой тип инструмента, ключ группы».
     * Ключа группы нет, счёт не назван либо группа не наблюдалась — поле
     * остаётся пустым, и потребитель отвергает действие: пустота нулём не
     * подменяется (docs/rules/absent-value-semantics.md).
     */
    private void hydrateFeeRate(InstrumentExternalRules rules, Long exchangeAccountId) {
        if (isNull(exchangeAccountId)
                || isBlank(rules.getExternalFeeGroupId())
                || isBlank(rules.getExternalInstrumentType())) {
            return;
        }
        tradeFeeRateDataService
                .findCurrent(exchangeAccountId, rules.getExternalInstrumentType(), rules.getExternalFeeGroupId())
                .ifPresent(rate -> rules.setExternalTakerFeeRate(rate.getExternalTakerFeeRate()));
    }
}
