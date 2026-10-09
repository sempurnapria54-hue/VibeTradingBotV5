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
 * <p><b>Пустых полей у принятого набора не бывает:</b> число, которого в
 * окружении нет либо которое приём отверг (область определения, цепочка
 * процентов), роняет старт ядра, а не доезжает до читателей пустым. Пусто
 * поле бывает только у кандидата внутри приёма — до его вердикта.
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
     * вопросы»). Спрашивает его приём, у которого кандидат может нести
     * пустое звено — непринятое по области; поэтому сверяется каждая пара, у
     * которой оба числа есть: пустое звено цепочку не рвёт, но и не скрывает
     * нарушения между соседями через себя.
     */
    public Boolean percentChainBroken() {
        return exceeds(globalSimultaneousRiskPerDealPercent, globalSimultaneousRiskPerAccountPercent)
                || exceeds(globalSimultaneousRiskPerAccountPercent, globalSimultaneousRiskPerTenantPercent)
                || exceeds(globalSimultaneousRiskPerDealPercent, globalSimultaneousRiskPerTenantPercent);
    }

    /**
     * Плечо выше принятого предела. Пустое плечо ответа «выше» не даёт: его
     * отсутствие отвергается своим кодом (docs/rules/trading-constraints.md).
     */
    public Boolean leverageAboveLimit(Integer leverage) {
        return nonNull(leverage) && new BigDecimal(leverage).compareTo(globalMaxLeverage) > 0;
    }

    /**
     * Назначение плеча пары допустимо: снять плечо можно всегда, а
     * назначить — не выше принятого предела.
     */
    public Boolean leverageAssignable(Integer leverage) {
        if (isNull(leverage)) {
            return true;
        }
        return isFalse(leverageAboveLimit(leverage));
    }

    private static boolean exceeds(BigDecimal lower, BigDecimal upper) {
        return nonNull(lower) && nonNull(upper) && lower.compareTo(upper) > 0;
    }
}
