package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
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
            completeAction(actionState);
            return ServiceCommandExecutionResult.ok();
        }
        return harvestCloseRecords(deal, externalInstrumentId, actionState);
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
