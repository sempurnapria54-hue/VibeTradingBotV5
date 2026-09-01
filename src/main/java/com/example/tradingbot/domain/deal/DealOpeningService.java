package com.example.tradingbot.domain.deal;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.DealTrancheDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Атомарно создаёт {@link Deal} и остаётся единственным писателем этой
 * строки. Торгового решения не принимает: на входной тропе его принял
 * {@code EntryScannerJob} проверкой условия входа.
 *
 * <p>{@code entryReason} сервис ставит САМ, значением своей тропы, и
 * снаружи его не принимает: ярлык, приходящий параметром, мог бы
 * разойтись с тропой, а на нём стои́т признак «позиция по сделке
 * наблюдалась» — расхождение пропустило бы сделку с наблюдённой
 * позицией как сделку без неё.
 *
 * <p>Биржевой момент создания сервис принимает готовым и на биржу за ним
 * НЕ ходит: якорь биржевого времени добывает вызывающий. Поле несёт
 * вторую нагрузку — служит нижней границей окна линковки движений, пока
 * колонка границы пуста (docs/models/domain/aggregate/Deal.md).
 * См. docs/components/DealOpeningService.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealOpeningService {

    /** Порядковый номер эпизода у транша, материализованного при создании сделки. */
    private static final int FIRST_EPISODE_SEQ = 1;

    private final DealDataService dealDataService;
    private final DealTrancheDataService dealTrancheDataService;

    /**
     * Входная тропа — вход по стратегии. Зовёт сканер входа, когда
     * сработало условие; передаёт уже выбранные данные, включая фазу, по
     * которой выбрана деталь, и биржевой момент создания.
     */
    @Transactional
    public Optional<Deal> openDeal(Long instrumentId, Long strategyDetailId, StrategyTradeDirection direction,
                                   Deal.EntryStepType entryStepType, MarketPhase.Type entryMarketPhase,
                                   OffsetDateTime externalCreatedAt) {
        if (isTrue(dealDataService.existsActiveByInstrumentId(instrumentId))) {
            log.debug("Active deal already exists for instrument {} — entry skipped", instrumentId);
            return Optional.empty();
        }
        Deal deal = newDeal(instrumentId, direction, Deal.EntryReason.STRATEGY, externalCreatedAt);
        deal.setStrategyDetailId(strategyDetailId);
        deal.setEntryStepType(entryStepType);
        deal.setEntryMarketPhase(entryMarketPhase);
        Deal saved = dealDataService.save(deal);
        materializeTranche(saved, DealTranche.Status.PRECHECK);
        return Optional.of(saved);
    }

    /**
     * Восстановительная тропа — сделка вокруг уже живого риска,
     * созданного вне приложения. Деталь не закрепляется: выбора входа не
     * было, а на инструменте без активной стратегии выбирать не из чего.
     * Статусные ворота входной тропы здесь не стоят: риск уже живой, и
     * отказ завести сделку по заблокированному инструменту оставил бы
     * его вне модели.
     *
     * <p>Биржевой момент создания несёт время ОТКРЫТИЯ наблюдённой
     * позиции: своей входной заявки такая сделка не отправит никогда,
     * колонка нижней границы окна останется пуста навсегда, и это поле —
     * единственный операнд границы.
     */
    @Transactional
    public Optional<Deal> recoverDeal(Long instrumentId, StrategyTradeDirection direction,
                                      OffsetDateTime positionOpenedAt) {
        if (isTrue(dealDataService.existsActiveByInstrumentId(instrumentId))) {
            log.debug("Instrument {} is already explained by an active deal — recovery skipped", instrumentId);
            return Optional.empty();
        }
        Deal deal = newDeal(instrumentId, direction, Deal.EntryReason.RECOVERY, positionOpenedAt);
        Deal saved = dealDataService.save(deal);
        materializeTranche(saved, DealTranche.Status.MANAGING);
        return Optional.of(saved);
    }

    private Deal newDeal(Long instrumentId, StrategyTradeDirection direction, Deal.EntryReason entryReason,
                         OffsetDateTime externalCreatedAt) {
        Deal deal = new Deal();
        deal.setInternalId(ClientIdGenerator.generate());
        deal.setInstrumentId(instrumentId);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setDirection(direction);
        deal.setEntryReason(entryReason);
        deal.setExternalCreatedAt(externalCreatedAt);
        return deal;
    }

    /**
     * Материализация транша при создании сделки. Эагерна: транш, чей
     * вход так и не сработает, закроется истёкшим условием, а «уровень
     * объявлен и ждёт» обязано быть видно в данных. Восстановленный
     * транш заводится сразу в сопровождении — объявления у него нет, и
     * штатные рёбра входа ему недостижимы.
     */
    private void materializeTranche(Deal deal, DealTranche.Status status) {
        DealTranche tranche = new DealTranche();
        tranche.setInternalId(ClientIdGenerator.generate());
        tranche.setDealId(deal.getId());
        tranche.setStatus(status);
        tranche.setEpisodeSeq(FIRST_EPISODE_SEQ);
        DealTranche saved = dealTrancheDataService.save(tranche);
        if (nonNull(deal.getTranches())) {
            deal.getTranches().add(saved);
        }
    }
}
