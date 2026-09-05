package com.example.marketdata.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.marketdata.domain.model.IndicatorConfig;
import com.example.marketdata.domain.model.MarketStructureConfig;
import com.example.marketdata.mapping.ComputationParamsJsonConverter;
import com.example.marketdata.persistence.model.IndicatorConfigEntity;
import com.example.marketdata.persistence.model.MarketStructureConfigEntity;
import com.example.marketdata.persistence.repository.IndicatorConfigRepository;
import com.example.marketdata.persistence.repository.MarketStructureConfigRepository;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.util.InternalIdFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для реестров идентичностей вычисления.
 *
 * <p><b>Заведение идемпотентно по идентичности, а не по вызову.</b>
 * Повторное требование того же вычисления возвращает ту же строку: в этом
 * и состоит смысл реестра — «ATR(14) на 1H» считается один раз для всех,
 * кому нужен (docs/models/domain/other/IndicatorValue.md §«Ключевание —
 * идентичностью вычисления»).
 */
@Service
@RequiredArgsConstructor
public class ComputationConfigDataService {

    private final IndicatorConfigRepository indicatorConfigRepository;
    private final MarketStructureConfigRepository structureConfigRepository;
    private final ComputationParamsJsonConverter paramsConverter;

    /** Заводит идентичность индикатора либо возвращает уже заведённую. */
    @Transactional
    public IndicatorConfig ensureIndicatorConfig(IndicatorConfig config) {
        String canonical = paramsConverter.paramsToCanonical(config.getParams());
        return indicatorConfigRepository
                .findByIndicatorTypeAndTimeframeAndParamsCanonical(
                        config.getIndicatorType().name(), config.getTimeframe().name(), canonical)
                .map(this::toDomain)
                .orElseGet(() -> toDomain(indicatorConfigRepository.save(toEntity(config, canonical))));
    }

    @Transactional(readOnly = true)
    public Optional<IndicatorConfig> findIndicatorConfig(Long id) {
        return indicatorConfigRepository.findById(id).map(this::toDomain);
    }

    @Transactional(readOnly = true)
    public IndicatorConfig getRequiredIndicatorConfigByInternalId(String internalId) {
        return indicatorConfigRepository.findByInternalId(internalId)
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Indicator config not found: " + internalId));
    }

    @Transactional(readOnly = true)
    public List<IndicatorConfig> findAllIndicatorConfigs() {
        return indicatorConfigRepository.findAll().stream().map(this::toDomain).collect(toList());
    }

    /** Заводит идентичность структуры рынка либо возвращает уже заведённую. */
    @Transactional
    public MarketStructureConfig ensureMarketStructureConfig(MarketStructureConfig config) {
        String canonical = paramsConverter.paramsToCanonical(config.getParams());
        return structureConfigRepository
                .findByIdentity(config.getTimeframe().name(), canonical,
                        config.getEfficiencyRatioConfigId(), config.getAtrConfigId())
                .map(this::toDomain)
                .orElseGet(() -> toDomain(structureConfigRepository.save(toEntity(config, canonical))));
    }

    @Transactional(readOnly = true)
    public MarketStructureConfig getRequiredMarketStructureConfigByInternalId(String internalId) {
        return structureConfigRepository.findByInternalId(internalId)
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Market structure config not found: " + internalId));
    }

    @Transactional(readOnly = true)
    public List<MarketStructureConfig> findAllMarketStructureConfigs() {
        return structureConfigRepository.findAll().stream().map(this::toDomain).collect(toList());
    }

    private IndicatorConfigEntity toEntity(IndicatorConfig config, String canonical) {
        IndicatorConfigEntity entity = new IndicatorConfigEntity();
        entity.setInternalId(InternalIdFactory.forInternalEntity());
        entity.setIndicatorType(config.getIndicatorType().name());
        entity.setTimeframe(config.getTimeframe().name());
        entity.setParamsCanonical(canonical);
        entity.setParams(paramsConverter.paramsToJson(config.getParams()));
        return entity;
    }

    private MarketStructureConfigEntity toEntity(MarketStructureConfig config, String canonical) {
        MarketStructureConfigEntity entity = new MarketStructureConfigEntity();
        entity.setInternalId(InternalIdFactory.forInternalEntity());
        entity.setTimeframe(config.getTimeframe().name());
        entity.setParamsCanonical(canonical);
        entity.setParams(paramsConverter.paramsToJson(config.getParams()));
        entity.setEfficiencyRatioConfigId(config.getEfficiencyRatioConfigId());
        entity.setAtrConfigId(config.getAtrConfigId());
        return entity;
    }

    private IndicatorConfig toDomain(IndicatorConfigEntity entity) {
        IndicatorValue.Type indicatorType = IndicatorValue.Type.valueOf(entity.getIndicatorType());
        IndicatorConfig config = new IndicatorConfig();
        config.setId(entity.getId());
        config.setInternalId(entity.getInternalId());
        config.setIndicatorType(indicatorType);
        config.setTimeframe(TimeFrame.valueOf(entity.getTimeframe()));
        config.setParams(paramsConverter.jsonToIndicatorParams(entity.getParams(), indicatorType));
        return config;
    }

    private MarketStructureConfig toDomain(MarketStructureConfigEntity entity) {
        MarketStructureConfig config = new MarketStructureConfig();
        config.setId(entity.getId());
        config.setInternalId(entity.getInternalId());
        config.setTimeframe(TimeFrame.valueOf(entity.getTimeframe()));
        config.setParams(paramsConverter.jsonToMarketStructureParams(entity.getParams()));
        config.setEfficiencyRatioConfigId(entity.getEfficiencyRatioConfigId());
        config.setAtrConfigId(entity.getAtrConfigId());
        return config;
    }
}
