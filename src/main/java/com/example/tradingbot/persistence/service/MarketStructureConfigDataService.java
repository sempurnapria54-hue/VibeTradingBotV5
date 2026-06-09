package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.mapping.MarketDataConfigWriter;
import com.example.tradingbot.persistence.model.marketdata.MarketStructureConfigEntity;
import com.example.tradingbot.persistence.repository.MarketStructureConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реестр конфигураций структуры рынка: резолв config_id по идентичности
 * считаемого (timeframe + canonical-params) с lookup-or-insert. Вид
 * расчёта структуры один (тип — выход), type-дискриминатора в
 * идентичности нет. Гонка двух вставок снимается re-lookup'ом. См.
 * docs/decisions/market-data-result-identity-keying.md.
 */
@Service
@RequiredArgsConstructor
public class MarketStructureConfigDataService {

    private final MarketStructureConfigRepository repository;
    private final MarketDataConfigWriter configWriter;

    /** config_id конфигурации (создаёт строку реестра при первом запросе). */
    @Transactional
    public Long resolveConfigId(TimeFrame timeframe, MarketStructureParams params) {
        String timeframeName = timeframe.name();
        String canonical = configWriter.canonicalStructureParams(params);
        return repository.findByTimeframeAndParamsCanonical(timeframeName, canonical)
                .map(MarketStructureConfigEntity::getId)
                .orElseGet(() -> insert(timeframeName, canonical));
    }

    private Long insert(String timeframe, String canonical) {
        try {
            MarketStructureConfigEntity entity = new MarketStructureConfigEntity();
            entity.setTimeframe(timeframe);
            entity.setParamsCanonical(canonical);
            return repository.saveAndFlush(entity).getId();
        } catch (DataIntegrityViolationException e) {
            return repository.findByTimeframeAndParamsCanonical(timeframe, canonical)
                    .map(MarketStructureConfigEntity::getId)
                    .orElseThrow(() -> e);
        }
    }
}
