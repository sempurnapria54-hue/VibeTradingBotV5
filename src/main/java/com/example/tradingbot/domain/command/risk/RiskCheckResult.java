package com.example.tradingbot.domain.command.risk;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Результат одной конкретной risk-проверки внутри RiskValidationResult.
 * RVO, не persisted. {@code limitValue} отдельным полем не вводится — при
 * необходимости лимит кладётся в {@code details}. См.
 * docs/components/models/RiskCheckResult.md.
 */
@Value
@Builder
public class RiskCheckResult {

    /** Машинный код проверки. */
    RiskCheckCode code;

    /** Результат конкретной проверки. */
    RiskCheckStatus status;

    /** Фактическое значение, если проверка числовая. */
    BigDecimal actualValue;

    /** Короткое пояснение. */
    String comment;

    /** Дополнительные детали для диагностики. */
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

    /** Результат конкретной risk-проверки. */
    public enum RiskCheckStatus {

        /** Проверка пройдена. */
        PASSED,

        /** Предупреждение — действие не блокируется. */
        WARNING,

        /** Действие заблокировано этой проверкой. */
        BLOCKED
    }

    /**
     * Машинный код risk-проверки. Часть кодов (PARTIAL_EXIT_*,
     * DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN) — safety/invariant violation
     * exit-flow (reduce-only), не risk-policy проверки RiskValidator.
     */
    public enum RiskCheckCode {

        /** Убыток на стопе как % от свободного депозита превышает лимит сделки. */
        RISK_PER_TRADE_EXCEEDED(true),

        /** Плечо превышает биржевой максимум инструмента. */
        EXCHANGE_MAX_LEVERAGE_EXCEEDED(true),

        /** Режим маржи не isolated. */
        MARGIN_MODE_NOT_ISOLATED(true),

        /** Обнаружен borrow/debt (торгуем только своими средствами). */
        BORROW_OR_DEBT_DETECTED(true),

        /** Недостаточно средств для действия. */
        BALANCE_NOT_ENOUGH(false),

        /** Баланс не свежий. */
        BALANCE_NOT_FRESH(false),

        /** Баланс отсутствует/невалиден. */
        BALANCE_INVALID(false),

        /**
         * Размер поднят до минимального лота инструмента и на нём вышел за
         * поактный потолок риска. НЕ расхождение расчёта: ветвь подъёма до
         * минимума потолком не ограничена вовсе и отвергается своим
         * неравенством (docs/spec/order-sizing.json). Дом различения —
         * docs/components/models/RiskCheckResult.md.
         */
        SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET(true),

        /** Размер ниже минимального размера инструмента. */
        SIZE_BELOW_MIN(true),

        /** Размер не кратен шагу лота. */
        SIZE_LOT_STEP_INVALID(true),

        /** Размер выше per-order лимита инструмента. */
        SIZE_ABOVE_LIMIT(true),

        /** Стоп-лосс на неверной стороне относительно входа. */
        STOP_LOSS_INVALID_SIDE(true),

        /** Тейк-профит на неверной стороне относительно входа. */
        TAKE_PROFIT_INVALID_SIDE(true),

        /** Стоп-лосс слишком близко к цене ликвидации. */
        STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION(true),

        /** Risk-creating вход (открытие/наращивание) без резолвимого стопа (docs/rules/risk-creating-entry-protection.md). */
        RISK_CREATING_ENTRY_WITHOUT_STOP(true),

        /** Частичный выход не reduce-only (safety/invariant). */
        PARTIAL_EXIT_NOT_REDUCE_ONLY(true),

        /** Частичный выход увеличивает позицию (safety/invariant). */
        PARTIAL_EXIT_INCREASES_POSITION(true),

        /** Прямое частичное закрытие позиции запрещено (safety/invariant). */
        DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN(true),

        /** Обнаружено более одной позиции по инструменту. */
        MULTIPLE_POSITIONS_DETECTED(true),

        /** Состояние позиции неизвестно. */
        POSITION_STATE_UNKNOWN(false),

        /** Инструмент не торгуется (status != LIVE). */
        INSTRUMENT_NOT_LIVE(true),

        /** Внешние правила инструмента не материализованы. */
        INSTRUMENT_RULES_MISSING(true),

        /** Рассчитанное действие невалидно (нет размера/цены). */
        CALCULATED_ACTION_INVALID(true);

        /**
         * Отказ БЕССРОЧЕН: повторная проверка того же действия не даст иного
         * исхода без изменения самой стратегии либо внешнего состояния
         * контура. Ложь — отказ ВРЕМЕННЫЙ: исход зависит от состояния, которое
         * меняется само (баланс пополнится, свежесть вернётся, наблюдение
         * придёт).
         *
         * <p>Признак живёт РЯДОМ СО ЗНАЧЕНИЕМ намеренно: карта реакции его
         * только читает, а второй носитель разошёлся бы с ним первой же
         * правкой. Новый код обязан объявить признак — конструктор не даёт
         * его умолчать. Дом определения —
         * docs/components/models/RiskCheckResult.md.
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
