package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.resolve.PositionStatusResolver;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.mapping.PositionMapper;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.persistence.service.PositionDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Добыча состояния позиции. Команда ДВУНОГАЯ: живой эпизод, затем — при
 * его отсутствии либо смене — история закрытых эпизодов
 * (docs/components/RefreshPositionExecutor.md).
 *
 * <p><b>Строку заводит нога 1, наполняет положением закрытия нога 2.</b>
 * Разделение безусловно: иначе у эпизода, материализованного ногой 1,
 * порог доказанного покрытия не двигался бы, и обязанность сверки у
 * сделки не возникала бы вовсе (docs/spec/pnl-reconciliation.json,
 * {@code dutyArisen}).
 *
 * <p><b>«Тот же эпизод» — совпадение ПАРЫ</b> (биржевой идентификатор,
 * биржевое время создания): источник переиспользует идентификатор у
 * переоткрытой позиции, и одного его недостаточно
 * (docs/models/domain/core/Position.md).
 *
 * <p><b>Терминала команда не выносит.</b> Недобытая запись закрытия — не
 * «сущность потеряна», а недобытый факт: звено не завершается и
 * повторяется по бюджету строки исполнения. Этим добыча позиции
 * отличается от добычи заявки, где исчерпанный цикл и есть основание
 * терминала.
 */
@Component
@RequiredArgsConstructor
public class RefreshPositionExecutor implements CommandExecutor {

    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;
    private final PositionMapper positionMapper;
    private final PositionStatusResolver positionStatusResolver;
    private final DealRiskNumbersService dealRiskNumbersService;
    private final DealDataService dealDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_POSITION_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        String accountInternalId = dealContext.getExchangeAccount().getInternalId();
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        Position fetched = exchangeOperationsClient.getPosition(accountInternalId, externalInstrumentId);
        Boolean liveLegStops = liveLegStops(deal, fetched);
        if (isFalse(liveLegStops)) {
            harvestCloseRecords(deal, accountInternalId, externalInstrumentId);
        }
        deal.setPositions(positionDataService.findEpisodes(deal.getId()));
        assignEpisodeAxis(deal);
        if (isFalse(dealRiskNumbersService.recompute(dealContext))) {
            return ServiceCommandExecutionResult.notCompleted(
                    "Deal graph incomplete: risk numbers not recomputed for deal " + deal.getId());
        }
        if (isFalse(liveLegStops) && isFalse(isEmpty(deal.episodesAwaitingCloseRecord()))) {
            return ServiceCommandExecutionResult.notCompleted(
                    "Close record not fetched for deal " + deal.getId());
        }
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Нога 1. Истина — обход останавливается на ней: живой эпизод тот же
     * (обновили внешние поля) либо эпизода не было вовсе.
     *
     * <p><b>У четвёртой ветви два дискриминатора, и оба обязательны.</b>
     * Без признака наблюдения ветвь заводила бы фантомную строку у всякой
     * сделки между отправкой входной ноги и её филлом; без «строк эпизода
     * нет ни одной» — ещё одну строку каждым проходом после закрытия
     * эпизода, а на задвоенном результате стои́т счётчик серии убытков.
     */
    private Boolean liveLegStops(Deal deal, Position fetched) {
        Position live = deal.livePosition();
        if (nonNull(fetched)) {
            if (nonNull(live)
                    && isTrue(live.sameEpisode(fetched.getExternalId(), fetched.getExternalCreatedAt()))) {
                applyFetched(live, fetched);
                return Boolean.TRUE;
            }
            if (nonNull(live)) {
                closeEpisode(live);
            }
            openEpisode(deal.getId(), fetched);
            return Boolean.FALSE;
        }
        if (nonNull(live)) {
            closeEpisode(live);
            return Boolean.FALSE;
        }
        if (isEmpty(deal.getPositions())) {
            if (isFalse(deal.positionObserved())) {
                return Boolean.TRUE;
            }
            materializeStub(deal.getId());
        }
        return Boolean.FALSE;
    }

    /**
     * Нога 2. Наполняет положением закрытия каждую строку эпизода, которая
     * закрыта и записи закрытия не несёт; запись, чьей строки нет,
     * материализует своей.
     *
     * <p>Предикат отбора — «строка закрыта и положения не несёт», поэтому
     * нога идемпотентна и покрывает эпизод, схлопнувшийся и
     * переоткрывшийся между тиками.
     */
    private void harvestCloseRecords(Deal deal, String accountInternalId, String externalInstrumentId) {
        List<Position> episodes = positionDataService.findEpisodes(deal.getId());
        if (episodes.stream().noneMatch(episode -> isTrue(episode.awaitsCloseRecord()))) {
            return;
        }
        Deque<Position> stubs = new ArrayDeque<>(episodes.stream()
                .filter(episode -> isTrue(episode.awaitsCloseRecord()) && isNull(episode.getExternalId()))
                .toList());
        for (Position record : closeRecords(deal, accountInternalId, externalInstrumentId)) {
            applyCloseRecord(deal, episodes, stubs, record);
        }
    }

    private void applyCloseRecord(Deal deal, List<Position> episodes, Deque<Position> stubs, Position record) {
        Position matched = episodes.stream()
                .filter(episode -> isTrue(episode.sameEpisode(record.getExternalId(),
                        record.getExternalCreatedAt())))
                .findFirst()
                .orElse(null);
        if (nonNull(matched) && isFalse(matched.awaitsCloseRecord())) {
            return;
        }
        Position target = nonNull(matched) ? matched : materializationTarget(deal.getId(), stubs);
        positionMapper.updateFromFetched(record, target);
        target.setStatus(Position.Status.CLOSED);
        target.setExternalSize(BigDecimal.ZERO);
        positionDataService.save(target);
        dealDataService.advanceCoverageProvenThrough(deal.getId(), record.getExternalModifiedAt());
    }

    /**
     * Цель материализации: сперва строка-заготовка, заведённая ногой 1 без
     * идентичности, и только затем новая. Иначе у сделки, чью позицию
     * впервые увидели закрытой, заготовка осталась бы пустой навсегда, а
     * рядом с ней встал бы дубль того же эпизода.
     */
    private Position materializationTarget(Long dealId, Deque<Position> stubs) {
        return isEmpty(stubs) ? newEpisode(dealId) : stubs.poll();
    }

    /**
     * Нижняя граница окна записей — та же, что у окна линковки движений:
     * поле сделки, а при пустой колонке суррогат из биржевого момента её
     * заведения (docs/spec/cash-flow-linkage.json, {@code lowerBound}).
     * Подстановка идёт ВНУТРИ исполнителя, колонку не трогая: различитель
     * провенанса стои́т на её пустоте.
     *
     * <p>Граница не резолвилась — записей не запрашиваем: без нижней
     * границы окно накрыло бы чужие эпизоды инструмента.
     */
    private List<Position> closeRecords(Deal deal, String accountInternalId, String externalInstrumentId) {
        OffsetDateTime windowBegin = nonNull(deal.getBillsWindowBegin())
                ? deal.getBillsWindowBegin()
                : deal.getExternalCreatedAt();
        if (isNull(windowBegin)) {
            return List.of();
        }
        return exchangeOperationsClient.getPositionCloseRecords(accountInternalId, externalInstrumentId,
                windowBegin);
    }

    /**
     * <b>Ось эпизода на ногах — write-once.</b> Нога с непустым филлом и
     * пустой осью приписывается ЖИВОМУ эпизоду: заполнял его именно он —
     * иначе филла к моменту наблюдения не было бы.
     *
     * <p>Без оси ноги закрытых эпизодов неотличимы от ног текущего, и пара
     * «взятое ↔ снятое защитой» считалась бы по всей истории сделки, то
     * есть кратно завышенной (docs/spec/deal-risk-numbers.json,
     * {@code onLiveEpisode}).
     *
     * <p>Ноги собираются <b>обходом траншей</b>: в целевой модели они висят
     * на них (docs/models/domain/aggregate/Deal.md §Структура).
     */
    private void assignEpisodeAxis(Deal deal) {
        Position live = deal.livePosition();
        if (isNull(live) || isNull(live.getId())) {
            return;
        }
        emptyIfNull(deal.getTranches()).stream()
                .flatMap(tranche -> emptyIfNull(tranche.getOrders()).stream())
                .filter(order -> isNull(order.getPositionId()))
                .filter(RefreshPositionExecutor::hasFill)
                .forEach(order -> {
                    order.setPositionId(live.getId());
                    orderDataService.save(order);
                });
    }

    private static boolean hasFill(Order order) {
        return nonNull(order.getAccumulatedFillSize()) && order.getAccumulatedFillSize().signum() > 0;
    }

    private void applyFetched(Position episode, Position fetched) {
        positionMapper.updateFromFetched(fetched, episode);
        applyResolvedStatus(episode, fetched);
        positionDataService.save(episode);
    }

    /**
     * Закрытие прежней строки эпизода. Наблюдение нового эпизода размер
     * прежнего не подменяет: он остаётся тем, каким наблюдался последний
     * раз.
     */
    private void closeEpisode(Position live) {
        applyResolvedStatus(live, null);
        positionDataService.save(live);
    }

    private void openEpisode(Long dealId, Position fetched) {
        Position episode = newEpisode(dealId);
        positionMapper.updateFromFetched(fetched, episode);
        applyResolvedStatus(episode, fetched);
        positionDataService.save(episode);
    }

    /**
     * Строка ЗАКРЫТОГО эпизода без положения закрытия: позиция
     * наблюдалась, живой строки нет и строк эпизода нет ни одной.
     *
     * <p>Пропуск этой ветви счётен: признак полноты графа остался бы
     * ложным навсегда, и сделка висела бы активной, занимая слот пары
     * «счёт × инструмент».
     */
    private void materializeStub(Long dealId) {
        Position stub = newEpisode(dealId);
        stub.setStatus(Position.Status.CLOSED);
        stub.setExternalSize(BigDecimal.ZERO);
        positionDataService.save(stub);
    }

    private Position newEpisode(Long dealId) {
        Position episode = new Position();
        episode.setDealId(dealId);
        return episode;
    }

    /** Причина закрытия — write-once: резолвер её не перебивает. */
    private void applyResolvedStatus(Position position, Position fetched) {
        StatusResolveResult<Position.Status, Position.CloseReason> result = positionStatusResolver.resolve(fetched);
        position.setStatus(result.getStatus());
        if (isNull(position.getCloseReason()) && nonNull(result.getCloseReason())) {
            position.setCloseReason(result.getCloseReason());
        }
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
