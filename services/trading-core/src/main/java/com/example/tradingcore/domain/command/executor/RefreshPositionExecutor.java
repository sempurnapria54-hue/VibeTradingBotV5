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
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
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
import java.util.Objects;
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
 * <p><b>Эпизод закрывается записью закрытия своей пары, а не пустым
 * ответом живой ноги.</b> Пустота одного чтения неотличима от закрытия, и
 * ложное закрытие снимает живой риск эпизода со счёта; закрывает строку
 * смена пары на живой ноге либо запись её закрытия, добытая ногой 2
 * (docs/spec/external-status-resolution.json, {@code positionCloseCorroborated};
 * docs/components/RefreshPositionExecutor.md).
 *
 * <p><b>«Тот же эпизод» — совпадение ПАРЫ</b> (биржевой идентификатор,
 * биржевое время создания): источник переиспользует идентификатор у
 * переоткрытой позиции, и одного его недостаточно
 * (docs/models/domain/core/Position.md).
 *
 * <p><b>Строку эпизода исполнитель адресует АКТИВНОЙ строкой, а не живым
 * эпизодом</b> ({@code Deal.activeEpisode()}): сверка пары, закрытие прежней
 * строки при смене пары, добыча записи закрытия и ось эпизода у ног нужны и
 * активной строке с нулевым размером — между обнулением позиции на площадке
 * и приходом факта закрытия. Живой эпизод по дому эту строку не отдаёт, и
 * без второго имени у сделки вставали бы две активные строки
 * (docs/models/domain/core/Position.md §«Живой риск»).
 *
 * <p><b>Терминала команда не выносит.</b> Недобытая запись закрытия — не
 * «сущность потеряна», а недобытый факт: живая строка остаётся живой,
 * звено не завершается и повторяется по бюджету строки исполнения. Этим
 * добыча позиции отличается от добычи заявки, где исчерпанный цикл и есть
 * основание терминала.
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
        Position unconfirmedLive = isNull(fetched) ? deal.activeEpisode() : null;
        Boolean liveLegStops = liveLegStops(deal, fetched);
        if (isFalse(liveLegStops)) {
            harvestCloseRecords(deal, unconfirmedLive, accountInternalId, externalInstrumentId);
        }
        deal.setPositions(positionDataService.findEpisodes(deal.getId()));
        assignEpisodeAxis(deal);
        if (isFalse(dealRiskNumbersService.recompute(dealContext))) {
            return ServiceCommandExecutionResult.notCompleted(
                    "Deal graph incomplete: risk numbers not recomputed for deal " + deal.getId());
        }
        if (isFalse(liveLegStops) && (isFalse(isEmpty(deal.episodesAwaitingCloseRecord()))
                || (nonNull(unconfirmedLive) && nonNull(deal.activeEpisode())))) {
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
     * <p><b>Пустой ответ живой строку не закрывает.</b> Транзиентная пустота
     * неотличима от закрытия, а ложное закрытие снимает живой риск эпизода
     * со счёта: нетто-размер сделки становится нулём, и следующее
     * наблюдение заводит вторую строку того же эпизода. Строка остаётся
     * живой, и закрывает её нога 2 — записью закрытия её пары
     * (docs/spec/external-status-resolution.json, величина
     * {@code positionCloseCorroborated}). <b>Смена пары закрывает прежний
     * эпизод здесь же</b>: другая пара на живой ноге сама доказывает, что
     * прежний закрыт.
     *
     * <p><b>У ветви «строк эпизода нет» два дискриминатора, и оба
     * обязательны.</b> Без признака наблюдения ветвь заводила бы фантомную
     * строку у всякой сделки между отправкой входной ноги и её филлом; без
     * «строк эпизода нет ни одной» — ещё одну строку каждым проходом после
     * закрытия эпизода, а на задвоенном результате стои́т счётчик серии
     * убытков.
     */
    private Boolean liveLegStops(Deal deal, Position fetched) {
        Position live = deal.activeEpisode();
        if (nonNull(fetched)) {
            if (nonNull(live)
                    && isTrue(live.sameEpisode(fetched.getExternalId(), fetched.getExternalCreatedAt()))) {
                applyFetched(live, fetched);
                return Boolean.TRUE;
            }
            if (nonNull(live)) {
                closeReplacedEpisode(live);
            }
            openEpisode(deal.getId(), fetched);
            return Boolean.FALSE;
        }
        if (nonNull(live)) {
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
     * закрыта и записи закрытия не несёт, и живую строку, которой нога 1 не
     * нашла; запись, чьей строки нет, материализует своей.
     *
     * <p>Предикат отбора — «строка закрыта и положения не несёт», поэтому
     * нога идемпотентна и покрывает эпизод, схлопнувшийся и
     * переоткрывшийся между тиками.
     *
     * <p><b>Живая строка, не найденная живой ногой, закрывается ТОЛЬКО
     * записью своей пары.</b> Запись найдена — строка закрыта
     * ({@code EXTERNAL_CLOSE}), поля положения закрытия и порог доказанного
     * покрытия ложатся той же транзакцией; не найдена — строка остаётся
     * живой, и звено не завершается.
     *
     * @param unconfirmedLive живая строка, которой живая нога не нашла;
     *                        пусто — такой нет
     */
    private void harvestCloseRecords(Deal deal, Position unconfirmedLive, String accountInternalId,
                                     String externalInstrumentId) {
        List<Position> episodes = positionDataService.findEpisodes(deal.getId());
        if (isNull(unconfirmedLive)
                && episodes.stream().noneMatch(episode -> isTrue(episode.awaitsCloseRecord()))) {
            return;
        }
        Deque<Position> stubs = new ArrayDeque<>(episodes.stream()
                .filter(episode -> isTrue(episode.awaitsCloseRecord()) && isNull(episode.getExternalId()))
                .toList());
        for (Position record : closeRecords(deal, accountInternalId, externalInstrumentId)) {
            applyCloseRecord(deal, episodes, stubs, record, unconfirmedLive);
        }
    }

    private void applyCloseRecord(Deal deal, List<Position> episodes, Deque<Position> stubs, Position record,
                                  Position unconfirmedLive) {
        Position matched = episodes.stream()
                .filter(episode -> isTrue(episode.sameEpisode(record.getExternalId(),
                        record.getExternalCreatedAt())))
                .findFirst()
                .orElse(null);
        Boolean closesLive = nonNull(matched) && nonNull(unconfirmedLive)
                && Objects.equals(unconfirmedLive.getId(), matched.getId());
        if (nonNull(matched) && isFalse(matched.awaitsCloseRecord()) && isFalse(closesLive)) {
            return;
        }
        Position target = nonNull(matched) ? matched : materializationTarget(deal.getId(), stubs);
        if (isTrue(closesLive)) {
            applyResolvedStatus(target, null, Boolean.TRUE);
        }
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
        Position live = deal.activeEpisode();
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
        applyResolvedStatus(episode, fetched, Boolean.FALSE);
        positionDataService.save(episode);
    }

    /**
     * Закрытие прежней строки эпизода при смене пары на живой ноге: другая
     * пара сама корроборирует закрытие прежнего эпизода, записи для этого не
     * нужно. Наблюдение нового эпизода размер прежнего не подменяет: он
     * остаётся тем, каким наблюдался последний раз.
     */
    private void closeReplacedEpisode(Position live) {
        applyResolvedStatus(live, null, Boolean.TRUE);
        positionDataService.save(live);
    }

    private void openEpisode(Long dealId, Position fetched) {
        Position episode = newEpisode(dealId);
        positionMapper.updateFromFetched(fetched, episode);
        applyResolvedStatus(episode, fetched, Boolean.FALSE);
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
    private void applyResolvedStatus(Position position, Position fetched, Boolean closeCorroborated) {
        StatusResolveResult<Position.Status, Position.CloseReason> result =
                positionStatusResolver.resolve(fetched, closeCorroborated);
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
