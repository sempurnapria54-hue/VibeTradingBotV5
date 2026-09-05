package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.marketdata.config.ConnectorProperties;
import com.example.marketdata.integration.ExchangeReadClient;
import com.example.marketdata.mapping.InstrumentMapper;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.marketdata.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.util.InternalIdFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ведёт каталог инструментов площадки: заводит новые из листинга,
 * обновляет спецификацию уже заведённых, синхронизирует справочный навес
 * и держит статус готовности данных.
 *
 * <p><b>Каталог наполняет ЛИСТИНГ, а не человек.</b> Полнота листинга
 * нужна советнику по построению и требованием потребителя не выражается —
 * это конфигурация сервиса
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»). Ручного заведения инструмента поэтому нет.
 *
 * <p><b>Готовность инструмента считается по его группам, и групп может
 * не быть.</b> Инструмент без единой заказанной группы остаётся в
 * {@code SYNC}: спецификация известна, собирать нечего. Это не дефект
 * онбординга, а следствие того, что группу заводит требование
 * (docs/lifecycles/Instrument.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentCatalogService {

    /** Статусы, из которых готовность ещё может измениться. */
    private static final Set<Instrument.Status> NOT_READY_STATUSES = Set.of(
            Instrument.Status.CREATED,
            Instrument.Status.SYNC,
            Instrument.Status.CANDLES_LOADING);

    private final ExchangeReadClient readClient;
    private final InstrumentDataService instrumentDataService;
    private final InstrumentExternalRulesDataService rulesDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentMapper instrumentMapper;
    private final ConnectorProperties connectorProperties;

    /**
     * Сводит каталог с листингом площадки по типу инструмента.
     *
     * @return число заведённых и обновлённых строк.
     */
    public Integer synchronizeListing(String externalInstrumentType) {
        List<Instrument> listed = readClient.getInstruments(externalInstrumentType);
        if (isEmpty(listed)) {
            log.warn("Exchange listing is empty for instType={}", externalInstrumentType);
            return 0;
        }
        listed.forEach(this::upsert);
        return listed.size();
    }

    /** Обновляет справочный навес правил одного инструмента. */
    public void synchronizeRules(Instrument instrument) {
        InstrumentExternalRules rules = readClient.getInstrumentRules(
                instrument.getExternalId(), instrument.getExternalType());
        if (isNull(rules)) {
            log.warn("Instrument rules not found on exchange: externalId={} id={}",
                    instrument.getExternalId(), instrument.getId());
            return;
        }
        rulesDataService.save(instrument.getId(), rules);
    }

    /**
     * Пересчитывает готовность у всех, у кого она могла измениться.
     *
     * <p><b>Популяция двусторонняя, и односторонней ей быть нельзя.</b>
     * Инструмент, чьи группы только что дошли до готовности, в выборке
     * «есть незавершённая группа» уже не значится — и без второй половины
     * остался бы в {@code CANDLES_LOADING} навсегда. Обратная половина —
     * готовый инструмент, которому заказали новую группу.
     */
    public void refreshReadiness() {
        Set<Long> candidates = new HashSet<>(candleGroupDataService.findInstrumentIdsWithUnreadyGroups());
        instrumentDataService.findByStatusIn(NOT_READY_STATUSES)
                .forEach(instrument -> candidates.add(instrument.getId()));
        for (Long instrumentId : candidates) {
            try {
                evaluateReadiness(instrumentId);
            } catch (RuntimeException e) {
                log.error("Instrument readiness evaluation failed for {}", instrumentId, e);
            }
        }
    }

    /**
     * Пересчитывает готовность инструмента по его группам: все группы
     * готовы — {@code ACTIVE}, есть незавершённая — {@code CANDLES_LOADING},
     * групп нет — {@code SYNC}.
     */
    public Instrument evaluateReadiness(Long instrumentId) {
        Instrument instrument = instrumentDataService.getRequiredById(instrumentId);
        List<CandleGroup> groups = candleGroupDataService.findByInstrumentId(instrumentId);
        Instrument.Status target = resolveReadiness(groups);
        if (instrument.getStatus() == target) {
            return instrument;
        }
        instrument.setStatus(target);
        return instrumentDataService.saveSpecification(instrument);
    }

    private Instrument.Status resolveReadiness(List<CandleGroup> groups) {
        if (isEmpty(groups)) {
            return Instrument.Status.SYNC;
        }
        boolean allActive = groups.stream().allMatch(group -> isTrue(group.isActive()));
        return allActive ? Instrument.Status.ACTIVE : Instrument.Status.CANDLES_LOADING;
    }

    private void upsert(Instrument listed) {
        Optional<Instrument> stored = instrumentDataService.findByExternalId(
                connectorProperties.getExchangeCode(), listed.getExternalId());
        if (stored.isPresent()) {
            Instrument instrument = stored.get();
            instrumentMapper.updateFromListing(listed, instrument);
            instrumentDataService.saveSpecification(instrument);
            return;
        }
        listed.setInternalId(InternalIdFactory.forInternalEntity());
        listed.setExchangeCode(connectorProperties.getExchangeCode());
        listed.setStatus(Instrument.Status.SYNC);
        instrumentDataService.save(listed);
    }
}
