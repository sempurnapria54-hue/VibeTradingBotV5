package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.config.CandleLoadingProperties;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.mapping.TimeFrameMapper;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Онбординг инструмента (docs/lifecycles/Instrument.md): операции
 * переходов CREATED → SYNC → CANDLES_LOADING → ACTIVE. Каждый метод
 * выполняет один переход; автономного драйвера последовательности нет
 * — владелец оркестрации Instrument.Status открыт (ORCH-Q1). Готовность
 * групп свечей ведёт CandleJob.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentDataService instrumentDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final ClientService clientService;
    private final InstrumentMapper instrumentMapper;
    private final TimeFrameMapper timeFrameMapper;
    private final CandleLoadingProperties properties;

    /** Заводит инструмент в статусе CREATED. */
    public Instrument create(Instrument instrument) {
        instrument.setStatus(Instrument.Status.CREATED);
        if (Objects.isNull(instrument.getPlannedCandleStartDate())) {
            instrument.setPlannedCandleStartDate(properties.getDefaultPlannedStartDate());
        }
        return instrumentDataService.save(instrument);
    }

    /**
     * CREATED → SYNC: тянет спецификацию у биржи и обновляет домен из
     * снапшота (идентичность + биржевые externalStatus/externalLeverage).
     * Снапшот не найден → ERROR.
     */
    public Instrument synchronizeSpecification(Long instrumentId) {
        Instrument instrument = getRequiredById(instrumentId);
        instrument.setStatus(Instrument.Status.SYNC);
        InstrumentExternalSnapshot snapshot =
                clientService.getInstrument(instrument.getExternalId(), instrument.getExternalType());
        if (Objects.isNull(snapshot)) {
            log.error("Instrument spec not found on exchange: instId={}", instrument.getExternalId());
            instrument.setStatus(Instrument.Status.ERROR);
            return instrumentDataService.save(instrument);
        }
        instrumentMapper.updateFromSnapshot(instrument, snapshot);
        return instrumentDataService.save(instrument);
    }

    /** SYNC → CANDLES_LOADING: заводит группы свечей под конфиг-таймфреймы. */
    public Instrument startCandlesLoading(Long instrumentId) {
        Instrument instrument = getRequiredById(instrumentId);
        properties.getTimeframes().forEach(timeframe -> ensureCandleGroup(instrumentId, timeframe));
        instrument.setStatus(Instrument.Status.CANDLES_LOADING);
        return instrumentDataService.save(instrument);
    }

    /** CANDLES_LOADING → ACTIVE, когда все группы свечей достигли ACTIVE. */
    public Instrument evaluateReadiness(Long instrumentId) {
        Instrument instrument = getRequiredById(instrumentId);
        if (Objects.equals(instrument.getStatus(), Instrument.Status.CANDLES_LOADING)) {
            List<CandleGroup> groups = candleGroupDataService.findByInstrumentId(instrumentId);
            if (instrument.isReadyForActivation(groups)) {
                instrument.setStatus(Instrument.Status.ACTIVE);
                return instrumentDataService.save(instrument);
            }
        }
        return instrument;
    }

    public List<Instrument> findLoadingInstruments() {
        return instrumentDataService.findByStatus(Instrument.Status.CANDLES_LOADING);
    }

    public List<CandleGroup> getCandleGroups(Long instrumentId) {
        return candleGroupDataService.findByInstrumentId(instrumentId);
    }

    public Instrument getRequiredById(Long id) {
        return instrumentDataService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }

    private void ensureCandleGroup(Long instrumentId, TimeFrame timeframe) {
        if (candleGroupDataService.findByInstrumentIdAndTimeframe(instrumentId, timeframe).isPresent()) {
            return;
        }
        CandleGroup group = new CandleGroup();
        group.setInstrumentId(instrumentId);
        group.setTimeframe(timeframe);
        group.setExternalTimeframe(timeFrameMapper.domainToOkxClient(timeframe));
        group.setStatus(CandleGroup.Status.CREATED);
        group.setCount(0L);
        candleGroupDataService.save(group);
    }
}
