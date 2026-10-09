package com.example.strategies.domain.model;

import java.math.BigDecimal;

/**
 * Числа риск-аппетита, принятые ядром, — операнды трёх из пяти неравенств
 * создания (docs/rules/strategy-validation.md §«Исключения: неравенства,
 * проверяемые на создании»): первого, третьего и пятого.
 *
 * <p><b>Владелец определений их не хранит и конфигурации окружения не
 * читает.</b> Числа — оси окружения, и принимает их одно ядро при старте
 * (docs/rules/risk-policy.md, правило о числах риск-аппетита;
 * .claude/decisions/risk-appetite-environment-config.md); сюда они
 * приезжают синхронным чтением по строке docs/architecture/contracts.md
 * §«Синхронные вызовы» и живут ровно столько, сколько идёт проверка.
 * Проекции не заводится: устаревшая копия потолка ошибалась бы <b>в
 * разрешающую</b> сторону. Имя значения историческое — числа одинаковы
 * для всех тенантов окружения.
 *
 * <p><b>Запись, а не {@code @Value}:</b> значение собирается из ответа
 * соседа, и форма обязана собираться без скрытых механизмов
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * <p>Пустое поле означает «ядро числа не приняло», и это отказ, а не
 * разрешение: сверять объявленное стратегией не с чем.
 *
 * @param globalSimultaneousRiskPerDealPercent  потолок одновременного риска на сделку, % базы (неравенство 1)
 * @param globalCumulativeRiskPerDealMultiplier предел множителя кумулятивного потолка (неравенство 3)
 * @param globalMaxLeverage                     предел плеча — потолок нотинала сделки в долях базы (неравенство 5)
 */
public record TenantRiskAppetite(BigDecimal globalSimultaneousRiskPerDealPercent,
                                 BigDecimal globalCumulativeRiskPerDealMultiplier,
                                 BigDecimal globalMaxLeverage) {
}
