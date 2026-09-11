package com.example.tradingcore.domain.command;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.tradingcore.domain.market.MarketFeatures;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;

/**
 * Runtime-картина одного прохода машины состояний: то, что нужно для
 * обработки сделки прямо сейчас. Не хранится и частью доменной модели
 * сделки не является.
 *
 * <p><b>Универсальным контейнером свежих рыночных данных контекст не
 * является:</b> для расчёта собирается отдельный свежий контекст на КАЖДОЕ
 * действие. Фичи момента лежат здесь потому, что их потребляют УСЛОВИЯ
 * ШАГОВ, а не расчёт (docs/components/models/DealContext.md).
 *
 * <p><b>Заявки и позиции отдельными полями не дублируются:</b> ноги входят
 * в граф через свои транши, эпизоды позиции представлены строками сделки.
 * <b>Строки разбивки движений — отдельная коллекция</b>, потому что в
 * агрегате сделки они не живут, а по ним считаются левые стороны пар
 * сверки.
 *
 * <p><b>Признаки полноты — поля, а не производные читателей.</b> Читатель
 * берёт готовый ответ и НЕ пересобирает его: иначе каждый обязан был бы
 * знать объём загрузки. Формулы признаков — docs/spec/deal-context-load.json,
 * объёмы — docs/components/DealContextService.md; здесь они не
 * пересказываются.
 *
 * <p><b>Собирает контекст его фабрика, а не этот класс.</b> Модель —
 * операнд прохода: диспетчер команд, обработчики и исполнители получают
 * её готовой.
 */
@Value
@Builder
public class DealContext {

    /** Сделка с графом: транши, их ноги и встроенные защиты, эпизоды позиции. */
    Deal deal;

    /**
     * Биржевой счёт с ТОРГОВЫМ состоянием: база риска, серия убытков,
     * ступень, счётчик слепоты (docs/models/domain/core/ExchangeAccount.md).
     */
    ExchangeAccount exchangeAccount;

    /** Торговый инструмент сделки. */
    Instrument instrument;

    /**
     * Закреплённая деталь стратегии. <b>Пуста у восстановленной сделки</b>
     * — у неё закреплять было нечего; это единственное поле состава, чья
     * пустота объяснена тропой создания, а не недогрузом
     * (docs/models/domain/aggregate/Deal.md).
     */
    StrategyDetail strategyDetail;

    /**
     * <b>Копия определения — владельца закреплённой детали</b>, ради её
     * административного статуса. Пуста там же, где пуста деталь: у
     * восстановленной сделки и у детали, чьей копии больше нет.
     *
     * <p>Читается копия <b>владельца детали</b>, а не активная копия пары:
     * сделка ведётся по закреплённой детали, а активная копия к моменту
     * прохода может быть уже другой.
     */
    Strategy strategy;

    /**
     * Последний снимок средств счёта. <b>Свежесть контекст не
     * гарантирует</b> — её проверяет обработчик перед чувствительным к
     * риску действием.
     */
    BalanceContainer balanceContainer;

    /**
     * Строки исполнения сделки — стратегийные и системные вместе.
     *
     * <p>Список <b>изменяемый по построению</b>: строка, заведённая этим
     * же проходом, обязана быть видна анкеру команды, а контекст собран до
     * неё ({@link #register(DealActionState)}).
     */
    List<DealActionState> actionStates;

    /** Строки разбивки движений средств сделки. */
    List<DealCashFlow> cashFlows;

    /**
     * Фичи момента прохода — значения индикаторов и их предыдущие
     * значения, структуры, цены и фаза. Потребители — условия шагов.
     *
     * <p><b>Собраны ОДНИМ чтением у владельца.</b> Россыпь чтений по
     * операнду собрала бы контекст из значений разных моментов, и правило
     * пересечения сравнило бы величины, не существовавшие одновременно.
     *
     * <p><b>Фаза здесь — вычисленное значение, а не загруженная
     * сущность</b> (docs/models/domain/other/MarketPhase.md): персист-слоя
     * у неё нет.
     *
     * <p>Пусто у сделки <b>без закреплённой детали</b>: шагов у неё нет, и
     * оценивать нечего.
     */
    MarketFeatures marketFeatures;

    /**
     * <b>Граф сделки предъявлен целиком.</b> Ложен — писатели четвёрки
     * чисел риска не пишут и своё звено не завершают, а преконтроль
     * отказывает сразу: на неполном графе операнды потолков занижены, то
     * есть ошибка направлена в разрешающую сторону
     * (docs/models/domain/aggregate/Deal.md).
     */
    Boolean graphComplete;

    /**
     * <b>Разбивка движений добыта И предъявлена целиком.</b> Конъюнкты
     * разные по природе: первый — что добыча вообще выполнялась, второй —
     * что предъявленное не усечено потолком выборки. Полноты графа среди
     * них нет: строки разбивки в операнды четвёрки чисел риска не входят.
     */
    Boolean flowsComplete;

    /**
     * <b>Гейт звена, считающего итоговое число сделки:</b> предъявлены обе
     * половины — граф даёт правые операнды пар сверки, разбивка левые. Не
     * путать с доступностью самого числа: та строже и требует сверх этого
     * добытости движений, положений закрытия и резолва курса
     * (docs/spec/deal-result.json).
     */
    Boolean computationAllowed;

    /**
     * Явный конструктор — ради изменяемости списка строк исполнения:
     * строку, заведённую этим проходом, регистрирует
     * {@link #register(DealActionState)}, и вызывающая сторона не обязана
     * подавать сюда именно изменяемый список.
     */
    private DealContext(Deal deal, ExchangeAccount exchangeAccount, Instrument instrument,
                        StrategyDetail strategyDetail, Strategy strategy,
                        BalanceContainer balanceContainer,
                        List<DealActionState> actionStates, List<DealCashFlow> cashFlows,
                        MarketFeatures marketFeatures, Boolean graphComplete, Boolean flowsComplete,
                        Boolean computationAllowed) {
        this.deal = deal;
        this.exchangeAccount = exchangeAccount;
        this.instrument = instrument;
        this.strategyDetail = strategyDetail;
        this.strategy = strategy;
        this.balanceContainer = balanceContainer;
        this.actionStates = new ArrayList<>(emptyIfNull(actionStates));
        this.cashFlows = new ArrayList<>(emptyIfNull(cashFlows));
        this.marketFeatures = marketFeatures;
        this.graphComplete = graphComplete;
        this.flowsComplete = flowsComplete;
        this.computationAllowed = computationAllowed;
    }

    /**
     * <b>Определение сделки удалено</b> — сворачивание запущено
     * (docs/components/DealActiveHandler.md §«Выходные проверки»).
     *
     * <p>Пустая копия <b>ложна</b>, а не неизвестна, и это направление
     * выбрано: у восстановленной сделки определения нет вовсе, и удалять
     * там нечего — свернуть её по пустоте значило бы закрыть живой риск по
     * отсутствию операнда.
     */
    public Boolean strategyDeleted() {
        return nonNull(strategy) && isTrue(strategy.isDeleted());
    }

    /**
     * Идентичность определения, по которому ведётся сделка; пусто — сделка
     * заведена восстановлением, и определения у неё не было
     * (docs/rules/absent-value-semantics.md).
     *
     * <p>Ответ живёт здесь, а не у читателей: его берут писатели <b>трёх</b>
     * классов событий сделки, и три копии одного чтения разошлись бы первой
     * же правкой. Числовой ключ детали при этом наружу не выходит — событию
     * нужна идентичность (.claude/rules/codestyle.md §«Идентичность
     * наружу»).
     */
    public String strategyInternalId() {
        return isNull(strategy) ? null : strategy.getInternalId();
    }

    /**
     * Контекст оценки условий шага этого прохода.
     *
     * <p><b>Собирается здесь, потому что здесь лежат ОБЕ половины</b> —
     * фичи момента и факты сделки. У фич своей половины фактов нет, у
     * фактов — своих фич; собранный где-то ещё, контекст читал бы одну из
     * них через посредника.
     *
     * <p><b>Время оценки ставит читатель, а не владелец данных.</b> Оно
     * точка отсчёта {@code TIME}-операндов, то есть свойство момента
     * РЕШЕНИЯ; часы владельца отвечали бы на вопрос о моменте чтения.
     *
     * <p>Цена в контексте — последняя цена сделки: скалярный операнд
     * {@code PRICE} грамматики условий один, а прочие ценовые домены
     * читают калькуляторы у самого снимка
     * (docs/components/models/MarketPriceData.md).
     *
     * @param tranche транш, чей шаг оценивается; пусто у шага уровня сделки
     */
    public ConditionEvaluationContext conditionContext(DealTranche tranche) {
        return conditionOperands()
                .evaluationTime(OffsetDateTime.now(ZoneOffset.UTC))
                .entryMarketPhase(isNull(deal) ? null : deal.getEntryMarketPhase())
                .direction(isNull(deal) ? null : deal.getDirection())
                .activePosition(isNull(deal) ? null : deal.livePosition())
                .tranche(tranche)
                .build();
    }

    /**
     * Рыночная половина контекста; собирает её сама связка фич — второй
     * сборщик разошёлся бы с первым при вводе нового рыночного операнда
     * грамматики. У сделки без снятых фич половина пуста, и предикаты на
     * её операндах консервативно ложны.
     */
    private ConditionEvaluationContext.ConditionEvaluationContextBuilder conditionOperands() {
        return isNull(marketFeatures)
                ? MarketFeatures.absentConditionOperands()
                : marketFeatures.conditionOperands();
    }

    /**
     * <b>База риска</b> — делитель всех потолков и сайзинга: снимок базы
     * сделки, если он есть, иначе живая база счёта
     * (docs/spec/risk-limits.json, величина {@code base}).
     *
     * <p>Читателей у неё двое — преконтроль и контекст расчёта, — и
     * вторая копия развилки разошлась бы с первой ровно там, где
     * расхождение означает разные потолки у проверки и у размера.
     */
    public BigDecimal riskBase() {
        BigDecimal frozen = isNull(deal) ? null : deal.getPlannedRiskEquityBase();
        if (nonNull(frozen)) {
            return frozen;
        }
        return isNull(exchangeAccount) ? null : exchangeAccount.getRiskBase();
    }

    /**
     * Зарегистрировать строку исполнения, заведённую этим проходом.
     *
     * <p>Контекст собирается до неё, а анкер команды обязан резолвиться
     * сразу: без регистрации два запроса одного системного действия за
     * проход завели бы две живые строки и столкнулись бы на частичном
     * ключе.
     */
    public void register(DealActionState state) {
        if (nonNull(state) && isFalse(actionStates.contains(state))) {
            actionStates.add(state);
        }
    }

    /**
     * Объявление, по которому материализован транш; пусто у
     * восстановленной сделки (объявления у неё нет) и у сделки без
     * закреплённой детали.
     */
    public StrategyTranche declarationOf(DealTranche tranche) {
        if (isNull(strategyDetail) || isNull(tranche)) {
            return null;
        }
        return strategyDetail.declarationById(tranche.getStrategyTrancheId());
    }

    /**
     * Допускает ли объявление транша переоткрытие его эпизода. Признак
     * живёт на ОБЪЯВЛЕНИИ, а не на детали: сетка и одиночный вход в одной
     * фазе вправе решать это по-разному. Пусто читается как «не
     * допускает» — разрешение объявляется явно.
     */
    public Boolean reopenAllowed(DealTranche tranche) {
        StrategyTranche declaration = declarationOf(tranche);
        return nonNull(declaration) && isTrue(declaration.getPositionReopenAllowed());
    }

    /**
     * Признак покрытия <b>по уровню строки исполнения</b>: у потраншевой —
     * покрытие её транша, у строки уровня сделки — агрегатная
     * достаточность (docs/spec/protection-coverage.json).
     *
     * <p><b>Операнд выбирается уровнем, и это не косметика.</b> Триггер
     * исчерпания бюджета срабатывает и на исполнениях уровня сделки, у
     * которых транша нет вовсе; траншевый признак безусловно давал бы у
     * них «не резолвится» — то есть жёсткую счётную ступень с flatten
     * всего счёта там, где исход объявлен мягким
     * (docs/rules/instrument-hold.md §«Форма реакции на исчерпание
     * бюджета попыток»).
     *
     * <p>Транш, которого в графе нет, читается как <b>непокрытый</b>:
     * «не резолвится» и «нарушен» на этой тропе дают один исход, и
     * ошибаться здесь следует в сторону меньшего доверия.
     */
    public Boolean coveredAtLevelOf(DealActionState state) {
        if (isNull(deal)) {
            return false;
        }
        if (isNull(state) || isFalse(state.isTrancheLevel())) {
            return deal.allTranchesCovered();
        }
        return emptyIfNull(deal.getTranches()).stream()
                .filter(tranche -> Objects.equals(state.getDealTrancheId(), tranche.getId()))
                .findFirst()
                .map(DealTranche::isCovered)
                .orElse(false);
    }

    /**
     * Строка исполнения СТРАТЕГИЙНОГО действия. Отбор дискриминируется
     * УРОВНЕМ объявления, а не разными вызовами:
     *
     * <ul>
     *   <li>потраншевое — тройка «действие + транш + номер эпизода»:
     *       переоткрытие ведётся тем же траншем, поэтому без номера
     *       эпизода строки прошлого эпизода неотличимы от строк
     *       текущего;</li>
     *   <li>агрегатное — пара «действие + сделка»: транша у него нет ни
     *       одного, и пустой транш участвует в отборе как пустой, а не
     *       как «любой».</li>
     * </ul>
     *
     * <p>Общий вход, а не два: вызывающий держит уровень в одном операнде
     * — самом транше, — и ветвление здесь не даёт ему выбрать не тот
     * отбор.
     */
    public Optional<DealActionState> actionState(Long strategyActionId, DealTranche tranche) {
        if (isEmpty(actionStates)) {
            return Optional.empty();
        }
        return actionStates.stream()
                .filter(state -> isFalse(state.isSystem()))
                .filter(state -> Objects.equals(strategyActionId, state.getStrategyActionId()))
                .filter(state -> matchesLevel(state, tranche))
                .findFirst();
    }

    /**
     * Строки исполнения действий ЭТОГО шага на текущем эпизоде объекта
     * шага — операнд признака применённости и гейта повтора
     * (docs/spec/strategy-walkthrough.json, величины
     * {@code stepActionRowsApplied} и {@code stepFailedRowOnEpisode}).
     *
     * <p>Отбор по уровню тот же, что у {@link #actionState}: у
     * потраншевого шага одного идентификатора транша не хватает —
     * переоткрытие идёт тем же траншем, и строки прошлого эпизода
     * неотличимы от строк текущего без номера эпизода.
     */
    public List<DealActionState> stepActionStates(StrategyStep step, DealTranche tranche) {
        if (isNull(step) || isEmpty(step.getActions())) {
            return List.of();
        }
        Set<Long> declared = step.getActions().stream()
                .map(StrategyAction::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return actionStates.stream()
                .filter(state -> isFalse(state.isSystem()))
                .filter(state -> declared.contains(state.getStrategyActionId()))
                .filter(state -> matchesLevel(state, tranche))
                .collect(Collectors.toList());
    }

    /**
     * ЖИВЫЕ строки стратегийного исполнения своего уровня — то, что этот
     * проход обязан продвинуть прежде, чем начинать новое.
     *
     * <p><b>Продвижение живого исполнения отбором шага не гейтится.</b>
     * Строка на действие в эпизоде одна, и признак применённости шага
     * стои́т на её существовании (docs/rules/strategy-step-once-per-episode.md):
     * пропусти́ проход живую строку через отбор — одно-действенный шаг
     * выпал бы из набора сразу после заведения строки, и его команда
     * второй стадии не была бы выдана никогда.
     */
    public List<DealActionState> liveStrategyActionStates(DealTranche tranche) {
        return actionStates.stream()
                .filter(state -> isFalse(state.isSystem()))
                .filter(state -> isTrue(state.isLive()))
                .filter(state -> matchesLevel(state, tranche))
                .sorted(Comparator.comparing(DealActionState::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    /**
     * ЖИВАЯ строка исполнения системного действия названного типа и
     * уровня. Завершённые и отказавшие живыми не считаются: новая
     * надобность заводит новую строку.
     */
    public Optional<DealActionState> liveSystemActionState(SystemActionType type, DealTranche tranche) {
        return actionStates.stream()
                .filter(state -> isTrue(state.isSystem()))
                .filter(state -> Objects.equals(type, state.getSystemActionType()))
                .filter(state -> matchesLevel(state, tranche))
                .filter(state -> isTrue(state.isLive()))
                .findFirst();
    }

    /** Строки исполнения системного действия названного типа — любых статусов и уровней. */
    public List<DealActionState> systemActionStates(SystemActionType type) {
        return actionStates.stream()
                .filter(state -> isTrue(state.isSystem()))
                .filter(state -> Objects.equals(type, state.getSystemActionType()))
                .collect(Collectors.toList());
    }

    private boolean matchesLevel(DealActionState state, DealTranche tranche) {
        if (isNull(tranche)) {
            return isNull(state.getDealTrancheId());
        }
        return Objects.equals(tranche.getId(), state.getDealTrancheId())
                && Objects.equals(tranche.getEpisodeSeq(), state.getTrancheEpisodeSeq());
    }
}
