package com.example.tradingcore.unit.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.tradingcore.config.RiskAppetiteProperties;
import com.example.tradingcore.domain.model.RiskAppetite;
import com.example.tradingcore.domain.service.RiskAppetiteService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.assertj.core.api.AbstractStringAssert;
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
 * <p><b>Правило приёма трёхосное:</b> заданность каждого числа, его область
 * определения и цепочка процентов {@code сделка ≤ счёт ≤ тенант}. Набор не
 * принят — звено не конструируется, и это роняет старт ядра: исход здесь
 * мерится исключением конструктора, а его сообщение обязано назвать каждое
 * непринятое число и причину.
 */
class RiskAppetiteAcceptanceTest {

    /** Имена шести чисел в порядке оси окружения. */
    private static final List<String> NUMBERS = List.of(
            "globalSimultaneousRiskPerDealPercent",
            "globalSimultaneousRiskPerAccountPercent",
            "globalSimultaneousRiskPerTenantPercent",
            "globalCumulativeRiskPerDealMultiplier",
            "globalMaxLeverage",
            "globalConsecutiveLossLimit");

    @Test
    @DisplayName("U34.1 — оси тестового окружения держателя принимаются все шесть")
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
    @DisplayName("U34.2 — оси манифеста пустые строки: старт падает, причина называет все шесть чисел")
    void emptyAxesRefuseTheStart() {
        AbstractStringAssert<?> refusal = refusal(properties("", "", "", "", "", ""));

        NUMBERS.forEach(name -> refusal.contains(name + ": is not set by the environment"));
    }

    /**
     * Форма ключей конфигурации ядра — {@code ${GLOBAL_…:}}: без переменной
     * среды значение — пустая строка, и звено читает её как «не задано».
     * Окружение здесь подменённое, без переменных процесса: иначе исход
     * зависел бы от машины прогона.
     */
    @Test
    @DisplayName("U34.3 — ключи конфигурации ядра без переменных среды: старт падает на всех шести")
    void theServiceConfigurationWithoutEnvironmentVariablesRefusesTheStart() {
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

        AbstractStringAssert<?> refusal = refusal(bound);

        NUMBERS.forEach(name -> refusal.contains(name + ": is not set by the environment"));
    }

    @Test
    @DisplayName("U34.4 — процент либо множитель неположителен: старт падает, причина называет ровно его")
    void aNonPositivePercentOrMultiplierRefusesTheStart() {
        refusal(properties("1", "10", "30", "0", "10", "3"))
                .contains("globalCumulativeRiskPerDealMultiplier=0: must be strictly positive")
                .doesNotContain("globalSimultaneousRiskPerDealPercent")
                .doesNotContain("globalMaxLeverage");

        refusal(properties("1", "10", "-30", "2", "10", "3"))
                .contains("globalSimultaneousRiskPerTenantPercent=-30: must be strictly positive")
                .doesNotContain("globalSimultaneousRiskPerAccountPercent");
    }

    @Test
    @DisplayName("U34.5 — предел плеча ниже единицы роняет старт; ровно единица — принимается")
    void aMaxLeverageBelowOneRefusesTheStart() {
        refusal(properties("1", "10", "30", "2", "0.5", "3"))
                .contains("globalMaxLeverage=0.5: must not be below one");
        assertThat(accept(properties("1", "10", "30", "2", "1", "3")).getGlobalMaxLeverage())
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("U34.6 — предел серии — только положительное целое: дробный и нулевой роняют старт")
    void theLossLimitMustBeAPositiveInteger() {
        refusal(properties("1", "10", "30", "2", "10", "2.5"))
                .contains("globalConsecutiveLossLimit=2.5: must be a positive integer");
        refusal(properties("1", "10", "30", "2", "10", "0"))
                .contains("globalConsecutiveLossLimit=0: must be a positive integer");
        assertThat(accept(properties("1", "10", "30", "2", "10", "3.0")).getGlobalConsecutiveLossLimit())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("U34.7 — значения, не читающиеся числом, роняют старт: причина называет каждое")
    void nonNumericValuesRefuseTheStart() {
        refusal(properties("1%", "10", "30", "2", "ten", "3"))
                .contains("globalSimultaneousRiskPerDealPercent=1%: is not a number")
                .contains("globalMaxLeverage=ten: is not a number")
                .doesNotContain("globalConsecutiveLossLimit");
    }

    @Test
    @DisplayName("U34.8 — цепочка сделка ≤ счёт ≤ тенант нарушена: старт падает, причина называет все три процента")
    void aBrokenPercentChainRefusesTheStart() {
        refusal(properties("1", "40", "30", "2", "10", "3"))
                .contains("globalSimultaneousRiskPerDealPercent=1")
                .contains("globalSimultaneousRiskPerAccountPercent=40")
                .contains("globalSimultaneousRiskPerTenantPercent=30")
                .contains("chain deal <= account <= tenant is broken")
                .doesNotContain("globalMaxLeverage");
    }

    @Test
    @DisplayName("U34.9 — звенья цепочки равны: граница включена")
    void equalChainLinksAreAccepted() {
        RiskAppetite accepted = accept(properties("10", "10", "10", "2", "10", "3"));

        assertThat(accepted.getGlobalSimultaneousRiskPerDealPercent()).isEqualByComparingTo("10");
        assertThat(accepted.getGlobalSimultaneousRiskPerTenantPercent()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("U34.10 — пустое среднее звено нарушения между крайними не скрывает: причин две")
    void anEmptyMiddleLinkDoesNotHideABreachBetweenTheOuterOnes() {
        refusal(properties("40", "", "30", "2", "10", "3"))
                .contains("globalSimultaneousRiskPerAccountPercent: is not set by the environment")
                .contains("chain deal <= account <= tenant is broken");
    }

    private static RiskAppetite accept(RiskAppetiteProperties properties) {
        return new RiskAppetiteService(properties).getAccepted();
    }

    /** Сообщение отказа приёма: звено не конструируется, и старт ядра падает. */
    private static AbstractStringAssert<?> refusal(RiskAppetiteProperties properties) {
        Throwable failure = catchThrowable(() -> new RiskAppetiteService(properties));

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        return assertThat(failure.getMessage())
                .startsWith("Risk appetite is not accepted, trading-core does not start");
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
