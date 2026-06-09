package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.mapping.MarketDataConfigWriter;
import com.example.tradingbot.persistence.model.marketdata.IndicatorConfigEntity;
import com.example.tradingbot.persistence.repository.IndicatorConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реестр конфигураций индикатора: резолв config_id по идентичности
 * считаемого (тип + timeframe + canonical-params) с lookup-or-insert.
 * Канонизация params — раз при регистрации (не на каждом результате).
 * Гонка двух вставок снимается re-lookup'ом по UNIQUE-конфликту. См.
 * docs/decisions/market-data-result-identity-keying.md.
 */
@Service
@RequiredArgsConstructor
public class IndicatorConfigDataService {

    private final IndicatorConfigRepository repository;
    private final MarketDataConfigWriter configWriter;

    /** config_id конфигурации (создаёт строку реестра при первом запросе). timeframe — из params. */
    @Transactional
    public Long resolveConfigId(IndicatorValue.Type type, IndicatorParams params) {
        String typeName = type.name();
        String timeframe = params.getTimeframe().name();
        String canonical = configWriter.canonicalIndicatorParams(params);
        return repository.findByIndicatorTypeAndTimeframeAndParamsCanonical(typeName, timeframe, canonical)
                .map(IndicatorConfigEntity::getId)
                .orElseGet(() -> insert(typeName, timeframe, canonical));
    }

    private Long insert(String type, String timeframe, String canonical) {
        try {
            IndicatorConfigEntity entity = new IndicatorConfigEntity();
            entity.setIndicatorType(type);
            entity.setTimeframe(timeframe);
            entity.setParamsCanonical(canonical);
            return repository.saveAndFlush(entity).getId();
        } catch (DataIntegrityViolationException e) {
            return repository.findByIndicatorTypeAndTimeframeAndParamsCanonical(type, timeframe, canonical)
                    .map(IndicatorConfigEntity::getId)
                    .orElseThrow(() -> e);
        }
    }
}
