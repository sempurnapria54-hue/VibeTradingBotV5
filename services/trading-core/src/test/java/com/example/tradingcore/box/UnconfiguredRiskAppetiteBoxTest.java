package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingcore.TradingCoreApplication;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p><b>Та же причина уходит в журнал уровнем ERROR, и это мерится
 * журналом, а не исключением.</b> Приёмник журнала ({@link AppLog})
 * привязывается ИНИЦИАЛИЗАТОРОМ контекста: подъём Boot переинициализирует
 * логирование и снимает чужие приёмники раньше, чем создаются бины, а
 * инициализатор исполняется после переинициализации и до создания
 * принимающего звена — отметка, снятая им, и есть начало журнала этого
 * подъёма.
 */
class UnconfiguredRiskAppetiteBoxTest {

    /** Начало причины непринятого набора — общее у исключения и строки журнала. */
    private static final String NOT_ACCEPTED = "Risk appetite is not accepted, trading-core does not start";

    /** Причина пустого числа: окружение его не задало. */
    private static final String NOT_SET = ": is not set by the environment";

    /** Причина нарушенной цепочки процентов. */
    private static final String CHAIN_BROKEN = "chain deal <= account <= tenant is broken";

    /** Имена всех шести чисел риск-аппетита в причине отказа — в порядке оси окружения. */
    private static final List<String> SIX_NUMBERS = List.of(
            "globalSimultaneousRiskPerDealPercent",
            "globalSimultaneousRiskPerAccountPercent",
            "globalSimultaneousRiskPerTenantPercent",
            "globalCumulativeRiskPerDealMultiplier",
            "globalMaxLeverage",
            "globalConsecutiveLossLimit");

    @Test
    @DisplayName("B4.9 (пустые оси) — ядро не поднимается, причина называет все шесть чисел")
    void b4_9_emptyRiskAppetiteAxesDoNotRaiseTheCore() {
        List<Integer> marks = new ArrayList<>();

        assertThatThrownBy(() -> raise(TradingCoreSubstrate.emptyRiskAppetite(), marks))
                .as("пустое число риск-аппетита — отказ подъёма, а не рантайм-отказ на действии")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NOT_ACCEPTED)
                .hasMessageContainingAll(SIX_NUMBERS.stream()
                        .map(name -> name + NOT_SET)
                        .toArray(String[]::new));

        assertThat(marks).as("инициализатор контекста исполнился ровно раз").hasSize(1);
        assertThat(AppLog.errorsSince(marks.getFirst()))
                .as("B4.9 (пустые оси): причина непринятого набора в журнале уровнем ERROR")
                .contains(NOT_ACCEPTED)
                .contains(SIX_NUMBERS.stream().map(name -> name + NOT_SET).toList());
    }

    @Test
    @DisplayName("B4.9 (непринятые оси) — цепочка процентов нарушена: ядро не поднимается, причина называет её")
    void b4_9_aBrokenPercentChainDoesNotRaiseTheCore() {
        Map<String, String> axes = new LinkedHashMap<>(TradingCoreSubstrate.riskAppetite());
        axes.put(TradingCoreSubstrate.RISK_APPETITE_KEYS.get(0), "40");
        List<Integer> marks = new ArrayList<>();

        assertThatThrownBy(() -> raise(axes, marks))
                .as("непринятое число — тот же отказ подъёма, что и пустое")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NOT_ACCEPTED)
                .hasMessageContaining(CHAIN_BROKEN);

        assertThat(marks).as("инициализатор контекста исполнился ровно раз").hasSize(1);
        assertThat(AppLog.errorsSince(marks.getFirst()))
                .as("B4.9 (непринятые оси): причина непринятого набора в журнале уровнем ERROR")
                .contains(NOT_ACCEPTED)
                .contains(CHAIN_BROKEN);
    }

    /**
     * Подъём ядра на осях субстрата с заменёнными осями риск-аппетита;
     * отметку журнала снимает инициализатор контекста (довод — шапка класса).
     *
     * @param riskAppetiteAxes оси риск-аппетита, заменяющие оси субстрата
     * @param marks            куда инициализатор кладёт отметку журнала
     */
    private void raise(Map<String, String> riskAppetiteAxes, List<Integer> marks) {
        new SpringApplicationBuilder(TradingCoreApplication.class)
                .initializers(context -> marks.add(AppLog.mark()))
                .run(arguments(riskAppetiteAxes))
                .close();
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
