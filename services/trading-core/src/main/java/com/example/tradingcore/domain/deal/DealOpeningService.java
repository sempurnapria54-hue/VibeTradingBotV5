package com.example.tradingcore.domain.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.event.DealOpenedContent;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Атомарно создаёт {@link Deal} и остаётся единственным писателем этой
 * строки (docs/components/DealOpeningService.md).
 *
 * <p><b>Троп создания две:</b> вход по стратегии и восстановление уже
 * живого риска, созданного вне приложения. Торгового решения не принимает
 * ни на одной: на входной его принял сканер входа проверкой условия, на
 * восстановительной решать нечего — риск уже существует.
 *
 * <p><b>Причину заведения ставит САМ, значением своей тропы</b>, и
 * снаружи её не принимает: ярлык, пришедший параметром, мог бы разойтись
 * с тропой, а на нём стои́т предикат «позиция по сделке наблюдалась» —
 * расхождение пропустило бы сделку с наблюдённой позицией как сделку без
 * неё.
 *
 * <p><b>Счёт приходит параметром, а не резолвится здесь.</b> Резолвить
 * его было бы нечем: инструмент принадлежит площадке, и у двух счетов
 * одной площадки он один
 * (docs/architecture/tenant-and-exchange.md §«Торговая строка называет
 * счёт, и радиусы читаются от него»).
 *
 * <p><b>Биржевой момент создания принимает готовым и на биржу за ним не
 * ходит:</b> якорь биржевого времени добывает вызывающий. Поле несёт
 * вторую нагрузку — служит нижней границей окна линковки движений, пока
 * колонка границы пуста (docs/models/domain/aggregate/Deal.md).
 *
 * <p>Стратегию не ищет, деталь не выбирает, условий не проверяет,
 * заявок не создаёт, на биржу не ходит и FSM не запускает — ни на одной
 * из двух троп.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealOpeningService {

    /** Порядковый номер эпизода у транша, материализованного при создании сделки. */
    private static final Integer FIRST_EPISODE_SEQ = 1;

    /** Индекс первого экземпляра шаблона: смещение цены входа равно level × levelStep. */
    private static final Integer FIRST_LEVEL = 0;

    private final DealDataService dealDataService;
    private final DealTrancheDataService dealTrancheDataService;
    private final OutboxWriter outboxWriter;

    /**
     * Входная тропа — вход по стратегии. Зовёт сканер входа, когда
     * сработало условие; передаёт уже выбранные данные, включая фазу, по
     * которой выбрана деталь, и биржевой момент создания.
     *
     * <p><b>Деталь приходит целиком, а не одним идентификатором:</b>
     * транши материализуются ПО ЕЁ ОБЪЯВЛЕНИЯМ, и по идентификатору
     * сервису пришлось бы читать её второй раз тем же проходом.
     *
     * <p>Финальная защитная проверка — по паре «счёт, инструмент»: она
     * дублирует гейт сканера намеренно, потому что между отбором и
     * созданием проходит время, а инвариант базы стои́т на той же паре.
     */
    @Transactional
    public Optional<Deal> openDeal(ExchangeAccount account, Instrument instrument, StrategyDetail detail,
                                   StrategyTradeDirection direction, MarketPhase.Type entryMarketPhase,
                                   OffsetDateTime externalCreatedAt) {
        if (isTrue(dealDataService.existsActiveOnPair(account.getId(), instrument.getId()))) {
            log.debug("Active deal already exists on the pair accountId={} instrumentId={} — entry skipped",
                    account.getId(), instrument.getId());
            return Optional.empty();
        }
        Deal deal = newDeal(account.getId(), instrument.getId(), direction, Deal.EntryReason.STRATEGY,
                externalCreatedAt);
        deal.setStrategyDetailId(detail.getId());
        deal.setEntryMarketPhase(entryMarketPhase);
        Deal saved = dealDataService.save(deal);
        saved.setTranches(new ArrayList<>());
        materializeDeclaredTranches(saved, detail);
        publishOpened(account, instrument, saved);
        return Optional.of(saved);
    }

    /**
     * Восстановительная тропа — сделка вокруг уже живого риска, созданного
     * вне приложения. Зовёт поиск нарушений инвариантов тем же тиком,
     * которым нашёл активную позицию, не объяснимую ни одной сделкой:
     * <b>заведение сделки и есть реакция на эту аномалию</b>.
     *
     * <p><b>Радиус защитной проверки — пара, и это несущее:</b> проверка
     * по одному инструменту у тенанта с двумя счетами на одной площадке
     * объявила бы позицию счёта B объяснённой сделкой счёта A, то есть
     * промолчала бы на живом непонятом риске.
     *
     * <p><b>Статусные ворота входной тропы здесь не стоят:</b> риск уже
     * живой, и отказ завести сделку по заблокированному инструменту
     * оставил бы его вне модели — ровно тем зависшим живым риском,
     * которого не бывает.
     *
     * <p><b>Деталь не закрепляется:</b> выбора входа не было, а на
     * инструменте без активной стратегии выбирать не из чего.
     *
     * <p><b>Ступень сворачивания поднимает не этот сервис.</b> Экспозиция
     * транша производна от его заявок, а у восстановленного их нет —
     * поэтому сумма экспозиций расходится с нетто-размером живого эпизода
     * с первого же прохода, и реакцию поднимает инвариант экспозиции
     * (docs/models/domain/aggregate/Deal.md).
     *
     * <p><b>Эпизод позиции сервис не заводит:</b> строку эпизода
     * материализует добыча состояния ближайшим проходом уже созданной
     * сделки (docs/components/RefreshPositionExecutor.md).
     */
    @Transactional
    public Optional<Deal> recoverDeal(ExchangeAccount account, Instrument instrument,
                                      StrategyTradeDirection direction, OffsetDateTime positionOpenedAt) {
        if (isTrue(dealDataService.existsActiveOnPair(account.getId(), instrument.getId()))) {
            log.debug("The pair accountId={} instrumentId={} is already explained by an active deal"
                    + " — recovery skipped", account.getId(), instrument.getId());
            return Optional.empty();
        }
        Deal deal = newDeal(account.getId(), instrument.getId(), direction, Deal.EntryReason.RECOVERY,
                positionOpenedAt);
        Deal saved = dealDataService.save(deal);
        saved.setTranches(new ArrayList<>());
        // Объявления у восстановленного транша нет: ни ссылки, ни уровня,
        // ни типа входа — заводил его не выбор входа. Ведётся он
        // safety-тропой, и штатные рёбра входа ему недостижимы.
        materializeTranche(saved, DealTranche.Status.MANAGING, null, null, null);
        publishOpened(account, instrument, saved);
        return Optional.of(saved);
    }

    /**
     * Событие создания сделки — <b>той же транзакцией</b>, обе тропы
     * (docs/architecture/contracts.md §«У каждого класса события назван
     * писатель, и он же писатель решения»).
     *
     * <p>Счёт и инструмент приходят моделями, а не идентификаторами,
     * именно поэтому: наружу едет {@code internalId} — числовой ключ
     * границу сервиса не пересекает.
     */
    private void publishOpened(ExchangeAccount account, Instrument instrument, Deal deal) {
        outboxWriter.write(account.getTenantId(), CoreEventType.DEAL_OPENED,
                new DealOpenedContent(deal.getInternalId(), account.getInternalId(),
                        instrument.getInternalId(), deal.getEntryReason().name(),
                        String.valueOf(deal.getDirection())));
    }

    private Deal newDeal(Long exchangeAccountId, Long instrumentId, StrategyTradeDirection direction,
                         Deal.EntryReason entryReason, OffsetDateTime externalCreatedAt) {
        Deal deal = new Deal();
        deal.setInternalId(InternalIdFactory.forInternalEntity());
        deal.setExchangeAccountId(exchangeAccountId);
        deal.setInstrumentId(instrumentId);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setDirection(direction);
        deal.setEntryReason(entryReason);
        deal.setExternalCreatedAt(externalCreatedAt);
        return deal;
    }

    /**
     * Материализация траншей ПО ОБЪЯВЛЕНИЯМ детали: по одному на
     * объявление, по {@code levelCount} на шаблон.
     *
     * <p>Материализация <b>эагерна</b> — транш, чей вход так и не
     * сработает, закроется истёкшим условием, а «уровень объявлен и ждёт»
     * обязано быть видно в данных.
     */
    private void materializeDeclaredTranches(Deal deal, StrategyDetail detail) {
        for (StrategyTranche declaration : emptyIfNull(detail.getTranches())) {
            DealTranche.EntryStepType entryStepType = entryStepTypeOf(declaration);
            int count = declaration.materializedCount();
            for (int index = 0; index < count; index++) {
                // Уровень несёт ТОЛЬКО шаблон: у нешаблонного объявления
                // смещать нечего, и пустота колонки этим и означена.
                Integer level = count > 1 ? FIRST_LEVEL + index : null;
                materializeTranche(deal, DealTranche.Status.PRECHECK, declaration.getId(), level,
                        entryStepType);
            }
        }
    }

    /**
     * Тип входного шага объявления: у сетки входов столько же, сколько
     * уровней, поэтому поле живёт на транше, а не на сделке. Пусто —
     * объявление входа не несёт.
     */
    private DealTranche.EntryStepType entryStepTypeOf(StrategyTranche declaration) {
        StrategyStep entryStep = declaration.entrySteps().stream().findFirst().orElse(null);
        if (isNull(entryStep)) {
            return null;
        }
        return StrategyStepType.GRID_ENTRY.equals(entryStep.getStepType())
                ? DealTranche.EntryStepType.GRID_ENTRY
                : DealTranche.EntryStepType.ENTRY;
    }

    /** Одна строка транша, заведённая той же транзакцией, что и сделка. */
    private void materializeTranche(Deal deal, DealTranche.Status status, Long strategyTrancheId,
                                    Integer level, DealTranche.EntryStepType entryStepType) {
        DealTranche tranche = new DealTranche();
        tranche.setInternalId(InternalIdFactory.forInternalEntity());
        tranche.setDealId(deal.getId());
        tranche.setStatus(status);
        tranche.setStrategyTrancheId(strategyTrancheId);
        tranche.setLevel(level);
        tranche.setEntryStepType(entryStepType);
        tranche.setEpisodeSeq(FIRST_EPISODE_SEQ);
        DealTranche saved = dealTrancheDataService.save(tranche);
        if (nonNull(deal.getTranches())) {
            deal.getTranches().add(saved);
        }
    }
}
