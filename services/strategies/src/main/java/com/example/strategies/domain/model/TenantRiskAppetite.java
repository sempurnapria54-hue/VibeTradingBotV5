package com.example.strategies.domain.model;

import java.math.BigDecimal;

/**
 * Числа риск-аппетита тенанта — операнд трёх из пяти неравенств создания
 * (docs/rules/strategy-validation.md §«Исключения: неравенства,
 * проверяемые на создании»).
 *
 * <p><b>Владелец определений их не хранит.</b> Они принадлежат тенанту и
 * живут на его строке в базе ядра и только там
 * (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»); сюда они приезжают синхронным чтением по строке
 * docs/architecture/contracts.md §«Синхронные вызовы» и живут ровно
 * столько, сколько идёт проверка. Проекции не заводится: устаревшая
 * копия потолка ошибалась бы <b>в разрешающую</b> сторону.
 *
 * <p><b>Запись, а не {@code @Value}:</b> у значения есть читатель —
 * разбор ответа соседа, — и форма обязана собираться без скрытых
 * механизмов (.claude/rules/codestyle.md §«Неизменяемое значение,
 * пересекающее сериализацию»).
 *
 * <p>Пустое поле означает «держатель числа не назначил», и это отказ, а
 * не разрешение: сверять объявленное стратегией не с чем.
 *
 * @param globalSimultaneousRiskPerDealPercent    потолок одновременного риска на сделку, % базы
 * @param globalCatastrophicRiskPerDealMultiplier предел множителя катастрофического потолка
 */
public record TenantRiskAppetite(BigDecimal globalSimultaneousRiskPerDealPercent,
                                 BigDecimal globalCatastrophicRiskPerDealMultiplier) {
}
