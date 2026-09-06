package com.example.tradingcore.domain.safety;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.util.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детекторы, сравнивающие наши строки с биржей: жёсткая ступень радиуса
 * не проэнфорсена, локально терминальная сущность жива на бирже, хвосты
 * заявок без живой сделки (docs/components/AnomalyJob.md §«Что ищет»).
 *
 * <p><b>У всех трёх гистерезис в два тика.</b> Их признак сравнивает БД с
 * биржей, и наш собственный незавершённый ход производит его транзиторно:
 * между отправкой команды и её появлением в срезе состояние выглядит
 * расхождением.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingDetectors {

    /** Признак сравнивает БД с биржей: подтверждается следующим тиком. */
    private static final Integer CONFIRMED_NEXT_TICK = 2;

    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final AnomalyReaction reaction;

    /**
     * @param accountHardRung  жёсткая ступень стои́т на самом счёте
     * @param hardRungPairs    инструменты счёта под жёсткой ступенью пары
     * @param dealExplains     живая сделка по паре есть; резолвится
     *                         проходом один раз на инструмент — тот же
     *                         признак нужен восстановительной тропе, и
     *                         второй запрос за ним был бы дублем в
     *                         пределах одного тика
     */
    public void detect(AnomalyScan scan, ExchangeAccount account, Instrument instrument,
                       Boolean accountHardRung, Set<Long> hardRungPairs, Boolean dealExplains) {
        rungNotEnforced(scan, account, instrument, accountHardRung, hardRungPairs);
        terminalAliveOnExchange(scan, account, instrument);
        orphanOrders(scan, account, instrument, dealExplains);
    }

    /**
     * Жёсткая ступень радиуса стои́т, а на бирже живут сущности этого
     * радиуса. Запрос той же ступени поглотит анкер — права на доведение
     * недоделанного у автоматического сигнала нет, — поэтому находка
     * заводит только строку журнала, и она некритична: kill-switch ЭТОЙ
     * реакцией не гоняется
     * (docs/components/SafetyHoldCoordinator.md §«Поглощённый сигнал»).
     */
    private void rungNotEnforced(AnomalyScan scan, ExchangeAccount account, Instrument instrument,
                                 Boolean accountHardRung, Set<Long> hardRungPairs) {
        if (isFalse(accountHardRung) && isFalse(hardRungPairs.contains(instrument.getId()))) {
            return;
        }
        if (isFalse(hasLiveEntities(scan, instrument))) {
            return;
        }
        log.warn("Hard safety rung is not enforced instrumentId={}", instrument.getId());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.SOFT)
                .code(Constants.Hold.SAFETY_RUNG_NOT_ENFORCED)
                .instrument(instrument)
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(true)
                .build(), account);
    }

    /**
     * Наша строка терминальна, а сущность на бирже жива. Обратное
     * направление детектором не является: отсутствие на бирже — штатный
     * факт (исполнение, отмена, закрытие), и его разрешает добыча.
     *
     * <p>Предмет отчёта — сама сущность: у отчёта без блокировки
     * состояние держится на ней, а не на объекте радиуса, и без предмета
     * в ключе два разных расхождения по одному инструменту схлопнулись бы
     * в одну строку.
     */
    private void terminalAliveOnExchange(AnomalyScan scan, ExchangeAccount account, Instrument instrument) {
        for (String clientId : liveOwnClientIds(scan, instrument)) {
            if (isFalse(terminalLocally(clientId))) {
                continue;
            }
            log.warn("Locally terminal entity is alive on the exchange clientId={}", clientId);
            reaction.apply(AnomalyFinding.builder()
                    .scope(HoldScope.INSTRUMENT)
                    .rung(HoldRung.SOFT)
                    .code(Constants.Hold.LOCAL_TERMINAL_ALIVE_ON_EXCHANGE)
                    .instrument(instrument)
                    .subjectExternalId(clientId)
                    .hysteresisTicks(CONFIRMED_NEXT_TICK)
                    .journalOnly(true)
                    .build(), account);
        }
    }

    /**
     * Позиции по инструменту нет, а заявки живут, и живая сделка их не
     * объясняет. Операнд БД обязателен: наша штатная отдыхающая входная
     * заявка позиции ещё не имеет по построению, и без него детектор
     * срабатывал бы на каждом нормальном входе.
     *
     * <p>Ступень мягкая по оси: живого направленного риска у хвоста нет,
     * снимать нечего, а под сомнением наш учёт по этому инструменту — то
     * есть право заводить на нём новое.
     */
    private void orphanOrders(AnomalyScan scan, ExchangeAccount account, Instrument instrument,
                              Boolean dealExplains) {
        String externalId = instrument.getExternalId();
        if (isNotEmpty(scan.positionsOf(externalId))) {
            return;
        }
        if (isEmpty(scan.ordersOf(externalId)) && isEmpty(scan.algoOrdersOf(externalId))) {
            return;
        }
        if (isTrue(dealExplains)) {
            return;
        }
        log.warn("Orphan orders without a live deal instrumentId={}", instrument.getId());
        reaction.apply(AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.SOFT)
                .code(Constants.Hold.INSTRUMENT_ORPHAN_ORDERS)
                .instrument(instrument)
                .hysteresisTicks(CONFIRMED_NEXT_TICK)
                .journalOnly(false)
                .build(), account);
    }

    /** По инструменту на бирже живёт хоть что-то. */
    private Boolean hasLiveEntities(AnomalyScan scan, Instrument instrument) {
        String externalId = instrument.getExternalId();
        return isNotEmpty(scan.positionsOf(externalId))
                || isNotEmpty(scan.ordersOf(externalId))
                || isNotEmpty(scan.algoOrdersOf(externalId));
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
                .map(Order::getInternalId)
                .filter(id -> isTrue(InternalIdFactory.isOurs(id)))
                .forEach(ids::add);
        scan.algoOrdersOf(externalId).stream()
                .map(AlgoOrder::getInternalId)
                .filter(id -> isTrue(InternalIdFactory.isOurs(id)))
                .forEach(ids::add);
        return ids;
    }

    /**
     * Наша строка по этому идентификатору терминальна. Строки нет вовсе —
     * не терминальна: это предмет другого детектора, и путать «мы её
     * закрыли» с «мы её не заводили» нельзя.
     */
    private Boolean terminalLocally(String clientId) {
        Order order = orderDataService.findByInternalId(clientId).orElse(null);
        if (nonNull(order)) {
            return isFalse(order.isLive());
        }
        return algoOrderDataService.findByInternalId(clientId)
                .map(algoOrder -> isFalse(algoOrder.isLive()))
                .orElse(false);
    }
}
