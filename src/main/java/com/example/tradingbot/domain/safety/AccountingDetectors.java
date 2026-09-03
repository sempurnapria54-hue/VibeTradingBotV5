package com.example.tradingbot.domain.safety;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import com.example.tradingbot.util.Constants;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детекторы, сравнивающие наши строки с биржей: `A6` (жёсткая ступень
 * радиуса не проэнфорсена), `A8` (локально терминальная сущность жива на
 * бирже), `A9` (хвосты заявок, не объяснимые живой сделкой).
 *
 * <p><b>У всех трёх гистерезис в два тика.</b> Их признак сравнивает БД с
 * биржей, и наш собственный незавершённый ход производит его транзиторно:
 * между отправкой команды и её появлением в срезе состояние выглядит
 * расхождением (docs/components/AnomalyJob.md §«Такт и гистерезис»).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingDetectors {

    /** Признак сравнивает БД с биржей: подтверждается следующим тиком. */
    private static final Integer CONFIRMED_NEXT_TICK = 2;

    private final DealDataService dealDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final AnomalyReaction reaction;

    public void detect(AnomalyScan scan, Exchange exchange, Instrument instrument) {
        rungNotEnforced(scan, exchange, instrument);
        terminalAliveOnExchange(scan, exchange, instrument);
        orphanOrders(scan, exchange, instrument);
    }

    /**
     * `A6`: жёсткая ступень радиуса стои́т, а на бирже живут сущности
     * этого радиуса. Запрос той же ступени поглотит анкер — права на
     * доведение недоделанного у автоматического сигнала нет, — поэтому
     * находка заводит только строку журнала, и она `NON_CRITICAL`:
     * kill-switch ЭТОЙ реакцией не гоняется
     * (docs/components/SafetyHoldCoordinator.md §«Поглощённый сигнал
     * наблюдаем»).
     */
    private void rungNotEnforced(AnomalyScan scan, Exchange exchange, Instrument instrument) {
        if (isFalse(exchange.isTradeBlocked()) && isFalse(instrument.isTradeBlocked())) {
            return;
        }
        if (isFalse(hasLiveEntities(scan, instrument))) {
            return;
        }
        log.warn("[anomaly] жёсткая ступень не проэнфорсена instrumentId={}", instrument.getId());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.SOFT)
                .code(Constants.Hold.SAFETY_RUNG_NOT_ENFORCED)
                .instrument(instrument)
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(true)
                .build(), exchange);
    }

    /**
     * `A8`: наша строка терминальна, а сущность на бирже жива. Обратное
     * направление детектором не является: отсутствие на бирже — штатный
     * факт (исполнение, отмена, закрытие), и его разрешает `REFRESH_*`.
     *
     * <p>Предмет отчёта — сама сущность: у отчёта без блокировки
     * состояние держится на ней, а не на объекте радиуса, и без предмета
     * в ключе два разных расхождения по одному инструменту схлопнулись бы
     * в одну строку.
     */
    private void terminalAliveOnExchange(AnomalyScan scan, Exchange exchange, Instrument instrument) {
        for (String clientId : liveOwnClientIds(scan, instrument)) {
            if (isFalse(terminalLocally(clientId))) {
                continue;
            }
            log.warn("[anomaly] локально терминальная сущность жива на бирже clientId={}", clientId);
            reaction.apply(AnomalyFinding.builder()
                    .scope(HoldScope.INSTRUMENT)
                    .rung(HoldRung.SOFT)
                    .code(Constants.Hold.LOCAL_TERMINAL_ALIVE_ON_EXCHANGE)
                    .instrument(instrument)
                    .subjectExternalId(clientId)
                    .hysteresisTicks(CONFIRMED_NEXT_TICK)
                    .journalOnly(true)
                    .build(), exchange);
        }
    }

    /**
     * `A9`: позиции по инструменту нет, а заявки живут, и живая сделка их
     * не объясняет. Операнд БД обязателен: наша штатная отдыхающая
     * входная заявка позиции ещё не имеет по построению, и без него
     * детектор срабатывал бы на каждом нормальном входе.
     */
    private void orphanOrders(AnomalyScan scan, Exchange exchange, Instrument instrument) {
        String externalId = instrument.getExternalId();
        if (isFalse(scan.positionsOf(externalId).isEmpty())) {
            return;
        }
        if (isEmpty(scan.ordersOf(externalId)) && isEmpty(scan.algoOrdersOf(externalId))) {
            return;
        }
        if (isTrue(dealDataService.existsActiveByInstrumentId(instrument.getId()))) {
            return;
        }
        log.warn("[anomaly] хвосты заявок без живой сделки instrumentId={}", instrument.getId());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.SOFT)
                .code(Constants.Hold.INSTRUMENT_ORPHAN_ORDERS)
                .instrument(instrument)
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), exchange);
    }

    /** По инструменту на бирже живёт хоть что-то. */
    private Boolean hasLiveEntities(AnomalyScan scan, Instrument instrument) {
        String externalId = instrument.getExternalId();
        return isFalse(scan.positionsOf(externalId).isEmpty())
                || isFalse(scan.ordersOf(externalId).isEmpty())
                || isFalse(scan.algoOrdersOf(externalId).isEmpty());
    }

    /**
     * Клиентские идентификаторы живых на бирже сущностей, поставленных
     * НАМИ. Чужие сюда не попадают: их предмет — свой детектор, и искать
     * их в наших таблицах бессмысленно.
     */
    private List<String> liveOwnClientIds(AnomalyScan scan, Instrument instrument) {
        String externalId = instrument.getExternalId();
        List<String> ids = new ArrayList<>();
        scan.ordersOf(externalId).stream()
                .map(OrderExternalSnapshot::getInternalId)
                .filter(id -> isTrue(ClientIdGenerator.isOurs(id)))
                .forEach(ids::add);
        scan.algoOrdersOf(externalId).stream()
                .map(AlgoOrderExternalSnapshot::getInternalId)
                .filter(id -> isTrue(ClientIdGenerator.isOurs(id)))
                .forEach(ids::add);
        return ids;
    }

    /**
     * Наша строка по этому идентификатору терминальна. Строки нет вовсе —
     * не терминальна: это предмет другого детектора, и путать «мы её
     * закрыли» с «мы её не заводили» нельзя.
     */
    private Boolean terminalLocally(String clientId) {
        Order order = orderDataService.findByInternalId(clientId);
        if (nonNull(order)) {
            return isFalse(order.isLive());
        }
        AlgoOrder algoOrder = algoOrderDataService.findByInternalId(clientId);
        return nonNull(algoOrder) && isFalse(algoOrder.isLive());
    }
}
