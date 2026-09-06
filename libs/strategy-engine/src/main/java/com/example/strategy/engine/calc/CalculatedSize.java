package com.example.strategy.engine.calc;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Рассчитанный размер действия — результат {@link SizeCalculator}
 * (docs/components/models/CalculatedSize.md). Неизменяемый
 * runtime-объект прохода, не хранится.
 *
 * <p>Размер решается <b>в контрактах</b>: {@code sz} площадки для
 * бессрочных и поставочных контрактов — контракты, не валюта.
 */
@Value
@Builder
public class CalculatedSize {

    /**
     * Итоговый размер в контрактах. У reduce-only выхода это размер
     * заявки: на обоих полных исходах — экспозиция транша целиком, а не
     * округлённая доля (docs/spec/order-sizing.json, {@code exitSizeFinal}).
     */
    BigDecimal sizeContracts;

    /** Доля уменьшения позиции в долях единицы; только у reduce-only. */
    BigDecimal closeFraction;

    /** Номинал в расчётной валюте, если считался. */
    BigDecimal notionalUsdt;

    /** Режим размера. */
    SizeMode sizeMode;

    /**
     * Исход округления reduce-only выхода. Заполняется только у выхода; у
     * прочих режимов пусто — округлять долю там нечего.
     */
    ExitOutcome exitOutcome;

    /** Пояснение расчёта для логов и аудита. */
    String description;
}
