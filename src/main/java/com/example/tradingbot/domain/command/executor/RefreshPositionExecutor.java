package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.risk.DealRiskNumbers;
import com.example.tradingbot.domain.command.resolve.PositionStatusResolver;
import com.example.tradingbot.domain.command.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionCloseResultExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет добычу состояния позиции. Команда ДВУНОГАЯ: живая позиция,
 * затем — при её отсутствии либо смене эпизода — история закрытых
 * позиций.
 *
 * <p>Строку заводит нога 1, наполняет положением закрытия нога 2.
 * Разделение безусловно: иначе у эпизода, материализованного ногой 1,
 * порог доказанного покрытия не двигался бы, и обязанность сверки у
 * сделки не возникала бы вовсе. «Тот же эпизод» — совпадение ПАРЫ
 * (биржевой идентификатор, биржевое время создания): источник
 * переиспользует идентификатор у переоткрытой позиции, и одного его
 * недостаточно.
 *
 * <p>Терминала команда не выносит: недобытая запись закрытия — не
 * «сущность потеряна», а недобытый факт, и звено просто не
 * завершается, повторяясь по бюджету действия.
 * См. docs/components/RefreshPositionExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class RefreshPositionExecutor implements CommandExecutor {

    private final PositionDataService positionDataService;
    private final DealDataService dealDataService;
    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;
    private final PositionMapper positionMapper;
    private final PositionStatusResolver positionStatusResolver;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_POSITION;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        PositionExternalSnapshot snapshot = integrationService.getPosition(externalInstrumentId);

        if (isTrue(liveLegStops(deal, snapshot))) {
            applyEpisodeAxisAndRiskNumbers(deal, dealContext);
            completeAction(actionState);
            return ServiceCommandExecutionResult.ok();
        }
        ServiceCommandExecutionResult result = harvestCloseRecords(deal, externalInstrumentId, actionState);
        applyEpisodeAxisAndRiskNumbers(deal, dealContext);
        return result;
    }

    /**
     * Две работы одного наблюдения — ось эпизода на ногах и пересчёт
     * четвёрки чисел риска сделки. Обе принадлежат этому исполнителю
     * потому, что обе меняются ровно тем, что он наблюдает: размером
     * живого эпизода и сменой эпизода
     * (docs/models/domain/aggregate/Deal.md §«Писатели четвёрки и их
     * триггеры»).
     */
    private void applyEpisodeAxisAndRiskNumbers(Deal deal, DealContext dealContext) {
        assignEpisodeAxis(deal);
        recomputeRiskNumbers(deal, dealContext);
    }

    /**
     * <b>Ось эпизода на ногах — write-once.</b> Нога с непустым филлом и
     * пустой осью приписывается ЖИВОМУ эпизоду: заполнял его именно
     * он — иначе филла к моменту наблюдения не было бы.
     *
     * <p>Без оси ноги закрытых эпизодов неотличимы от ног текущего, и
     * пара «взятое ↔ снятое защитой» считалась бы по всей истории
     * сделки — то есть кратно завышенной
     * (docs/spec/deal-risk-numbers.json §onLiveEpisode).
     *
     * <p>Write-once не украшение: переприписывание ноги новому эпизоду
     * задним числом перенесло бы её риск с закрытого эпизода на живой.
     */
    private void assignEpisodeAxis(Deal deal) {
        Position live = deal.livePosition();
        if (isNull(live) || isNull(live.getId())) {
            return;
        }
        emptyIfNull(deal.getOrders()).stream()
                .filter(order -> isNull(order.getPositionId()))
                .filter(order -> nonNull(order.getAccumulatedFillSize())
                        && order.getAccumulatedFillSize().signum() > 0)
                .forEach(order -> {
                    order.setPositionId(live.getId());
                    orderDataService.save(order);
                });
    }

    /**
     * Пересчёт запрещён на неполном графе: числа считаются по ногам,
     * защитам и эпизодам из контекста прохода, и на неполном графе они
     * вышли бы ЗАНИЖЕННЫМИ — то есть ослабили бы кумулятивный потолок.
     * Прежние значения тогда остаются нетронутыми.
     */
    private void recomputeRiskNumbers(Deal deal, DealContext dealContext) {
        if (isFalse(DealRiskNumbers.recomputeAllowed(dealContext.getGraphComplete()))) {
            return;
        }
        deal.setPositions(positionDataService.findEpisodes(deal.getId()));
        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);
        deal.setPlannedRiskAmount(numbers.getPlannedRiskAmount());
        deal.setIncurredRiskAmount(numbers.getIncurredRiskAmount());
        deal.setCurrentRiskAmount(numbers.getCurrentRiskAmount());
        deal.setProtectionRelievedRiskAmount(numbers.getProtectionRelievedRiskAmount());
        dealDataService.save(deal);
    }

    /**
     * Нога 1. Возвращает {@code true}, когда обход останавливается на
     * ней: живой эпизод тот же (обновили внешние поля) либо эпизода не
     * было вовсе (позиция по сделке не наблюдалась).
     */
    private Boolean liveLegStops(Deal deal, PositionExternalSnapshot snapshot) {
        Position live = deal.livePosition();
        if (nonNull(snapshot)) {
            if (nonNull(live) && isTrue(live.sameEpisode(snapshot.getExternalId(), snapshot.getExternalCreatedAt()))) {
                applyLiveSnapshot(live, snapshot);
                return Boolean.TRUE;
            }
            if (nonNull(live)) {
                closeEpisode(live);
            }
            openEpisode(deal.getId(), snapshot);
            return Boolean.FALSE;
        }
        if (nonNull(live)) {
            closeEpisode(live);
            return Boolean.FALSE;
        }
        if (isEmpty(deal.getPositions())) {
            // Дискриминаторов у ветви два, и оба обязательны: без признака
            // наблюдения ветвь заводила бы фантомную строку у всякой сделки
            // между отправкой входной заявки и её филлом, а без «строк
            // эпизода нет ни одной» — ещё одну строку каждым проходом
            // после закрытия эпизода.
            if (isFalse(deal.positionObserved())) {
                return Boolean.TRUE;
            }
            materializeStub(deal.getId());
        }
        return Boolean.FALSE;
    }

    /**
     * Нога 2. Наполняет положением закрытия каждую строку эпизода,
     * которая закрыта и записи закрытия не несёт; запись, чьей строки
     * нет, материализует своей. Записей нет — факт не добыт, звено не
     * завершается.
     */
    private ServiceCommandExecutionResult harvestCloseRecords(Deal deal, String externalInstrumentId,
                                                              DealActionState actionState) {
        List<Position> episodes = positionDataService.findEpisodes(deal.getId());
        Deque<Position> stubs = new ArrayDeque<>(episodes.stream()
                .filter(episode -> isTrue(episode.awaitsCloseRecord()) && isNull(episode.getExternalId()))
                .toList());
        if (episodes.stream().noneMatch(episode -> isTrue(episode.awaitsCloseRecord()))) {
            completeAction(actionState);
            return ServiceCommandExecutionResult.ok();
        }
        for (PositionCloseResultExternalSnapshot record : closeRecords(deal, externalInstrumentId)) {
            applyCloseRecord(deal, episodes, stubs, record);
        }
        if (positionDataService.findEpisodes(deal.getId()).stream()
                .anyMatch(episode -> isTrue(episode.awaitsCloseRecord()))) {
            return ServiceCommandExecutionResult.ok();
        }
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    private void applyCloseRecord(Deal deal, List<Position> episodes, Deque<Position> stubs,
                                  PositionCloseResultExternalSnapshot record) {
        Position matched = episodes.stream()
                .filter(episode -> isTrue(episode.sameEpisode(record.getExternalPosId(), record.getExternalCreatedAt())))
                .findFirst()
                .orElse(null);
        if (nonNull(matched)) {
            if (isFalse(matched.awaitsCloseRecord())) {
                return;
            }
            positionMapper.updateFromCloseSnapshot(record, matched);
            positionDataService.save(matched);
        } else {
            Position target = isEmpty(stubs) ? newEpisode(deal.getId()) : stubs.poll();
            positionMapper.materializeFromCloseSnapshot(record, target);
            target.setStatus(Position.Status.CLOSED);
            target.setExternalSize(BigDecimal.ZERO);
            positionDataService.save(target);
        }
        dealDataService.advanceCoverageProvenThrough(deal.getId(), record.getExternalModifiedAt());
    }

    /**
     * Нижняя граница окна записей — поле сделки, а при пустой колонке
     * суррогат: биржевой момент, которым сделка заведена. Подстановка
     * идёт ВНУТРИ исполнителя, колонку не трогая, поэтому различитель
     * провенанса остаётся на её пустоте
     * (docs/models/domain/aggregate/Deal.md).
     */
    private List<PositionCloseResultExternalSnapshot> closeRecords(Deal deal, String externalInstrumentId) {
        OffsetDateTime windowBegin = deal.getExternalCreatedAt();
        if (isNull(windowBegin)) {
            return List.of();
        }
        return integrationService.getPositionCloseRecords(externalInstrumentId, windowBegin);
    }

    private void applyLiveSnapshot(Position position, PositionExternalSnapshot snapshot) {
        positionMapper.updateFromSnapshot(snapshot, position);
        applyResolvedStatus(position, snapshot);
        positionDataService.save(position);
    }

    /**
     * Закрытие прежней строки эпизода. Наблюдение нового эпизода
     * размер прежнего не подменяет: он остаётся тем, каким наблюдался
     * последний раз.
     */
    private void closeEpisode(Position live) {
        applyResolvedStatus(live, null);
        positionDataService.save(live);
    }

    private void openEpisode(Long dealId, PositionExternalSnapshot snapshot) {
        Position episode = newEpisode(dealId);
        positionMapper.updateFromSnapshot(snapshot, episode);
        applyResolvedStatus(episode, snapshot);
        positionDataService.save(episode);
    }

    /**
     * Строка ЗАКРЫТОГО эпизода без положения закрытия: позиция
     * наблюдалась, живой строки нет и строк эпизода нет ни одной.
     * Пропуск этой ветви счётен — признак полноты графа остался бы
     * ложным навсегда, и сделка висела бы активной, занимая слот
     * инструмента.
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
    private void applyResolvedStatus(Position position, PositionExternalSnapshot snapshot) {
        StatusResolveResult<Position.Status, Position.CloseReason> result = positionStatusResolver.resolve(snapshot);
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
