package com.example.tradingcore.domain.command.risk;

import java.math.BigDecimal;
import lombok.Value;

/**
 * Четвёрка чисел риска сделки как ОДНО значение: писатель кладёт её
 * целиком либо не кладёт вовсе — частичная запись оставляла бы соседние
 * числа посчитанными по прежнему графу
 * (docs/models/domain/aggregate/Deal.md §«Писатели четвёрки и их
 * триггеры»).
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией у неё нет,
 * поэтому неизменяемая форма здесь дефолт
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * <p>Формулы — docs/spec/deal-risk-numbers.json.
 */
@Value
public class DealRiskNumbers {

    /** Риск, принятый сделкой на входах, — знаменатель R и операнд кумулятивного потолка. */
    BigDecimal plannedRiskAmount;

    /** Взятый на входе риск — налитая доля заявленного. */
    BigDecimal incurredRiskAmount;

    /** Неотработанная доля взятого риска. */
    BigDecimal currentRiskAmount;

    /** Риск, снятый защитой; знак не клэмпится. */
    BigDecimal protectionRelievedRiskAmount;
}
