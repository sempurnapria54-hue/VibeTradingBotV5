package com.example.tradingcore.domain.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Числа риск-аппетита, ПРИНЯТЫЕ ядром при старте из конфигурации окружения
 * (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»; основание формы — .claude/decisions/risk-appetite-environment-config.md).
 *
 * <p><b>Пустое поле — «число не принято»</b>: в окружении его нет либо приём
 * его отверг (область определения, цепочка процентов). Читается оно как
 * ОТКАЗ действия, на котором стоит, а не как ноль и не как умолчание.
 *
 * <p><b>Значение одно на всех тенантов окружения</b> и живёт в памяти
 * процесса: носителя в базе у него нет, а наружу его переносит api-модель
 * ответа (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * <p>Живёт в ядре, а не в общей библиотеке: принимает числа одно ядро, а
 * владелец определений читает уже принятые своим синхронным чтением и
 * своей формой ответа.
 */
@Value
@Builder(toBuilder = true)
public class RiskAppetite {

    /**
     * Потолок одновременного риска сделки, проценты базы риска сделки;
     * сомножитель глобального кумулятивного потолка.
     */
    BigDecimal globalSimultaneousRiskPerDealPercent;

    /** Потолок живого риска биржевого счёта, проценты живой базы счёта. */
    BigDecimal globalSimultaneousRiskPerAccountPercent;

    /** Потолок живого риска тенанта, проценты базы тенанта. */
    BigDecimal globalSimultaneousRiskPerTenantPercent;

    /**
     * Предел множителя кумулятивного потолка: сделка за жизнь берёт не
     * больше него, помноженного на процент сделки.
     */
    BigDecimal globalCumulativeRiskPerDealMultiplier;

    /**
     * Предел плеча: правая часть потолка нотинала сделки в долях базы и
     * верхняя граница плеча пары «счёт, инструмент».
     */
    BigDecimal globalMaxLeverage;

    /**
     * Сколько подряд ценово-убыточных закрытых сделок останавливают
     * торговлю счёта (docs/rules/loss-streak-halt.md).
     */
    Integer globalConsecutiveLossLimit;

    /**
     * Цепочка процентов {@code сделка ≤ счёт ≤ тенант} нарушена — вложенность
     * потолков (docs/rules/risk-policy.md §«Четыре потолка на разные
     * вопросы»). Сверяется каждая пара, у которой оба числа есть: пустое
     * звено цепочку не рвёт, но и не скрывает нарушения между соседями
     * через себя.
     */
    public Boolean percentChainBroken() {
        return exceeds(globalSimultaneousRiskPerDealPercent, globalSimultaneousRiskPerAccountPercent)
                || exceeds(globalSimultaneousRiskPerAccountPercent, globalSimultaneousRiskPerTenantPercent)
                || exceeds(globalSimultaneousRiskPerDealPercent, globalSimultaneousRiskPerTenantPercent);
    }

    /**
     * Тот же набор без трёх процентов — исход приёма при нарушенной цепочке:
     * какой из них ошибочен, из отношения не выводится, и не принимается ни
     * один.
     */
    public RiskAppetite withoutPercents() {
        return toBuilder()
                .globalSimultaneousRiskPerDealPercent(null)
                .globalSimultaneousRiskPerAccountPercent(null)
                .globalSimultaneousRiskPerTenantPercent(null)
                .build();
    }

    /**
     * Плечо выше принятого предела. Пустой предел и пустое плечо ответа
     * «выше» не дают: их отсутствие отвергается своими кодами
     * (docs/rules/trading-constraints.md).
     */
    public Boolean leverageAboveLimit(Integer leverage) {
        return nonNull(globalMaxLeverage) && nonNull(leverage)
                && new BigDecimal(leverage).compareTo(globalMaxLeverage) > 0;
    }

    /**
     * Назначение плеча пары допустимо: снять плечо можно всегда, а
     * назначить — только при принятом пределе и не выше него. Пустой предел
     * отвергает назначение — сверять не с чем.
     */
    public Boolean leverageAssignable(Integer leverage) {
        if (isNull(leverage)) {
            return true;
        }
        return nonNull(globalMaxLeverage) && isFalse(leverageAboveLimit(leverage));
    }

    private static boolean exceeds(BigDecimal lower, BigDecimal upper) {
        return nonNull(lower) && nonNull(upper) && lower.compareTo(upper) > 0;
    }
}
