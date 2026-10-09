package com.example.strategies.integration.internal.api.model;

import java.math.BigDecimal;

/**
 * Ответ ядра о принятых числах риск-аппетита — форма ИСТОЧНИКА, как её
 * отдаёт сосед (.claude/rules/codestyle.md §«Нейминг по слоям»).
 *
 * <p><b>Компонентов три из шести.</b> Ядро отдаёт все шесть принятых чисел
 * (docs/architecture/contracts.md §«Синхронные вызовы»), а владельцу
 * определений нужны операнды трёх неравенств создания; потолки счёта и
 * тенанта и предел серии — операнды преконтроля ядра, и заводить их здесь
 * значило бы держать компоненты без читателя. Лишние ключи ответа разбор не
 * роняют: терпимость к неизвестному свойству — умолчание конвертера Boot.
 *
 * <p>Пустого поля работающее ядро не отдаёт: непринятый набор роняет его
 * старт (docs/rules/risk-policy.md, правило о числах риск-аппетита). Пустое
 * поле здесь — нарушение контракта соседа, и разбирает его чтец ответа.
 *
 * <p>Запись, а не {@code @Value}: у значения есть читатель — разбор
 * ответа, — и форма обязана собираться без скрытых механизмов
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * @param globalSimultaneousRiskPerDealPercent  потолок одновременного риска на сделку, % базы
 * @param globalCumulativeRiskPerDealMultiplier предел множителя кумулятивного потолка
 * @param globalMaxLeverage                     предел плеча
 */
public record RiskAppetiteCoreResponse(BigDecimal globalSimultaneousRiskPerDealPercent,
                                       BigDecimal globalCumulativeRiskPerDealMultiplier,
                                       BigDecimal globalMaxLeverage) {
}
