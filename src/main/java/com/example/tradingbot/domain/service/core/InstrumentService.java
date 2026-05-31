package com.example.tradingbot.domain.service.core;

import static java.util.Objects.isNull;

import com.example.tradingbot.config.CandleLoadingProperties;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.mapping.TimeFrameMapper;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Онбординг инструмента (docs/lifecycles/Instrument.md): операции
 * переходов CREATED → SYNC → CANDLES_LOADING → ACTIVE. Каждый метод
 * выполняет один переход; автономного драйвера последовательности нет
 * — владелец оркестрации Instrument.Status открыт (ORCH-Q1). Готовность
 * групп свечей ведёт CandleJob. Снаружи инструмент адресуется по
 * internalId; числовые id — внутренняя деталь связей.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService {

    /** Разделитель internalId группы свечей: {instrumentInternalId}:{TimeFrame}. */
    private static final String CANDLE_GROUP_INTERNAL_ID_SEPARATOR = ":";

    private final InstrumentDataService instrumentDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final ExchangeDataService exchangeDataService;
    private final IntegrationService integrationService;
    private final InstrumentMapper instrumentMapper;
    private final TimeFrameMapper timeFrameMapper;
    private final CandleLoadingProperties properties;

    /** Заводит инструмент в статусе CREATED; биржа резолвится по exchangeInternalId. */
    public Instrument create(Instrument instrument, String exchangeInternalId) {
        instrument.setExchangeId(exchangeDataService.getRequiredIdByInternalId(exchangeInternalId));
        instrument.setStatus(Instrument.Status.CREATED);
        if (isNull(instrument.getPlannedCandleStartDate())) {
            instrument.setPlannedCandleStartDate(properties.getDefaultPlannedStartDate());
        }
        return instrumentDataService.save(instrument);
    }

    public Instrument getRequiredByInternalId(String internalId) {
        return instrumentDataService.getRequiredByInternalId(internalId);
    }

    /**
     * CREATED → SYNC: тянет спецификацию у биржи и обновляет домен из
     * снапшота (идентичность + биржевые externalStatus/externalLeverage).
     * Снапшот не найден → ERROR.
     */
    public Instrument synchronizeSpecification(String internalId) {
        Instrument instrument = instrumentDataService.getRequiredByInternalId(internalId);
        instrument.setStatus(Instrument.Status.SYNC);
        InstrumentExternalSnapshot snapshot =
                integrationService.getInstrument(instrument.getExternalId(), instrument.getExternalType());
        if (isNull(snapshot)) {
            log.error("Instrument spec not found on exchange: instId={}", instrument.getExternalId());
            instrument.setStatus(Instrument.Status.ERROR);
            return instrumentDataService.save(instrument);
        }
        instrumentMapper.snapshotToDomain(instrument, snapshot);
        return instrumentDataService.save(instrument);
    }

    /** SYNC → CANDLES_LOADING: заводит группы свечей под конфиг-таймфреймы. */
    public Instrument startCandlesLoading(String internalId) {
        Instrument instrument = instrumentDataService.getRequiredByInternalId(internalId);
        properties.getTimeframes().forEach(timeframe -> ensureCandleGroup(instrument, timeframe));
        instrument.setStatus(Instrument.Status.CANDLES_LOADING);
        return instrumentDataService.save(instrument);
    }

    /**
     * CANDLES_LOADING → ACTIVE, когда все группы свечей достигли ACTIVE.
     * Инструмент грузится вместе с группами (join fetch); проверки
     * статуса и готовности — на самой модели (rich-модель).
     */
    public Instrument evaluateReadiness(Long instrumentId) {
        Instrument instrument = instrumentDataService.getRequiredByIdWithCandleGroups(instrumentId);
        if (instrument.isCandleLoading() && instrument.isReadyForActivation()) {
            instrument.setStatus(Instrument.Status.ACTIVE);
            return instrumentDataService.save(instrument);
        }
        return instrument;
    }

    public List<Instrument> findLoadingInstruments() {
        return instrumentDataService.findByStatus(Instrument.Status.CANDLES_LOADING);
    }

    public List<CandleGroup> getCandleGroups(String internalId) {
        Instrument instrument = instrumentDataService.getRequiredByInternalId(internalId);
        return candleGroupDataService.findByInstrumentId(instrument.getId());
    }

    private void ensureCandleGroup(Instrument instrument, TimeFrame timeframe) {
        if (candleGroupDataService.findByInstrumentIdAndTimeframe(instrument.getId(), timeframe).isPresent()) {
            return;
        }
        CandleGroup group = new CandleGroup();
        group.setInternalId(instrument.getInternalId() + CANDLE_GROUP_INTERNAL_ID_SEPARATOR + timeframe.name());
        group.setInstrumentId(instrument.getId());
        group.setTimeframe(timeframe);
        group.setExternalTimeframe(timeFrameMapper.domainToOkx(timeframe));
        group.setStatus(CandleGroup.Status.CREATED);
        group.setCount(0L);
        candleGroupDataService.save(group);
    }
}
