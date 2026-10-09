package com.example.tradingcore.unit.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingcore.config.RiskAppetiteProperties;
import com.example.tradingcore.domain.model.RiskAppetite;
import com.example.tradingcore.domain.service.RiskAppetiteService;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Приём чисел риск-аппетита ядром при старте (дом —
 * docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»; основание — .claude/decisions/risk-appetite-environment-config.md).
 *
 * <p><b>Правило приёма двухосное:</b> область определения каждого числа и
 * цепочка процентов {@code сделка ≤ счёт ≤ тенант}. Непринятое число
 * остаётся пустым; ядро при этом поднимается — исход здесь мерится
 * конструированием звена, которое не бросает ни на одной клетке.
 */
class RiskAppetiteAcceptanceTest {

    @Test
    @DisplayName("Оси тестового окружения держателя принимаются все шесть")
    void theHolderTestNumbersAreAllAccepted() {
        RiskAppetite accepted = accept(properties("1", "10", "30", "2", "10", "3"));

        assertThat(accepted.getGlobalSimultaneousRiskPerDealPercent()).isEqualByComparingTo("1");
        assertThat(accepted.getGlobalSimultaneousRiskPerAccountPercent()).isEqualByComparingTo("10");
        assertThat(accepted.getGlobalSimultaneousRiskPerTenantPercent()).isEqualByComparingTo("30");
        assertThat(accepted.getGlobalCumulativeRiskPerDealMultiplier()).isEqualByComparingTo("2");
        assertThat(accepted.getGlobalMaxLeverage()).isEqualByComparingTo("10");
        assertThat(accepted.getGlobalConsecutiveLossLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("Ось манифеста пустая строка: число не задано и не принято, ядро поднимается")
    void anEmptyAxisIsNotAccepted() {
        RiskAppetite accepted = accept(properties("", "", "", "", "", ""));

        assertThat(accepted).isEqualTo(RiskAppetite.builder().build());
    }

    /**
     * Форма ключей конфигурации ядра — {@code ${GLOBAL_…:}}: без переменной
     * среды значение — пустая строка, и звено читает её как «не задано».
     * Окружение здесь подменённое, без переменных процесса: иначе исход
     * зависел бы от машины прогона.
     */
    @Test
    @DisplayName("Ключи конфигурации ядра без переменных среды связываются в пустые числа")
    void theServiceConfigurationWithoutEnvironmentVariablesYieldsEmptyNumbers() {
        MockEnvironment environment = new MockEnvironment();
        try {
            new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
                    .forEach(source -> environment.getPropertySources().addLast(source));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        RiskAppetiteProperties bound = Binder.get(environment)
                .bind("risk-appetite", RiskAppetiteProperties.class)
                .orElseGet(RiskAppetiteProperties::new);

        assertThat(accept(bound)).isEqualTo(RiskAppetite.builder().build());
    }

    @Test
    @DisplayName("Процент и множитель неположительны: не принимаются, прочие числа принимаются")
    void nonPositivePercentsAndMultiplierAreNotAccepted() {
        RiskAppetite accepted = accept(properties("1", "10", "30", "0", "10", "3"));

        assertThat(accepted.getGlobalCumulativeRiskPerDealMultiplier()).isNull();
        assertThat(accepted.getGlobalSimultaneousRiskPerDealPercent()).isEqualByComparingTo("1");

        RiskAppetite negativeTenant = accept(properties("1", "10", "-30", "2", "10", "3"));

        assertThat(negativeTenant.getGlobalSimultaneousRiskPerTenantPercent()).isNull();
        assertThat(negativeTenant.getGlobalSimultaneousRiskPerAccountPercent()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Предел плеча ниже единицы не принимается; ровно единица — принимается")
    void aMaxLeverageBelowOneIsNotAccepted() {
        assertThat(accept(properties("1", "10", "30", "2", "0.5", "3")).getGlobalMaxLeverage()).isNull();
        assertThat(accept(properties("1", "10", "30", "2", "1", "3")).getGlobalMaxLeverage())
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("Предел серии — только положительное целое: дробный и нулевой не принимаются")
    void theLossLimitMustBeAPositiveInteger() {
        assertThat(accept(properties("1", "10", "30", "2", "10", "2.5")).getGlobalConsecutiveLossLimit()).isNull();
        assertThat(accept(properties("1", "10", "30", "2", "10", "0")).getGlobalConsecutiveLossLimit()).isNull();
        assertThat(accept(properties("1", "10", "30", "2", "10", "3.0")).getGlobalConsecutiveLossLimit())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Значение, не читающееся числом, не принимается и ядра не роняет")
    void aNonNumericValueIsNotAccepted() {
        RiskAppetite accepted = accept(properties("1%", "10", "30", "2", "ten", "3"));

        assertThat(accepted.getGlobalSimultaneousRiskPerDealPercent()).isNull();
        assertThat(accepted.getGlobalMaxLeverage()).isNull();
        assertThat(accepted.getGlobalConsecutiveLossLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("Цепочка сделка ≤ счёт ≤ тенант нарушена: не принимаются все три процента, прочие — да")
    void aBrokenPercentChainDropsAllThreePercents() {
        RiskAppetite accepted = accept(properties("1", "40", "30", "2", "10", "3"));

        assertThat(accepted.getGlobalSimultaneousRiskPerDealPercent()).isNull();
        assertThat(accepted.getGlobalSimultaneousRiskPerAccountPercent()).isNull();
        assertThat(accepted.getGlobalSimultaneousRiskPerTenantPercent()).isNull();
        assertThat(accepted.getGlobalCumulativeRiskPerDealMultiplier()).isEqualByComparingTo("2");
        assertThat(accepted.getGlobalMaxLeverage()).isEqualByComparingTo("10");
        assertThat(accepted.getGlobalConsecutiveLossLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("Звенья цепочки равны: граница включена")
    void equalChainLinksAreAccepted() {
        RiskAppetite accepted = accept(properties("10", "10", "10", "2", "10", "3"));

        assertThat(accepted.getGlobalSimultaneousRiskPerDealPercent()).isEqualByComparingTo("10");
        assertThat(accepted.getGlobalSimultaneousRiskPerTenantPercent()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Пустое среднее звено нарушения между крайними не скрывает")
    void anEmptyMiddleLinkDoesNotHideABreachBetweenTheOuterOnes() {
        RiskAppetite accepted = accept(properties("40", "", "30", "2", "10", "3"));

        assertThat(accepted.getGlobalSimultaneousRiskPerDealPercent()).isNull();
        assertThat(accepted.getGlobalSimultaneousRiskPerTenantPercent()).isNull();
    }

    private static RiskAppetite accept(RiskAppetiteProperties properties) {
        return new RiskAppetiteService(properties).getAccepted();
    }

    private static RiskAppetiteProperties properties(String dealPercent, String accountPercent,
                                                     String tenantPercent, String cumulativeMultiplier,
                                                     String maxLeverage, String lossLimit) {
        RiskAppetiteProperties properties = new RiskAppetiteProperties();
        properties.setGlobalSimultaneousRiskPerDealPercent(dealPercent);
        properties.setGlobalSimultaneousRiskPerAccountPercent(accountPercent);
        properties.setGlobalSimultaneousRiskPerTenantPercent(tenantPercent);
        properties.setGlobalCumulativeRiskPerDealMultiplier(cumulativeMultiplier);
        properties.setGlobalMaxLeverage(maxLeverage);
        properties.setGlobalConsecutiveLossLimit(lossLimit);
        return properties;
    }
}
