package com.example.tradingbot.config;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Боевой профиль без чисел риск-аппетита НЕ ПОДНИМАЕТСЯ (решение
 * держателя 2026-09-01): «пустое место — отказ» перестаёт быть только
 * доковым клеймом и становится машинным.
 *
 * <p><b>Почему только в бою.</b> Незаданное число отвергает действие
 * адресно, каждое своим кодом (docs/rules/risk-policy.md §«Числа
 * назначает держатель; пустое место — отказ»), и эта развязка остаётся
 * единственной там, где числа могут законно отсутствовать. В бою же
 * пустое число означает, что торговля запущена без объявленного
 * риск-аппетита, — и узнавать об этом из реджекта первого действия
 * поздно: подъём сорван раньше, чем сделан первый шаг.
 *
 * <p>Проверка не заменяет адресных отказов и не дублирует их: у неё
 * другой момент (старт против прохода) и другой адресат (оператор против
 * разбора по данным).
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class RiskAppetiteStartupCheck {

    private final RiskAppetiteProperties properties;

    @PostConstruct
    void requireRiskAppetite() {
        List<String> missing = new ArrayList<>();
        if (isNull(properties.getGlobalSimultaneousRiskPerDealPercent())) {
            missing.add("risk-appetite.global-simultaneous-risk-per-deal-percent");
        }
        if (isNull(properties.getGlobalCatastrophicRiskPerDealMultiplier())) {
            missing.add("risk-appetite.global-catastrophic-risk-per-deal-multiplier");
        }
        if (isNull(properties.getGlobalConsecutiveLossLimit())) {
            missing.add("risk-appetite.global-consecutive-loss-limit");
        }
        if (isNotEmpty(missing)) {
            throw new IllegalStateException("Риск-аппетит не назначен, боевой профиль не поднимается — "
                    + "заполните числа держателя: " + String.join(", ", missing));
        }
    }
}
