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
 *   <li>заданность — у каждого из шести чисел значение есть;</li>
 *   <li>область определения — проценты и множитель строго положительны,
 *       предел плеча не меньше единицы, предел серии — положительное целое;
 *       значение, не читающееся числом, — тоже вне области;</li>
 *   <li>цепочка процентов {@code сделка ≤ счёт ≤ тенант}: какой из них
 *       ошибочен, из отношения не выводится, и причина называет все три.</li>
 * </ul>
 *
 * <p><b>Набор не принят — ядро не стартует.</b> Исключение из конструктора
 * роняет создание бина, а с ним подъём контекста; причина называет каждое
 * непринятое число и что с ним не так, и та же причина уходит в лог уровнем
 * ERROR. Отсюда инвариант: у работающего ядра набор принят целиком, и пустого
 * поля в {@link RiskAppetite} не бывает.
 */
@Slf4j
@Service
public class RiskAppetiteService {

    /** Принятый набор: все шесть чисел есть. */
    @Getter
    private final RiskAppetite accepted;

    public RiskAppetiteService(RiskAppetiteProperties properties) {
        this.accepted = accept(properties);
    }

    private static RiskAppetite accept(RiskAppetiteProperties properties) {
        List<String> refusals = new ArrayList<>();
        RiskAppetite candidate = RiskAppetite.builder()
                .globalSimultaneousRiskPerDealPercent(positive("globalSimultaneousRiskPerDealPercent",
                        properties.getGlobalSimultaneousRiskPerDealPercent(), refusals))
                .globalSimultaneousRiskPerAccountPercent(positive("globalSimultaneousRiskPerAccountPercent",
                        properties.getGlobalSimultaneousRiskPerAccountPercent(), refusals))
                .globalSimultaneousRiskPerTenantPercent(positive("globalSimultaneousRiskPerTenantPercent",
                        properties.getGlobalSimultaneousRiskPerTenantPercent(), refusals))
                .globalCumulativeRiskPerDealMultiplier(positive("globalCumulativeRiskPerDealMultiplier",
                        properties.getGlobalCumulativeRiskPerDealMultiplier(), refusals))
                .globalMaxLeverage(atLeastOne("globalMaxLeverage", properties.getGlobalMaxLeverage(), refusals))
                .globalConsecutiveLossLimit(positiveInteger("globalConsecutiveLossLimit",
                        properties.getGlobalConsecutiveLossLimit(), refusals))
                .build();
        if (isTrue(candidate.percentChainBroken())) {
            refusals.add("globalSimultaneousRiskPerDealPercent=" + candidate.getGlobalSimultaneousRiskPerDealPercent()
                    + ", globalSimultaneousRiskPerAccountPercent="
                    + candidate.getGlobalSimultaneousRiskPerAccountPercent()
                    + ", globalSimultaneousRiskPerTenantPercent="
                    + candidate.getGlobalSimultaneousRiskPerTenantPercent()
                    + ": chain deal <= account <= tenant is broken");
        }
        if (isEmpty(refusals)) {
            return candidate;
        }
        String reason = "Risk appetite is not accepted, trading-core does not start: " + String.join("; ", refusals);
        log.error(reason);
        throw new IllegalStateException(reason);
    }

    /** Процент либо множитель: строго положителен. */
    private static BigDecimal positive(String name, String raw, List<String> refusals) {
        BigDecimal value = parse(name, raw, refusals);
        if (isNull(value) || value.signum() > 0) {
            return value;
        }
        refusals.add(name + "=" + raw + ": must be strictly positive");
        return null;
    }

    /** Предел плеча: не меньше единицы — плечо ниже единицы не плечо, а запрет торговли. */
    private static BigDecimal atLeastOne(String name, String raw, List<String> refusals) {
        BigDecimal value = parse(name, raw, refusals);
        if (isNull(value) || value.compareTo(BigDecimal.ONE) >= 0) {
            return value;
        }
        refusals.add(name + "=" + raw + ": must not be below one");
        return null;
    }

    /** Предел серии: положительное целое — счётчик сделок дробным не бывает. */
    private static Integer positiveInteger(String name, String raw, List<String> refusals) {
        BigDecimal value = parse(name, raw, refusals);
        if (isNull(value)) {
            return null;
        }
        Integer limit = exactInteger(value);
        if (nonNull(limit) && limit > 0) {
            return limit;
        }
        refusals.add(name + "=" + raw + ": must be a positive integer");
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
     * Сырое значение оси в число; пусто — ось не задана либо не читается
     * числом, и причина уже записана в отказы приёма.
     */
    private static BigDecimal parse(String name, String raw, List<String> refusals) {
        if (isBlank(raw)) {
            refusals.add(name + ": is not set by the environment");
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            refusals.add(name + "=" + raw + ": is not a number");
            return null;
        }
    }
}
