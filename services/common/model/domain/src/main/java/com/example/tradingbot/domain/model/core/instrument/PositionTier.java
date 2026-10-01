package com.example.tradingbot.domain.model.core.instrument;

import static java.util.Objects.nonNull;

import java.math.BigDecimal;

/**
 * Позиционный тир площадки для изолированной маржи — строка справочных
 * правил инструмента: в каких границах размера позиции действует какая
 * ставка поддерживающей маржи. Операнд оценки цены ликвидации позиции
 * после акта, создающего риск (docs/rules/risk-policy.md; форма —
 * docs/spec/risk-limits.json, величина postActMaintenanceMarginRate).
 * Источник — площадка (docs/integrations/okx/contracts/position-tiers.md).
 *
 * <p><b>Запись, а не бин:</b> значение пересекает сериализацию — провод
 * коннектора, навес правил у каталога и проекция у ядра, — а аксессоры
 * зовёт только новый код (.claude/rules/codestyle.md §«Неизменяемое
 * значение, пересекающее сериализацию»). См.
 * docs/models/domain/other/InstrumentExternalRules.md.
 *
 * @param minSize               нижняя граница размера позиции тира, в контрактах
 * @param maxSize               верхняя граница размера позиции тира, в контрактах
 * @param maintenanceMarginRate ставка поддерживающей маржи тира — доля нотинала позиции
 */
public record PositionTier(BigDecimal minSize, BigDecimal maxSize, BigDecimal maintenanceMarginRate) {

    /**
     * Позиция названного размера лежит в границах тира — условие отбора
     * тиров величины {@code postActMaintenanceMarginRate}
     * (docs/spec/risk-limits.json): обе границы включены, поэтому на стыке
     * тиров размер покрывают оба, и выбор между ними делает вызывающий.
     *
     * <p><b>Пустая граница либо пустой размер — не покрывает:</b> границы,
     * которой площадка не назвала, неограниченной не читается
     * (docs/rules/absent-value-semantics.md). Предикат с параметром
     * свойством записи для сериализатора не становится.
     */
    public Boolean covers(BigDecimal contracts) {
        return nonNull(minSize) && nonNull(maxSize) && nonNull(contracts)
                && minSize.compareTo(contracts) <= 0 && contracts.compareTo(maxSize) <= 0;
    }
}
