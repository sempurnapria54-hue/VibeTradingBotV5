package com.example.tradingbot.domain.model.aggregate.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Сделка — lifecycle root и runtime graph торговой сделки: что система
 * сопровождает сценарий по конкретному Instrument, по pinned
 * StrategyDetail, в ожидаемом направлении, с FSM-статусом, причинами и
 * итоговым PnL. Не биржевая сущность (нет external id/status). Итоговый результат
 * считается разбивкой движений средств (docs/rules/pnl-reconciliation.md). Связь с DealActionState — не поле Deal (через
 * Deal.id → DealActionState.dealId). См.
 * docs/models/domain/aggregate/Deal.md, docs/lifecycles/Deal.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class Deal extends Auditable {

    /**
     * Виды отдельной защиты, у которых уровень срабатывания ОБЪЯВЛЕН —
     * фиксированный стоп (docs/spec/stop-exit-slippage.json, операнд
     * {@code protections[].kind}, значение {@code STOP}). Трейлинг и тейк
     * сюда не входят: у первого уровень в момент срабатывания не записан
     * никем, у второго уровня остановки убытка нет вовсе.
     */
    private static final Set<AlgoOrder.ConditionType> FIXED_STOP_TYPES = EnumSet.of(
            AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.ConditionType.PARTIAL_STOP_LOSS,
            AlgoOrder.ConditionType.OCO_FULL);

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Безопасный внешний/межсервисный id (API, логи, timeline). */
    private String internalId;

    /**
     * <b>Биржевой счёт тенанта, на котором идёт сделка</b> — ключ строки
     * торгового состояния счёта в базе ядра. Операнд базы риска, серии
     * убытков, ступени счёта и всех выборок радиуса счёта. Тенанта сделка
     * отдельным полем не несёт: он резолвится по счёту
     * (docs/architecture/tenant-and-exchange.md §«Торговая строка называет
     * счёт, и радиусы читаются от него»).
     */
    private Long exchangeAccountId;

    /** Инструмент (полный Instrument — в DealContext). */
    private Long instrumentId;

    /** Pinned StrategyDetail: открытая сделка ведётся по этой версии даже при изменении Strategy. */
    private Long strategyDetailId;

    /** FSM-статус сделки. */
    private Status status;

    /** Expected direction, фиксируется при создании; Position.direction должен ему соответствовать. */
    private StrategyTradeDirection direction;

    /** Короткая причина создания (не управляет FSM). */
    private EntryReason entryReason;

    /**
     * Фаза рынка при входе сделки. Write-once, пишет DealOpeningService
     * на тропе входа по объявлению той же транзакцией, что заводит
     * сделку. Пусто ровно у восстановленной сделки — входа по
     * объявлению у неё не было.
     */
    private MarketPhase.Type entryMarketPhase;

    /** Причина graceful shutdown / controlled close (если запущен). Не заменяет closeReason. */
    private ShutdownReason shutdownReason;

    /** Итоговая бизнес-причина завершения. */
    private CloseReason closeReason;

    /** Итоговый PnL (для terminal CLOSED/EMERGENCY_CLOSED обязателен; считается по движениям средств). */
    private BigDecimal resultProfit;

    /** Валюта результата (для ETH-USDT-SWAP обычно USDT). */
    private String resultProfitCurrency;

    /**
     * <b>Риск, принятый сделкой на входах ({@code R})</b> — заявленный
     * риск живых и исполнившихся входных ног всех траншей плюс налитая
     * доля выбывших. Знаменатель R-мультипликатора и операнд
     * кумулятивного потолка. Производная проекция: пересчитывается
     * ЦЕЛИКОМ (docs/spec/deal-risk-numbers.json §dealPlannedRisk).
     */
    private BigDecimal plannedRiskAmount;

    /**
     * <b>Взятый на входе риск</b> — сколько из заявленного встало под
     * удар. Измеритель разрыва «заявлено ↔ взято»; частичным выходом не
     * уменьшается. Производная проекция.
     */
    private BigDecimal incurredRiskAmount;

    /**
     * <b>Неотработанная доля взятого на входе риска.</b> Уменьшается
     * частичным выходом, при полном — ноль. Риском «под ударом сейчас»
     * не является: уровня стопа формула не содержит. Производная
     * проекция.
     */
    private BigDecimal currentRiskAmount;

    /**
     * <b>Риск, снятый защитой</b> — насколько действующий стоп уменьшил
     * взятый риск. Наблюдение, не операнд потолков; знак не клэмпится.
     * Производная проекция.
     */
    private BigDecimal protectionRelievedRiskAmount;

    /**
     * Валюта всех чисел риска сделки. Источник — расчётная валюта
     * инструмента; у всех траншей она одна.
     */
    private String plannedRiskCurrency;

    /**
     * <b>База риска на момент сайзинга. Write-once</b>: фиксируется
     * ПЕРВЫМ сайзингом сделки, каким бы траншем он ни делался.
     * Потребители — знаменатель фактического процента риска в
     * отчётности и делитель всех четырёх потолков живой сделки. Пусто =
     * сайзинга не было (позиция создана вне приложения).
     */
    private BigDecimal plannedRiskEquityBase;

    /**
     * Порог доказанного покрытия — до какого момента наблюдался факт
     * закрытия эпизода. В предикате линковки движений НЕ участвует:
     * сверху окно открыто до времени источника прохода. Пишет
     * наблюдатель факта закрытия эпизода, монотонно вперёд; пусто —
     * закрытие не добыто ни у одного эпизода. Потребитель —
     * обязанность сверки (docs/models/domain/aggregate/Deal.md).
     */
    private OffsetDateTime coverageProvenThrough;

    /**
     * Нижняя граница окна линковки движений. Единственный писатель —
     * SubmitOrderExecutor, по биржевому времени создания первой
     * отправленной входной заявки сделки, каким бы траншем она ни
     * ставилась; write-once. Пусто — граница не добыта, предикат
     * линковки берёт суррогат из externalCreatedAt
     * (docs/spec/cash-flow-linkage.json §lowerBound).
     */
    private OffsetDateTime billsWindowBegin;

    /**
     * До какого момента движения средств добыты. Пишет
     * RefreshBillsExecutor, монотонно вперёд. Единственный durable-факт
     * «добыча выполнялась»: пусто = не добывали, а не «добыли, движений
     * нет» (docs/models/domain/aggregate/Deal.md).
     */
    private OffsetDateTime billsFetchedThrough;

    /**
     * <b>Торговый исход закрытия</b> — признак отбора для отчёта.
     * Отличается от {@link #closeReason} намеренно: та — бизнес-причина
     * завершения сделки, этот — рыночный факт того, кто и почему закрыл
     * позицию (docs/spec/position-close-outcome.json).
     */
    private CloseOutcome closeOutcome;

    /**
     * <b>Исход сверки разбивки</b> — признак отбора. Свойство числа, а
     * не членства: сделка остаётся в популяции при любом значении
     * (docs/spec/pnl-reconciliation.json).
     */
    private ReconciliationStatus reconciliationStatus;

    /**
     * <b>Полнота разбивки</b> — признак отбора: накрыло ли окно добычи
     * движений всю жизнь сделки (docs/components/RefreshBillsExecutor.md).
     */
    private BreakdownCompleteness breakdownIncomplete;

    /**
     * <b>Почему знаменатель {@code R} пуст или непуст</b> — признак
     * отбора. {@code MISSING} есть аномалия с отчётом: вход был, а
     * знаменателя нет (docs/spec/deal-lifecycle.json
     * §benchmarkAvailabilityOnTerminal).
     */
    private RiskBenchmarkAvailability riskBenchmarkAvailability;

    /**
     * <b>Заявки сделки, не приписанные ни одному её траншу</b> — остаток
     * сборки графа: ключ транша у них пуст либо не называет ни одного
     * загруженного транша. Ноги сделки висят на траншах и собираются их
     * обходом (docs/models/domain/aggregate/Deal.md §Структура), поэтому
     * штатно остаток пуст; непустой он есть операнд инварианта
     * {@link #unattributedLiveRisk()}. Кладёт сборщик графа прохода
     * (docs/components/DealContextService.md §«Объёмы загрузки»).
     */
    private List<Order> unattributedOrders;

    /** Отдельные условные заявки сделки, не приписанные ни одному её траншу, — тот же остаток. */
    private List<AlgoOrder> unattributedAlgoOrders;

    /**
     * Эпизоды позиции: одна биржевая позиция — одна строка. Живой
     * эпизод не более одного, закрытые остаются.
     */
    private List<Position> positions;

    /**
     * Транши сделки — единицы принятия и сопровождения риска. Стадии
     * входа и сопровождения принадлежат им, а не сделке.
     */
    private List<DealTranche> tranches;

    /** Все транши сделки терминальны — предусловие терминала сделки. */
    public Boolean allTranchesTerminal() {
        return emptyIfNull(tranches).stream()
                .allMatch(tranche -> isTrue(tranche.isTerminal()));
    }

    /**
     * Сделка в терминальном статусе: слот инструмента не держит и
     * движений счёта больше не принимает (операнд предиката линковки —
     * docs/spec/cash-flow-linkage.json §linksToDeal). ERROR терминалом
     * не является — обработка аварийной тропы ещё идёт.
     */
    public Boolean isTerminal() {
        return Objects.equals(Status.CLOSED, status) || Objects.equals(Status.EMERGENCY_CLOSED, status);
    }

    /**
     * Сделка в ОКНЕ СВОРАЧИВАНИЯ: нового риска не берёт ни один её транш
     * (docs/rules/exit-teardown-order.md).
     *
     * <p><b>Окно — один статус, а не два.</b> Дом называет окном пару
     * {@code EXIT_PENDING} и {@code ERROR}, но вторая половина закрыта
     * НЕДОСТИЖИМОСТЬЮ акта, а не этим предикатом: в ошибочном состоянии
     * FSM траншей не прогоняется, а преконтроль на аварийных и
     * восстановительных тропах не вызывается вовсе
     * (docs/rules/risk-validator-scope.md). Операнд объявлен именно так и
     * в исполнимой форме (docs/spec/risk-limits.json, {@code dealCollapsing}).
     *
     * <p>Предикат живёт на модели, потому что преконтроль признак НЕ
     * ВЫВОДИТ — он получает его готовым (docs/components/RiskValidator.md);
     * второе его чтение — энфорсер ребра транша.
     */
    public Boolean isCollapsing() {
        return Objects.equals(Status.EXIT_PENDING, status);
    }

    /** Хоть один транш сделки несёт живой риск. */
    public Boolean anyTrancheRiskBearing() {
        return emptyIfNull(tranches).stream()
                .anyMatch(tranche -> isTrue(tranche.isRiskBearing()));
    }

    /**
     * Риск ВСЕХ траншей сделки покрыт защитой (docs/spec/protection-coverage.json,
     * величина {@code allTranchesCovered}) — агрегатный аналог покрытия транша.
     * Терминальный транш экспозиции не несёт и покрыт тривиально, поэтому
     * перечень не сужается до живых: сужение меняло бы ответ только там, где
     * покрывать нечего.
     */
    public Boolean allTranchesCovered() {
        return emptyIfNull(tranches).stream()
                .allMatch(tranche -> isTrue(tranche.isCovered()));
    }

    /**
     * Уровень защиты на всю позицию не резолвится: хоть один транш с
     * экспозицией не несёт своего уровня (docs/spec/protection-coverage.json,
     * величина {@code dealStopUnresolved}).
     */
    public Boolean stopUnresolved() {
        return emptyIfNull(tranches).stream()
                .anyMatch(tranche -> isTrue(tranche.stopUnresolved()));
    }

    /**
     * Действующий уровень защиты НА ВСЮ ПОЗИЦИЮ — наименее благоприятный
     * среди действующих защит всех траншей: у LONG нижний, у SHORT верхний
     * (docs/spec/protection-coverage.json, величина {@code stopCurrentLive}).
     *
     * <p><b>ОТКАЗЫВАЕТ ВЫЧИСЛЕНИЕМ, а не отдаёт уровень соседа</b>, если
     * хотя бы один транш с экспозицией своего уровня не несёт: агрегат по
     * непустым такой транш ПРОПУСТИЛ БЫ, и уровень на всю позицию брался
     * бы у соседа — занижение живого риска, ничем не ограниченное сверху.
     *
     * <p>Под лестницей разноуровневых защит оценка завышается, и это
     * направление консервативное.
     */
    public BigDecimal currentStopLevel() {
        if (isTrue(stopUnresolved())) {
            return null;
        }
        Stream<BigDecimal> levels = emptyIfNull(tranches).stream()
                .map(tranche -> tranche.worstActiveStopLevel(direction))
                .filter(Objects::nonNull);
        return StrategyTradeDirection.LONG.equals(direction)
                ? levels.min(BigDecimal::compareTo).orElse(null)
                : levels.max(BigDecimal::compareTo).orElse(null);
    }

    /**
     * Инвариант «ликвидация за стопом» у УДЕРЖИВАЕМОЙ позиции: действующий
     * уровень остановки убытка на всю позицию ({@link #currentStopLevel()})
     * лежит между ценой и ценой ликвидации, которую площадка называет у
     * живого эпизода, — у LONG строго выше неё, у SHORT строго ниже; равенство
     * — нарушение (docs/spec/risk-limits.json, величина
     * {@code heldStopBeforeLiquidation}).
     *
     * <p><b>Пусто — не измерено, и ветвей три:</b> живого эпизода нет
     * (ликвидировать нечего; цена на строке закрытого эпизода не читается),
     * действующего уровня нет (хоть один транш с экспозицией своего уровня
     * не несёт), площадка цены ликвидации не называет. Оценка ликвидации
     * вместо факта площадки не подставляется: она заведена на случай, когда
     * факта нет, а у ведомой позиции он есть.
     *
     * <p>Предикат живёт на модели, потому что оба операнда — данные графа
     * сделки. Ложь — признак детектора переоценки инварианта ликвидации;
     * пустоту он читает молчанием, а гейт живого обязательства покрытия и
     * гистерезис — его, а не формы (docs/components/AnomalyJob.md).
     */
    public Boolean heldStopBeforeLiquidation() {
        Position live = livePosition();
        if (isNull(live)) {
            return null;
        }
        BigDecimal stop = currentStopLevel();
        BigDecimal liquidation = live.getExternalLiquidationPrice();
        if (isNull(stop) || isNull(liquidation)) {
            return null;
        }
        return StrategyTradeDirection.LONG.equals(direction)
                ? stop.compareTo(liquidation) > 0
                : stop.compareTo(liquidation) < 0;
    }

    /** Живые транши сделки: те, что ещё занимают место в проходе. */
    public List<DealTranche> liveTranches() {
        return emptyIfNull(tranches).stream()
                .filter(tranche -> isTrue(tranche.isActive()))
                .collect(Collectors.toList());
    }

    /**
     * Живая сущность сделки, не приписанная ни одному её траншу.
     *
     * <p><b>Инвариант, а не находка сканера.</b> Всё живое по сделке
     * атрибутируется её траншам: экспозицию считают транши, покрытие
     * считают транши, терминал требует терминальности всех траншей. Нога,
     * висящая мимо них, не входит ни в один из этих счётов — то есть
     * несёт живой риск, который модель не приписывает никому
     * (docs/components/TranchePrecheckHandler.md §«Входные проверки»).
     *
     * <p>Предикат живёт на агрегате, а не в обработчике: тот же вопрос
     * задают предвходовая проверка, сопровождение и поиск нарушений
     * инвариантов, и ответ у них один.
     *
     * <p>Операнд — остаток сборки графа ({@link #unattributedOrders},
     * {@link #unattributedAlgoOrders}): ноги, легшие на транш, в нём не
     * бывают, и сверять их с перечнями траншей повторно нечего.
     */
    public Boolean unattributedLiveRisk() {
        return emptyIfNull(unattributedOrders).stream()
                .anyMatch(order -> isTrue(order.isLive()))
                || emptyIfNull(unattributedAlgoOrders).stream()
                .anyMatch(algoOrder -> isTrue(algoOrder.isLive()));
    }

    /**
     * Сторона заявки, уменьшающей позицию сделки: у длинной — продажа, у
     * короткой — покупка (docs/models/domain/core/Order.md). Пусто — у
     * сделки нет торгового направления.
     */
    public Order.Side reducingSide() {
        if (isNull(direction)) {
            return null;
        }
        return StrategyTradeDirection.LONG.equals(direction) ? Order.Side.SELL : Order.Side.BUY;
    }

    /**
     * Живой эпизод позиции сделки по дому — строка, на которой держится
     * конъюнкция «статус {@code ACTIVE} и размер больше нуля», — либо пусто
     * (docs/models/domain/core/Position.md §«Живой риск»;
     * docs/spec/protection-coverage.json, {@code hasLiveEpisode}).
     *
     * <p><b>Активная строка с нулевым размером живым эпизодом не является:</b>
     * она несёт факты эпизода, чья экспозиция уже снята, и её средняя цена
     * якорем себестоимости не служит. Адресуемая строка независимо от
     * размера — второй ответ, {@link #activeEpisode()}, с одним читателем.
     */
    public Position livePosition() {
        return emptyIfNull(positions).stream()
                .filter(episode -> isTrue(episode.hasLiveRisk()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Активная строка эпизода — строка в статусе {@code ACTIVE} НЕЗАВИСИМО
     * от размера — либо пусто (docs/models/domain/core/Position.md
     * §«Живой риск», активная строка эпизода).
     *
     * <p><b>Живого риска не удостоверяет, и читатель у неё один</b> — добыча
     * позиции: сверка пары наблюдения, закрытие прежней строки при смене
     * пары, добыча записи закрытия по строке, которой площадка больше не
     * показывает, и ось эпизода у налитых ног
     * (docs/components/RefreshPositionExecutor.md). Между обнулением позиции
     * на площадке и приходом факта её закрытия два ответа расходятся:
     * строка активна, живого эпизода нет.
     */
    public Position activeEpisode() {
        return emptyIfNull(positions).stream()
                .filter(episode -> Position.Status.ACTIVE.equals(episode.getStatus()))
                .findFirst()
                .orElse(null);
    }

    /** Позиция сделки несёт живой рыночный риск: живой эпизод есть. */
    public Boolean hasLivePositionRisk() {
        return nonNull(livePosition());
    }

    /**
     * Выводит слагаемые экспозиции каждого транша: три собственных — из его
     * заявок и защит, четвёртое — приписанный ему объём закрывающего
     * исполнения уровня сделки (docs/spec/protection-coverage.json,
     * величины {@code trancheCloseAttributed} и {@code dealInTeardown}).
     *
     * <p><b>Правило сопоставления — FIFO по возрасту транша:</b> старшие
     * гасятся целиком, младшие остаются. Замкнутая форма: траншу достаётся
     * его gross-экспозиция минус та часть нетто-размера, которую младшие
     * транши покрыть не могут; отсечки держат величину в {@code [0; gross]}
     * по построению (docs/models/domain/aggregate/DealTranche.md
     * §«Правило сопоставления закрывающего исполнения уровня сделки»).
     *
     * <p><b>Окно атрибуции</b> — координированный выход безусловно,
     * ошибочное и терминальные состояния только при плоской позиции; вне
     * окна приписанное ноль, и сокращение нетто-размера остаётся
     * расхождением сверки.
     * Нетто-размер берётся у живого эпизода: закрытый эпизод несёт
     * необнулённый размер, и читать его значило бы приписывать закрытое
     * заново.
     *
     * <p>Возраст — порядок материализации, то есть идентификатор транша.
     */
    public void deriveTrancheExposures() {
        List<DealTranche> byAge = emptyIfNull(tranches).stream()
                .sorted(Comparator.comparing(DealTranche::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        byAge.forEach(DealTranche::deriveOwnFills);
        BigDecimal netSize = isTrue(hasLivePositionRisk()) ? livePosition().getExternalSize() : BigDecimal.ZERO;
        boolean window = isTrue(inAttributionWindow(netSize));
        BigDecimal youngerGross = BigDecimal.ZERO;
        for (int index = byAge.size() - 1; index >= 0; index--) {
            DealTranche tranche = byAge.get(index);
            BigDecimal gross = tranche.grossExposure();
            tranche.setCloseAttributed(window ? attributedTo(gross, youngerGross, netSize) : BigDecimal.ZERO);
            youngerGross = youngerGross.add(gross);
        }
    }

    /**
     * Окно атрибуции закрытия уровня сделки (величина {@code dealInTeardown}).
     * Терминал входит в окно наравне с ошибочным состоянием: сворачивание,
     * после которого сделка закрылась, состоялось, и закрытое им остаётся
     * приписанным — иначе экспозиция закрытого транша возвращалась бы к
     * налитому объёму на всяком чтении после терминала.
     */
    private Boolean inAttributionWindow(BigDecimal netSize) {
        return Objects.equals(Status.EXIT_PENDING, status)
                || ((Objects.equals(Status.ERROR, status) || isTrue(isTerminal())) && netSize.signum() == 0);
    }

    /** Приписанное траншу: {@code max(0, min(gross, gross + младшие − нетто))}. */
    private BigDecimal attributedTo(BigDecimal gross, BigDecimal youngerGross, BigDecimal netSize) {
        BigDecimal uncovered = gross.add(youngerGross).subtract(netSize);
        return gross.min(uncovered).max(BigDecimal.ZERO);
    }

    /**
     * Живых эпизодов у сделки больше одного — состояние, которого модель
     * не производит: эпизоды сделки последовательны, и второй заводится
     * только после закрытия первого. Наблюдение означает рассогласование
     * учёта, и входные проверки обработчиков читают его как небезопасное
     * состояние (docs/components/TranchePrecheckHandler.md).
     */
    public Boolean moreThanOneLiveEpisode() {
        return emptyIfNull(positions).stream()
                .filter(episode -> isTrue(episode.hasLiveRisk()))
                .count() > 1;
    }

    /** Эпизоды, ждущие положения закрытия: строка закрыта и записи закрытия не несёт. */
    public List<Position> episodesAwaitingCloseRecord() {
        return emptyIfNull(positions).stream()
                .filter(episode -> isTrue(episode.awaitsCloseRecord()))
                .collect(Collectors.toList());
    }

    /**
     * Нижняя граница окна линковки движений средств: durable-колонка, а
     * при её пустоте — суррогат из биржевого момента заведения сделки
     * (docs/spec/cash-flow-linkage.json, {@code lowerBound}).
     *
     * <p><b>Подстановка идёт в чтении, колонку не трогая:</b> различитель
     * провенанса стои́т на её пустоте. Предикат живёт на модели, потому
     * что читателей у него два — конвейер добычи движений и признак
     * полноты разбивки, — и вторая копия разошлась бы с первой.
     */
    public OffsetDateTime billsWindowLowerBound() {
        return nonNull(billsWindowBegin) ? billsWindowBegin : getExternalCreatedAt();
    }

    /**
     * Позиция по сделке наблюдалась: вход исполнялся либо сделка
     * заведена восстановлением уже существующего живого риска. Два
     * дизъюнкта несущие по отдельности — восстановленная сделка своих
     * исполненных ног не имеет, а исполнившаяся стратегийная не имеет
     * RECOVERY (docs/spec/deal-context-load.json, positionObserved).
     */
    public Boolean positionObserved() {
        return EntryReason.RECOVERY == entryReason
                || emptyIfNull(tranches).stream().anyMatch(tranche -> isTrue(tranche.hasEntryFill()));
    }

    /**
     * Позиция по сделке наблюдалась, а строки эпизода в зеркале нет ни
     * одной — эпизодная половина неполного графа.
     *
     * <p><b>Это не нулевая сторона сверки, а ненаблюдённая.</b> Эпизод
     * заводит только добыча позиции, и пока её не было, сумма экспозиций
     * сверяется с нулём, которого на бирже может не быть: у восстановленной
     * сделки заявок нет, и обе стороны сверки нулевые при любом живом
     * риске. Читатель — входные проверки прохода активной сделки
     * (docs/components/DealActiveHandler.md §«Входные проверки»).
     */
    public Boolean episodeNotPresented() {
        return isEmpty(positions) && isTrue(positionObserved());
    }

    /**
     * Граф сделки предъявлен целиком (docs/spec/deal-context-load.json,
     * graphComplete). Загрузка идёт одним заходом на коллекцию, поэтому
     * «предъявлено» решается не пометкой на строке, а НАЛИЧИЕМ коллекции
     * там, где она обязана быть: транши — у всякой сделки, эпизод — если
     * позиция наблюдалась, ноги — если входная заявка отправлялась.
     *
     * <p><b>Удостоверители у эпизодов и ног разные, и это счётно.</b>
     * Уровень «вход отправлялся» ошибается для эпизодов в обе стороны: у
     * восстановленной сделки заявки не было, а эпизод есть; у сделки со
     * снятым до налива входом заявка была, а эпизода нет. Поэтому у
     * эпизодов удостоверитель — «позиция наблюдалась», у ног —
     * durable-колонка нижней границы окна линковки движений.
     *
     * <p><b>Ноги берутся обходом траншей</b> — тем же, которым их читают
     * числа риска: предъявленность меряется у того множества, по которому
     * считают.
     *
     * <p>Предикат живёт на модели, потому что читателей у него два: сборка
     * контекста прохода и писатель чисел риска, чья собственная правка
     * могла граф дополнить.
     */
    public Boolean graphComplete() {
        boolean tranchesComplete = isNotEmpty(tranches);
        boolean episodesComplete = isFalse(episodeNotPresented());
        boolean legsComplete = emptyIfNull(tranches).stream().anyMatch(tranche -> isNotEmpty(tranche.getOrders()))
                || isNull(billsWindowBegin);
        return tranchesComplete && episodesComplete && legsComplete;
    }

    /**
     * Накопленное финансирование сделки — сумма по эпизодам, <b>издержкой
     * положительная</b>: знак этого поля в домене уже нормализован
     * (docs/models/domain/core/Position.md).
     *
     * <p>Величина живёт на модели, потому что читателей у неё два —
     * ценовой результат счётчика серии убытков
     * (docs/rules/loss-streak-halt.md) и операнд терминального события
     * (docs/architecture/contracts.md), — и вторая копия суммы разошлась бы
     * с первой.
     *
     * <p><b>На усечённом графе сумма занижена, и охраняет её не эта
     * модель</b>, а признак полноты графа у читателя: коллекция эпизодов
     * загружается проходом, и предикат «граф предъявлен целиком» стои́т
     * рядом с числом там, где число используется.
     */
    public BigDecimal accumulatedFundingCost() {
        return sumEpisodes(Position::getExternalFundingCost);
    }

    /**
     * Комиссии обеих ног сделки — сумма по эпизодам, приведённая к
     * <b>издержке положительной</b>: в домене у поля СЫРОЙ знак источника
     * (docs/models/domain/core/Position.md), а суммы отчёта объявлены
     * издержкой положительными (docs/rules/statistics-aggregates.md).
     */
    public BigDecimal accumulatedFeeCost() {
        return BigDecimal.ZERO.subtract(sumEpisodes(Position::getExternalFee));
    }

    /**
     * Штраф принудительного закрытия — сумма по эпизодам, приведённая к
     * издержке положительной по тому же доводу, что и комиссия.
     */
    public BigDecimal accumulatedLiquidationPenaltyCost() {
        return BigDecimal.ZERO.subtract(sumEpisodes(Position::getExternalLiquidationPenalty));
    }

    /**
     * Идентичность транша по его ключу; пусто — транша с таким ключом в
     * загруженном графе нет.
     *
     * <p>Числовой ключ границу сервиса не пересекает
     * (.claude/rules/codestyle.md §«Идентичность наружу»), а транши у
     * прохода уже загружены: чтение строки транша ради одного поля было бы
     * запросом по уже прочитанному.
     */
    public String trancheInternalId(Long trancheId) {
        return emptyIfNull(tranches).stream()
                .filter(tranche -> Objects.equals(trancheId, tranche.getId()))
                .findFirst()
                .map(DealTranche::getInternalId)
                .orElse(null);
    }

    /** Сумма поля по эпизодам сделки; пустое слагаемое считается нулём. */
    private BigDecimal sumEpisodes(Function<Position, BigDecimal> field) {
        return emptyIfNull(positions).stream()
                .map(field)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Проскок выхода сделки по стопу — деньги расчётной валюты, на которые
     * исполнение сработавшего стопа хуже его уровня; неблагоприятный
     * положителен, знак не обрезается (docs/spec/stop-exit-slippage.json,
     * величина {@code stopExitSlippage}). Пусто — мера неприменима либо
     * неизмерима, и нулём пустота не подменяется
     * (docs/rules/absent-value-semantics.md).
     *
     * <p><b>Своего поля у величины нет</b>: её считает писатель
     * терминального события по графу прохода, и операнды — граф сделки,
     * которого у читателя нет (docs/components/MarkDealClosedExecutor.md).
     *
     * <p><b>Применимость — конъюнкция, и каждый конъюнкт несущий:</b> сделка
     * закрыта стопом; торговый исход штатный и граф предъявлен целиком;
     * эпизод один — средняя цена выхода у каждого своя; все транши, принявшие
     * риск, закрыты стопом; ни один не сокращал позицию собственной
     * reduce-only ногой — её исполнение лежит в той же средней цене, а
     * причина транша его не выдаёт; хотя бы одна защита сработала, у каждой
     * сработавшей уровень объявлен, и он один на всех; средняя цена выхода
     * добыта; вышедшая экспозиция положительна.
     *
     * @param graphComplete граф сделки предъявлен целиком на момент терминала
     */
    public BigDecimal stopExitSlippage(Boolean graphComplete) {
        if (isFalse(stopExitApplicable(graphComplete))) {
            return null;
        }
        List<BigDecimal> firedLevels = firedStopLevels();
        if (isEmpty(firedLevels) || firedLevels.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        BigDecimal level = firedLevels.getFirst();
        if (firedLevels.stream().anyMatch(fired -> fired.compareTo(level) != 0)) {
            return null;
        }
        BigDecimal exitPrice = positions.getFirst().getExternalCloseAveragePrice();
        BigDecimal exposure = stopExitedExposure();
        if (isNull(exitPrice) || isNull(exposure) || exposure.signum() <= 0) {
            return null;
        }
        BigDecimal perUnit = StrategyTradeDirection.LONG.equals(direction)
                ? level.subtract(exitPrice)
                : exitPrice.subtract(level);
        return perUnit.multiply(exposure);
    }

    /**
     * Структурная половина применимости меры проскока
     * ({@code stopExitMeasurable} без конъюнктов уровня, цены и
     * экспозиции): причина, исход, граф, один эпизод, причины траншей,
     * принявших риск, и отсутствие собственного выхода reduce-only ногой.
     *
     * <p>Транш без налива объём эпизода не двигал и меру не портит, какой бы
     * ни была его причина. Пустое направление делает знак неизмеримым.
     */
    private Boolean stopExitApplicable(Boolean graphComplete) {
        return Objects.equals(CloseReason.STOP_LOSS, closeReason)
                && isTrue(graphComplete)
                && Objects.equals(CloseOutcome.NORMAL_EXIT, closeOutcome)
                && nonNull(direction)
                && emptyIfNull(positions).size() == 1
                && emptyIfNull(tranches).stream().noneMatch(tranche -> isTrue(tranche.hasEntryFill())
                        && isFalse(Objects.equals(DealTranche.CloseReason.STOP_LOSS, tranche.getCloseReason())))
                && emptyIfNull(tranches).stream().noneMatch(tranche -> nonNull(tranche.getReduceOnlyFilled())
                        && tranche.getReduceOnlyFilled().signum() > 0);
    }

    /**
     * Уровни срабатывания всех защит сделки, сработавших у площадки, — по
     * всем траншам и обоим носителям; {@code null} в перечне — сработавшая
     * защита без объявленного уровня (трейлинг, тейк): её исполнение лежит в
     * той же средней цене выхода, а сравнить его не с чем.
     *
     * <p>Уровень встроенной защиты — объявленная цена срабатывания; у
     * отдельной — уровень её условия, и только у фиксированного стопа: у
     * трейлинга персистится последний НАБЛЮДЁННЫЙ уровень, и сравнение с ним
     * занижало бы проскок.
     */
    private List<BigDecimal> firedStopLevels() {
        List<BigDecimal> levels = new ArrayList<>();
        for (DealTranche tranche : emptyIfNull(tranches)) {
            emptyIfNull(tranche.getOrders()).stream()
                    .flatMap(order -> emptyIfNull(order.getAttachedAlgoOrders()).stream())
                    .filter(protection -> AttachedAlgoOrder.CloseReason.TRIGGERED.equals(protection.getCloseReason()))
                    .forEach(protection -> levels.add(protection.getStopLossTriggerPrice()));
            emptyIfNull(tranche.getAlgoOrders()).stream()
                    .filter(algo -> AlgoOrder.CloseReason.TRIGGERED.equals(algo.getCloseReason()))
                    .forEach(algo -> levels.add(FIXED_STOP_TYPES.contains(algo.getConditionType())
                            ? algo.stopLevel()
                            : null));
        }
        return levels;
    }

    /**
     * Экспозиция, вышедшая по стопу, в единицах базового актива: налив
     * входных ног, помноженный на стоимость контракта, под которую нога
     * сайзилась (docs/spec/stop-exit-slippage.json, величина
     * {@code exitedExposure}). Объёму, закрытому стопами, она равна только
     * под применимостью меры. Налитая нога без стоимости контракта делает
     * величину неизмеримой — пусто, а не недосчитанная сумма.
     */
    private BigDecimal stopExitedExposure() {
        List<Order> filledEntryLegs = emptyIfNull(tranches).stream()
                .flatMap(tranche -> emptyIfNull(tranche.getOrders()).stream())
                .filter(order -> isTrue(order.isEntryLeg()))
                .filter(order -> nonNull(order.getAccumulatedFillSize())
                        && order.getAccumulatedFillSize().signum() > 0)
                .collect(Collectors.toList());
        if (filledEntryLegs.stream().anyMatch(order -> isNull(order.getPlannedContractValue()))) {
            return null;
        }
        return filledEntryLegs.stream()
                .map(order -> order.getAccumulatedFillSize().multiply(order.getPlannedContractValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Причина закрытия сделки по СТАРШИНСТВУ причин её траншей: берётся
     * старшая (наименьший ранг) среди закрывшихся
     * (docs/spec/deal-lifecycle.json §trancheCloseReasonRank,
     * §dealCloseReasonBySeniority). Пусто — ни один транш причины не
     * назвал, и подставлять благоприятную нечем.
     *
     * <p>Порядок — не порядок закрытия: транш, закрывшийся первым, не
     * получает этим старшинства. Ранг у причин один на модель, и здесь он
     * читается, а не переизобретается.
     */
    public CloseReason closeReasonBySeniority() {
        return emptyIfNull(tranches).stream()
                .map(DealTranche::getCloseReason)
                .filter(Objects::nonNull)
                .min(java.util.Comparator.comparingInt(Deal::trancheReasonRank))
                .map(Deal::toDealReason)
                .orElse(null);
    }

    /**
     * Ранг причины транша: 1 — старшая. Перечень причин транша покрыт
     * целиком, и ветви умолчания нет намеренно: значение, добавленное в
     * перечень без ранга, роняет компиляцию, а не получает ранг молча.
     * Пустую причину вызывающий отсекает до вызова.
     */
    private static int trancheReasonRank(DealTranche.CloseReason reason) {
        return switch (reason) {
            case EXTERNAL_CLOSE -> 1;
            case RISK_CONTROL -> 2;
            case STOP_LOSS -> 3;
            case STRATEGY_EXIT -> 4;
            case TAKE_PROFIT -> 5;
            case ENTRY_CONDITION_EXPIRED -> 6;
        };
    }

    /**
     * Причина, которую НАСЛЕДУЕТ транш, закрытый каскадом сворачивания
     * сделки; пусто — наследовать нечего.
     *
     * <p><b>Наследование, а не своё значение.</b> Транш, закрытый
     * каскадом, закрылся не по своей причине: инициатор у выхода
     * сделочный. Значение {@code DEAL_EXIT} удвоило бы перечень тем, что
     * уже выражено (docs/lifecycles/DealTranche.md §«Писатель причины
     * закрытия транша — обработчик терминального ребра»).
     *
     * <p><b>Аварийная причина не наследуется.</b> {@code EMERGENCY_CLOSE}
     * у транша не пишется вовсе: аварийная тропа сделочная, и FSM траншей
     * на ней не прогоняется.
     */
    public DealTranche.CloseReason inheritedTrancheCloseReason() {
        if (isNull(closeReason)) {
            return null;
        }
        return switch (closeReason) {
            case EXTERNAL_CLOSE -> DealTranche.CloseReason.EXTERNAL_CLOSE;
            case ENTRY_CONDITION_EXPIRED -> DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED;
            case STRATEGY_EXIT -> DealTranche.CloseReason.STRATEGY_EXIT;
            case TAKE_PROFIT -> DealTranche.CloseReason.TAKE_PROFIT;
            case STOP_LOSS -> DealTranche.CloseReason.STOP_LOSS;
            case RISK_CONTROL -> DealTranche.CloseReason.RISK_CONTROL;
            case EMERGENCY_CLOSE -> null;
        };
    }

    /**
     * Причина транша в перечне сделки. Каждая причина транша есть и у
     * сделки, поэтому перевод полный и ветви умолчания нет; пустую причину
     * вызывающий отсекает до вызова.
     */
    private static CloseReason toDealReason(DealTranche.CloseReason reason) {
        return switch (reason) {
            case EXTERNAL_CLOSE -> CloseReason.EXTERNAL_CLOSE;
            case RISK_CONTROL -> CloseReason.RISK_CONTROL;
            case STOP_LOSS -> CloseReason.STOP_LOSS;
            case STRATEGY_EXIT -> CloseReason.STRATEGY_EXIT;
            case TAKE_PROFIT -> CloseReason.TAKE_PROFIT;
            case ENTRY_CONDITION_EXPIRED -> CloseReason.ENTRY_CONDITION_EXPIRED;
        };
    }

    /**
     * Порог доказанного покрытия двигается ТОЛЬКО вперёд: число
     * наблюдений равно числу закрывшихся эпизодов, и порог обязан
     * накрывать движения всех.
     */
    public void advanceCoverageProvenThrough(OffsetDateTime observedAt) {
        if (nonNull(observedAt) && (isNull(coverageProvenThrough) || coverageProvenThrough.isBefore(observedAt))) {
            coverageProvenThrough = observedAt;
        }
    }

    /**
     * FSM-статус сделки: бизнес-этап, не статус Order/AlgoOrder/Position
     * и не exchange ACK. Значения, группы и переходы —
     * docs/lifecycles/Deal.md.
     */
    public enum Status {

        /**
         * Сделка ведётся: транши живут своими жизненными циклами внутри
         * неё. Стадии входа и сопровождения принадлежат ТРАНШУ, не сделке
         * ({@link DealTranche.Status}) — сделка их не повторяет.
         */
        ACTIVE,

        /** Запущено сворачивание — ждём завершения закрывающих действий. */
        EXIT_PENDING,

        /** Ошибка цикла сделки. */
        ERROR,

        /** Сделка завершена штатно. */
        CLOSED,

        /** Сделка завершена аварийно (emergency close). */
        EMERGENCY_CLOSED
    }

    /** Причина создания сделки (не управляет FSM). */
    public enum EntryReason {

        /** Создана EntryScannerJob по условиям. */
        STRATEGY,

        /** Восстановление существующего runtime risk. */
        RECOVERY
    }

    /** Причина запуска graceful shutdown / controlled close. Не заменяет closeReason. */
    public enum ShutdownReason {

        /** Стратегия удалена. */
        STRATEGY_DELETED,

        /** Устаревание рыночных данных (только если policy решила завершать сделку). */
        MARKET_DATA_EXPIRED,

        /**
         * Жёсткая ступень пары, любым поводом: значение — имя радиуса, а
         * повод несёт код отчёта (docs/lifecycles/Deal.md).
         */
        RISK_POLICY,

        /** Exchange hold. */
        EXCHANGE_HOLD
    }

    /**
     * Торговый исход закрытия позиции сделки — рыночный факт, а не
     * бизнес-причина. Резолв и старшинство по эпизодам —
     * docs/models/mapping/PositionCloseResult.md.
     */
    public enum CloseOutcome {

        /** Позицию закрыли мы либо биржевое событие вне ликвидационного контура. */
        NORMAL_EXIT,

        /** Принудительное закрытие по марже. */
        LIQUIDATION,

        /** Принудительное сокращение. */
        FORCED_REDUCTION,

        /**
         * Торговый исход <b>не установлен</b> — значение, а не пустота:
         * сделка остаётся в популяции и счётна отдельной корзиной.
         */
        UNDETERMINED
    }

    /**
     * Исход сверки разбивки движений с записями закрытия эпизодов.
     * Третьего значения нет: различение «были обязаны и не посчитали»
     * несёт терминальный статус, а не признак.
     */
    public enum ReconciliationStatus {

        /** Сверка не была обязана: хотя бы один конъюнкт обязанности не выполнен. */
        NOT_RUN,

        /** Общее расхождение по четырём парам в пределах допуска. */
        MATCHED,

        /** Общее расхождение сверх допуска. */
        MISMATCHED
    }

    /** Полнота разбивки движений: накрыло ли окно добычи всю жизнь сделки. */
    public enum BreakdownCompleteness {

        /** Возраст нижней границы окна на момент добычи не превышает глубины доступности движений. */
        COMPLETE,

        /** Превышает: часть движений старше доступной глубины и в разбивку не попала. */
        INCOMPLETE_BY_WINDOW,

        /** Сравнение не выполнялось — добыча движений не выполнялась (единственный триггер). */
        NOT_ASSESSED
    }

    /** Почему знаменатель {@code R} пуст или непуст. */
    public enum RiskBenchmarkAvailability {

        /** Знаменатель определён. */
        AVAILABLE,

        /** Входа не было, поэтому знаменателя нет ПО ПОСТРОЕНИЮ — нормальная популяция. */
        NOT_APPLICABLE,

        /** Вход был, а знаменателя нет — аномалия с отчётом. */
        MISSING
    }

    /** Итоговая бизнес-причина завершения сделки (не технический механизм закрытия). */
    public enum CloseReason {

        /**
         * Позицию закрыл кто-то извне системы. Клетка ребра
         * {@code EXIT_PENDING → CLOSED} без инициатора выхода закрывается
         * им; значение СТАРШЕЕ в порядке старшинства причин траншей
         * (docs/spec/deal-lifecycle.json §trancheCloseReasonRank).
         */
        EXTERNAL_CLOSE,

        /** Candidate закрыт в PRECHECK до live risk. */
        ENTRY_CONDITION_EXPIRED,

        /** Штатный выход по стратегии. */
        STRATEGY_EXIT,

        /** Take-profit. */
        TAKE_PROFIT,

        /** Stop-loss (включая fixed и trailing; механизм — в Order/AlgoOrder/DealActionState/audit). */
        STOP_LOSS,

        /** Штатное risk-control завершение (включая risk-block в PRECHECK). */
        RISK_CONTROL,

        /** Аварийное закрытие (только для EMERGENCY_CLOSED). */
        EMERGENCY_CLOSE
    }
}
