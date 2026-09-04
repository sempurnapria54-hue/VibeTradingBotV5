package com.example.tradingbot.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Связывает три числа риск-аппетита с их конфигурационным носителем
 * (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»).
 *
 * <p>Проверяется КОНТРАКТ, а не значения: ключи секции действительно
 * биндятся в поля (опечатка в ключе связала бы пустоту молча), и оба
 * профиля несут все три числа — пустое число в боевом профиле роняет
 * подъём приложения. Сами числа тест не повторяет — второй носитель
 * значения разошёлся бы с конфигом первой же правкой, а читатель не знал
 * бы, которое из двух действует.
 */
class RiskAppetitePropertiesBindingTest {

    private static final String TEST_PROFILE = "application-test.yaml";
    private static final String PROD_PROFILE = "application-prod.yaml";
    private static final String REFERENCE_STRATEGY = "strategy-examples/trend-following-ema.json";

    @Test
    @DisplayName("Профиль test несёт все три числа, и каждое биндится в своё поле")
    void testProfileCarriesAllThree() throws IOException {
        RiskAppetiteProperties properties = bind(TEST_PROFILE);

        assertNotNull(properties.getGlobalSimultaneousRiskPerDealPercent(), "риск на сделку");
        assertNotNull(properties.getGlobalCatastrophicRiskPerDealMultiplier(), "множитель потолка");
        assertNotNull(properties.getGlobalConsecutiveLossLimit(), "предел серии убытков");

        assertTrue(properties.getGlobalSimultaneousRiskPerDealPercent().signum() > 0);
        assertTrue(properties.getGlobalCatastrophicRiskPerDealMultiplier().signum() > 0);
        assertTrue(properties.getGlobalConsecutiveLossLimit() > 0);
    }

    @Test
    @DisplayName("Профиль prod объявляет все три ключа и несёт значение у каждого")
    void prodProfileCarriesAllThree() throws IOException {
        // Пустое число в боевом профиле роняет подъём приложения
        // (RiskAppetiteStartupCheck), поэтому проверяется ровно это: ключ
        // объявлен И значение биндится. КАКОЕ это значение, тест не знает и
        // знать не должен — до конца фазы 1 там временная копия профиля test
        // (решение держателя 2026-09-03), после — назначенное держателем
        // боевое число; носитель этого различия — не тест, а встречный якорь
        // .claude/work/backlog.md §«Боевые числа риск-аппетита — назначение
        // держателем перед первым запуском prod».
        ConfigurationPropertySource source = source(PROD_PROFILE);
        for (String key : List.of("global-simultaneous-risk-per-deal-percent",
                "global-catastrophic-risk-per-deal-multiplier",
                "global-consecutive-loss-limit")) {
            assertNotNull(source.getConfigurationProperty(
                            ConfigurationPropertyName.of("risk-appetite." + key)),
                    "ключ risk-appetite." + key + " в prod-профиле не объявлен");
        }

        RiskAppetiteProperties properties = bind(PROD_PROFILE);
        assertNotNull(properties.getGlobalSimultaneousRiskPerDealPercent(), "риск на сделку");
        assertNotNull(properties.getGlobalCatastrophicRiskPerDealMultiplier(), "множитель потолка");
        assertNotNull(properties.getGlobalConsecutiveLossLimit(), "предел серии убытков");

        assertTrue(properties.getGlobalSimultaneousRiskPerDealPercent().signum() > 0);
        assertTrue(properties.getGlobalCatastrophicRiskPerDealMultiplier().signum() > 0);
        assertTrue(properties.getGlobalConsecutiveLossLimit() > 0);
    }

    @Test
    @DisplayName("Числа профиля test допускают эталонную стратегию репозитория")
    void testProfileAdmitsReferenceStrategy() throws IOException {
        // Иначе контур test не смог бы создать стратегию, под которой он и
        // работает: оба неравенства создания стоя́т на этих же двух числах
        // (docs/spec/strategy-reference.json, riskChainHolds).
        RiskAppetiteProperties properties = bind(TEST_PROFILE);
        JsonNode detail = new ObjectMapper()
                .readTree(new ClassPathResource(REFERENCE_STRATEGY).getInputStream())
                .get("details").get(0);

        BigDecimal strategySimultaneous = detail.get("strategySimultaneousRiskPerDealPercent").decimalValue();
        BigDecimal strategyCatastrophic = detail.get("strategyCatastrophicRiskPerDealMultiplier").decimalValue();

        assertTrue(strategySimultaneous.compareTo(properties.getGlobalSimultaneousRiskPerDealPercent()) <= 0,
                "риск эталона выше конфигурационного максимума — стратегия не создастся");
        assertTrue(strategyCatastrophic.compareTo(properties.getGlobalCatastrophicRiskPerDealMultiplier()) <= 0,
                "множитель эталона выше конфигурационного предела — стратегия не создастся");
    }

    private RiskAppetiteProperties bind(String resource) throws IOException {
        return new Binder(source(resource))
                .bind("risk-appetite", RiskAppetiteProperties.class)
                .orElseGet(RiskAppetiteProperties::new);
    }

    private ConfigurationPropertySource source(String resource) throws IOException {
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load(resource, new ClassPathResource(resource));
        return ConfigurationPropertySources.from(loaded.getFirst()).iterator().next();
    }
}
