package com.example.tradingcore.domain.command.risk;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Результат ОДНОЙ конкретной проверки внутри вердикта преконтроля
 * (docs/components/models/RiskCheckResult.md).
 *
 * <p>Отдельного поля предела нет: не у каждой проверки один понятный
 * лимит, а у сложных их несколько — при надобности предел кладётся в
 * детали.
 *
 * <p>Живёт только в памяти прохода, поэтому неизменяемая форма здесь
 * дефолт (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
@Value
@Builder
public class RiskCheckResult {

    /** Машинный код проверки. */
    RiskCheckCode code;

    /** Исход конкретной проверки. */
    RiskCheckStatus status;

    /** Фактическое значение, если проверка числовая. */
    BigDecimal actualValue;

    /** Короткое пояснение. */
    String comment;

    /** Детали для диагностики. */
    Map<String, Object> details;

    /** Блокирующая проверка с пояснением и фактическим значением. */
    public static RiskCheckResult blocked(RiskCheckCode code, String comment, BigDecimal actualValue) {
        return RiskCheckResult.builder()
                .code(code)
                .status(RiskCheckStatus.BLOCKED)
                .comment(comment)
                .actualValue(actualValue)
                .build();
    }

    /** Исход конкретной risk-проверки. */
    public enum RiskCheckStatus {

        /** Проверка пройдена. */
        PASSED,

        /** Предупреждение — действие не блокируется. */
        WARNING,

        /** Действие заблокировано этой проверкой. */
        BLOCKED
    }

    /**
     * Машинный код risk-проверки.
     *
     * <p><b>Перечень шире, чем производит преконтроль,</b> и это названо
     * домом: инварианты частичного выхода и состояния позиции проверяет
     * обработчик минимальными проверками, а не риск-политика
     * (docs/rules/risk-validator-scope.md §«Не вызывается»).
     */
    public enum RiskCheckCode {

        /** Риск действия выше ПОАКТНОГО потолка — расхождение расчёта. */
        RISK_PER_ACTION_EXCEEDED(true),

        /**
         * Размер поднят до минимального лота инструмента и на нём вышел за
         * поактный потолок. НЕ расхождение расчёта: ветвь подъёма до
         * минимума потолком не ограничена вовсе
         * (docs/spec/order-sizing.json, величина entryWithinRiskBudget).
         * Разведение с соседним кодом делает карв-аут разрешимым: у
         * эталона при малой базе неделимый лот превышает бюджет КАЖДЫМ
         * входом, и сделка без живого риска уходила бы в аварийный контур
         * по ожидаемому отказу.
         */
        SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET(true),

        /** Взятое сделкой за жизнь плюс риск акта выше КУМУЛЯТИВНОГО потолка. */
        RISK_PER_DEAL_CUMULATIVE_EXCEEDED(false),

        /** Живой риск плюс риск акта выше максимума ОДНОВРЕМЕННОГО риска стратегии. */
        RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED(false),

        /** То же против ГЛОБАЛЬНОГО максимума риск-аппетита. */
        RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED(false),

        /** Нотинал живых ног, живого эпизода и акта выше КАТАСТРОФИЧЕСКОГО потолка сделки. */
        DEAL_NOTIONAL_EXCEEDED(false),

        /**
         * Уровень стопа на убыточной стороне ближе якоря, чем round-trip
         * комиссия: такой стоп срабатывает в убыток даже без движения цены
         * (docs/spec/stop-distance.json).
         */
        STOP_DISTANCE_BELOW_FLOOR(true),

        /** Расчётная валюта инструмента не резолвлена — числам риска не в чем меряться. */
        INSTRUMENT_SETTLE_CURRENCY_MISSING(true),

        /**
         * Сделка в окне сворачивания, а проверяемый акт создаёт риск
         * (docs/rules/exit-teardown-order.md). ВРЕМЕННЫЙ, и реакция —
         * карв-аут: сделка идёт к выходу, действие не исполняется.
         */
        RISK_CREATING_UNDER_COLLAPSE(false),

        /**
         * Инструмент действия стои́т в safety-ступени, а класс акта входит
         * в её блок-сет (docs/rules/instrument-hold.md §Enforcement).
         * ВРЕМЕННЫЙ: возобновление — снятием ступени.
         */
        INSTRUMENT_SAFETY_HOLD(false),

        /** Плечо выше биржевого максимума инструмента. */
        EXCHANGE_MAX_LEVERAGE_EXCEEDED(true),

        /** Режим маржи пары «счёт, инструмент» не изолированный. */
        MARGIN_MODE_NOT_ISOLATED(true),

        /** Обнаружен borrow/debt — торгуем только своими средствами. */
        BORROW_OR_DEBT_DETECTED(true),

        /** Свободных средств не хватает на действие — ожидаемый исход легитимной стратегии. */
        BALANCE_NOT_ENOUGH(false),

        /** Снимок средств несвежий — операнд не добыт. */
        BALANCE_NOT_FRESH(false),

        /** База риска пуста либо непозитивна — делителя потолков не существует. */
        BALANCE_INVALID(false),

        /** Размер ниже минимального размера инструмента. */
        SIZE_BELOW_MIN(true),

        /** Размер не кратен шагу лота. */
        SIZE_LOT_STEP_INVALID(true),

        /** Размер выше per-order лимита инструмента. */
        SIZE_ABOVE_LIMIT(true),

        /** Уровень остановки убытка на неверной стороне относительно якоря. */
        STOP_LOSS_INVALID_SIDE(true),

        /** Уровень фиксации прибыли на неверной стороне относительно якоря. */
        TAKE_PROFIT_INVALID_SIDE(true),

        /** Стоп не первым на пути к ликвидации. */
        STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION(true),

        /** Risk-creating вход без резолвимого уровня остановки убытка. */
        RISK_CREATING_ENTRY_WITHOUT_STOP(true),

        /** Частичный выход не reduce-only (инвариант обработчика). */
        PARTIAL_EXIT_NOT_REDUCE_ONLY(true),

        /** Частичный выход увеличивает позицию (инвариант обработчика). */
        PARTIAL_EXIT_INCREASES_POSITION(true),

        /** Прямое частичное закрытие позиции запрещено (инвариант обработчика). */
        DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN(true),

        /** Обнаружено более одной позиции по инструменту. */
        MULTIPLE_POSITIONS_DETECTED(true),

        /** Состояние позиции неизвестно. */
        POSITION_STATE_UNKNOWN(false),

        /** Инструмент не торгуется. */
        INSTRUMENT_NOT_LIVE(true),

        /** Справочные правила инструмента не материализованы. */
        INSTRUMENT_RULES_MISSING(true),

        /** Рассчитанное действие невалидно: размера нет либо он непозитивен. */
        CALCULATED_ACTION_INVALID(true),

        /**
         * Покрытие транша после завершения действия ниже его экспозиции
         * (docs/rules/live-risk-protection.md). ВРЕМЕННЫЙ: снятие
         * откладывается, а не отказывается — покрытие подтвердится
         * следующим проходом, и всё это время позицию держит прежняя
         * защита.
         */
        PROTECTION_COVERAGE_REDUCED(false),

        /**
         * Прогнозная ставка комиссии не резолвится. ВРЕМЕННЫЙ: ставку
         * приносит тик синка (docs/models/domain/other/TradeFeeRate.md).
         */
        FEE_RATE_UNAVAILABLE(false),

        /**
         * Максимальный риск на сделку не назначен держателем — сверять не с
         * чем (docs/rules/risk-policy.md §«Числа назначает держатель;
         * пустое место — отказ»). ВРЕМЕННЫЙ: число приходит НАЗНАЧЕНИЕМ
         * держателя на строку риск-аппетита тенанта, а не правкой
         * стратегии — то есть снаружи прохода, но без правки определения.
         */
        RISK_APPETITE_NOT_CONFIGURED(false),

        /**
         * Порог остановки по серии убытков не назначен — энфорсера
         * остановки не существует (docs/rules/loss-streak-halt.md).
         * ВРЕМЕННЫЙ по той же причине.
         */
        LOSS_LIMIT_NOT_CONFIGURED(false),

        /**
         * Граф сделки предъявлен контекстом не целиком — операнды потолков
         * занижены (docs/spec/deal-context-load.json, graphComplete).
         * Вердиктом риск-политики НЕ является: реакция на него — тропа
         * ошибки сделки на ЛЮБОЙ стадии
         * (docs/processes/risk-evaluation.md §«Отказ по неполноте графа
         * реакцию не делит со схемой»).
         */
        DEAL_GRAPH_INCOMPLETE(false);

        /**
         * Отказ БЕССРОЧЕН: повторная проверка того же действия не даст
         * иного исхода без изменения самой стратегии либо внешнего
         * состояния контура. Ложь — отказ ВРЕМЕННЫЙ: исход зависит от
         * состояния, которое меняется само.
         *
         * <p>Признак живёт РЯДОМ СО ЗНАЧЕНИЕМ намеренно: карта реакции его
         * только читает, а второй носитель разошёлся бы с ним первой же
         * правкой (.claude/rules/carrier-levels.md). Новый код обязан
         * объявить признак — конструктор не даёт его умолчать.
         *
         * <p>Различение денежное: уровень сеточной детали, отвергнутый
         * ВРЕМЕННО занятым бюджетом, при прочтении «терминал» теряется
         * навсегда — бюджет освободится выходом соседнего транша, а транша,
         * который должен был войти, уже нет.
         */
        private final Boolean permanent;

        RiskCheckCode(Boolean permanent) {
            this.permanent = permanent;
        }

        /** Отказ по этому коду бессрочен. */
        public Boolean isPermanent() {
            return permanent;
        }
    }
}
