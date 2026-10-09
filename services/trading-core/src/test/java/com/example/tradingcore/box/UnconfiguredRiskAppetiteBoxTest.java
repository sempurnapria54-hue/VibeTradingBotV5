package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingcore.TradingCoreApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Окружение, чей набор риск-аппетита ядро не принимает, — клетка {@code B4.9}
 * двумя половинами, пустой и непринятой: ядро НЕ ПОДНИМАЕТСЯ (docs/rules/risk-policy.md §«Числа
 * назначает держатель; пустое место — отказ»; основание —
 * .claude/decisions/risk-appetite-environment-config.md).
 *
 * <p><b>Без {@code @SpringBootTest}, и это следствие предмета:</b> ожидание
 * клетки — что контекст НЕ поднимается, а поднятый контекст есть
 * предусловие всякого кейса ящика. Подъём здесь — вход, и производит его
 * сам кейс; форма та же, что у {@link UnconfiguredAccessContourTest}.
 *
 * <p><b>Оси подаются АРГУМЕНТАМИ и ЗАМЕНЯЮТ оси субстрата</b> — довод у
 * шапки {@link UnconfiguredAccessContourTest}: умолчания ниже
 * {@code application.yaml}, а повторённый ключ командной строки склеивается.
 *
 * <p><b>Причина отказа пинится.</b> Засчитанный любой отказ позеленил бы
 * клетку и на недоступной базе; здесь корневая причина — исключение
 * принимающего звена, и его сообщение называет каждое непринятое число.
 */
class UnconfiguredRiskAppetiteBoxTest {

    @Test
    @DisplayName("B4.9 (пустые оси) — ядро не поднимается, причина называет все шесть чисел")
    void b4_9_emptyRiskAppetiteAxesDoNotRaiseTheCore() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(TradingCoreApplication.class)
                .run(arguments(TradingCoreSubstrate.emptyRiskAppetite()))
                .close())
                .as("пустое число риск-аппетита — отказ подъёма, а не рантайм-отказ на действии")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Risk appetite is not accepted, trading-core does not start")
                .hasMessageContaining("globalConsecutiveLossLimit: is not set by the environment")
                .hasMessageContaining("globalSimultaneousRiskPerDealPercent: is not set by the environment")
                .hasMessageContaining("globalMaxLeverage: is not set by the environment");
    }

    @Test
    @DisplayName("B4.9 (непринятые оси) — цепочка процентов нарушена: ядро не поднимается, причина называет её")
    void b4_9_aBrokenPercentChainDoesNotRaiseTheCore() {
        Map<String, String> axes = new LinkedHashMap<>(TradingCoreSubstrate.riskAppetite());
        axes.put(TradingCoreSubstrate.RISK_APPETITE_KEYS.get(0), "40");

        assertThatThrownBy(() -> new SpringApplicationBuilder(TradingCoreApplication.class)
                .run(arguments(axes))
                .close())
                .as("непринятое число — тот же отказ подъёма, что и пустое")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Risk appetite is not accepted, trading-core does not start")
                .hasMessageContaining("chain deal <= account <= tenant is broken");
    }

    /** Оси субстрата с заменёнными осями риск-аппетита — аргументами командной строки. */
    private String[] arguments(Map<String, String> riskAppetiteAxes) {
        Map<String, String> axes = new LinkedHashMap<>(TradingCoreSubstrate.defaults());
        axes.putAll(riskAppetiteAxes);
        axes.put("server.port", "0");
        return axes.entrySet().stream()
                .map(axis -> "--" + axis.getKey() + "=" + axis.getValue())
                .toArray(String[]::new);
    }
}
