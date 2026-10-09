package com.example.tradingcore.domain.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingcore.config.RiskAppetiteProperties;
import com.example.tradingcore.domain.model.RiskAppetite;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Принимает числа риск-аппетита из конфигурации окружения — один раз, при
 * старте — и отдаёт принятый набор всем читателям ядра
 * (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»; основание формы — .claude/decisions/risk-appetite-environment-config.md).
 *
 * <p><b>Принимающее звено одно.</b> Преконтроль, счётчик серии убытков,
 * назначение плеча пары и чтение владельца определений берут числа отсюда,
 * а не из конфигурации: второе звено завело бы вторую копию правила приёма
 * и могло бы принять набор, который это отвергло.
 *
 * <p><b>Правило приёма:</b>
 * <ul>
 *   <li>область определения — проценты и множитель строго положительны,
 *       предел плеча не меньше единицы, предел серии — положительное целое;
 *       значение, не читающееся числом, — тоже вне области. Число вне области
 *       не принимается и остаётся пустым;</li>
 *   <li>цепочка процентов {@code сделка ≤ счёт ≤ тенант}: нарушенная не
 *       принимает все три процента — какой из них ошибочен, из отношения не
 *       выводится.</li>
 * </ul>
 *
 * <p><b>Непринятый набор ядро не роняет.</b> Ядро стартует, причина уходит в
 * лог уровнем ERROR, а набор риска получает адресный отказ тем же кодом, что
 * и при пустом числе: падение оставило бы живые сделки без сопровождения
 * из-за опечатки в манифесте. Пустое число — штатное состояние окружения,
 * для которого держатель чисел не назвал, и в лог оно идёт предупреждением.
 */
@Slf4j
@Service
public class RiskAppetiteService {

    /** Принятый набор; пустое поле — число не принято. */
    @Getter
    private final RiskAppetite accepted;

    public RiskAppetiteService(RiskAppetiteProperties properties) {
        this.accepted = accept(properties);
    }

    private static RiskAppetite accept(RiskAppetiteProperties properties) {
        RiskAppetite candidate = RiskAppetite.builder()
                .globalSimultaneousRiskPerDealPercent(positive("globalSimultaneousRiskPerDealPercent",
                        properties.getGlobalSimultaneousRiskPerDealPercent()))
                .globalSimultaneousRiskPerAccountPercent(positive("globalSimultaneousRiskPerAccountPercent",
                        properties.getGlobalSimultaneousRiskPerAccountPercent()))
                .globalSimultaneousRiskPerTenantPercent(positive("globalSimultaneousRiskPerTenantPercent",
                        properties.getGlobalSimultaneousRiskPerTenantPercent()))
                .globalCumulativeRiskPerDealMultiplier(positive("globalCumulativeRiskPerDealMultiplier",
                        properties.getGlobalCumulativeRiskPerDealMultiplier()))
                .globalMaxLeverage(atLeastOne("globalMaxLeverage", properties.getGlobalMaxLeverage()))
                .globalConsecutiveLossLimit(positiveInteger("globalConsecutiveLossLimit",
                        properties.getGlobalConsecutiveLossLimit()))
                .build();
        if (isTrue(candidate.percentChainBroken())) {
            log.error("Risk appetite percents are not accepted: chain deal <= account <= tenant is broken"
                            + " (deal={}, account={}, tenant={}); all three percents stay empty",
                    candidate.getGlobalSimultaneousRiskPerDealPercent(),
                    candidate.getGlobalSimultaneousRiskPerAccountPercent(),
                    candidate.getGlobalSimultaneousRiskPerTenantPercent());
            candidate = candidate.withoutPercents();
        }
        warnEmpty(properties);
        return candidate;
    }

    /** Процент либо множитель: строго положителен. */
    private static BigDecimal positive(String name, String raw) {
        BigDecimal value = parse(name, raw);
        if (isNull(value) || value.signum() > 0) {
            return value;
        }
        log.error("Risk appetite number {}={} is not accepted: it must be strictly positive", name, raw);
        return null;
    }

    /** Предел плеча: не меньше единицы — плечо ниже единицы не плечо, а запрет торговли. */
    private static BigDecimal atLeastOne(String name, String raw) {
        BigDecimal value = parse(name, raw);
        if (isNull(value) || value.compareTo(BigDecimal.ONE) >= 0) {
            return value;
        }
        log.error("Risk appetite number {}={} is not accepted: it must not be below one", name, raw);
        return null;
    }

    /** Предел серии: положительное целое — счётчик сделок дробным не бывает. */
    private static Integer positiveInteger(String name, String raw) {
        BigDecimal value = parse(name, raw);
        if (isNull(value)) {
            return null;
        }
        Integer limit = exactInteger(value);
        if (nonNull(limit) && limit > 0) {
            return limit;
        }
        log.error("Risk appetite number {}={} is not accepted: it must be a positive integer", name, raw);
        return null;
    }

    /** Целое значение числа; пусто — у числа есть дробная часть либо оно вне диапазона. */
    private static Integer exactInteger(BigDecimal value) {
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * Сырое значение оси в число; пусто — ось не задана. Значение, не
     * читающееся числом, не принимается: это опечатка манифеста, а не повод
     * не поднять ядро.
     */
    private static BigDecimal parse(String name, String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.error("Risk appetite number {}={} is not accepted: it is not a number", name, raw);
            return null;
        }
    }

    /** Пустые оси — штатное состояние окружения без чисел держателя, но видимое в логе. */
    private static void warnEmpty(RiskAppetiteProperties properties) {
        List<String> empty = new ArrayList<>();
        addIfBlank(empty, "globalSimultaneousRiskPerDealPercent", properties.getGlobalSimultaneousRiskPerDealPercent());
        addIfBlank(empty, "globalSimultaneousRiskPerAccountPercent",
                properties.getGlobalSimultaneousRiskPerAccountPercent());
        addIfBlank(empty, "globalSimultaneousRiskPerTenantPercent",
                properties.getGlobalSimultaneousRiskPerTenantPercent());
        addIfBlank(empty, "globalCumulativeRiskPerDealMultiplier",
                properties.getGlobalCumulativeRiskPerDealMultiplier());
        addIfBlank(empty, "globalMaxLeverage", properties.getGlobalMaxLeverage());
        addIfBlank(empty, "globalConsecutiveLossLimit", properties.getGlobalConsecutiveLossLimit());
        if (isEmpty(empty)) {
            return;
        }
        log.warn("Risk appetite numbers are not set by the environment: {}; risk-creating acts are refused", empty);
    }

    private static void addIfBlank(List<String> empty, String name, String raw) {
        if (isBlank(raw)) {
            empty.add(name);
        }
    }
}
