package com.example.strategies.integration.internal.api.model;

import java.math.BigDecimal;

/**
 * Ответ ядра о числах риск-аппетита тенанта — форма ИСТОЧНИКА, как её
 * отдаёт сосед (.claude/rules/codestyle.md §«Нейминг по слоям»).
 *
 * <p>Пустое поле означает «держатель числа не назначил»: это отказ
 * создания, а не разрешение — сверять объявленное стратегией не с чем
 * (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»).
 *
 * <p>Запись, а не {@code @Value}: у значения есть читатель — разбор
 * ответа, — и форма обязана собираться без скрытых механизмов
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * @param tenantInternalId                        идентичность тенанта
 * @param globalSimultaneousRiskPerDealPercent    потолок одновременного риска на сделку
 * @param globalCatastrophicRiskPerDealMultiplier предел множителя катастрофического потолка
 */
public record RiskAppetiteCoreResponse(String tenantInternalId,
                                       BigDecimal globalSimultaneousRiskPerDealPercent,
                                       BigDecimal globalCatastrophicRiskPerDealMultiplier) {
}
