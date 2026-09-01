package com.example.tradingbot.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Три числа риск-аппетита системы (секция {@code risk-appetite}). Все три
 * назначает держатель при запуске; дом смысла, единицы и роли каждого —
 * docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ» (третье — docs/rules/loss-streak-halt.md). Значения живут только
 * в конфиге профиля, доки их не фиксируют.
 *
 * <p><b>Умолчаний нет ни у одного, и это не упущение.</b> Незаданное
 * число означает ОТКАЗ, а не проход: провизорное число выглядит как
 * контроль, но им не является, и его наличие мешает заметить, что
 * контроля нет. Что именно отвергается пустым числом — таблица дома;
 * пустота приезжает сюда как {@code null}.
 *
 * <p>Прочие числа конфигурации — сроки, лимиты запросов, допуски сверки —
 * к риск-аппетиту не относятся и живут в своих секциях.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "risk-appetite")
public class RiskAppetiteProperties {

    /**
     * Максимальный риск на одну сделку, проценты базы риска. Потолок
     * одновременного риска сделки и первый сомножитель катастрофического
     * потолка (docs/spec/risk-limits.json, величины
     * {@code withinGlobalSimultaneous} и {@code catastrophicLossCeiling}).
     * Пусто — risk-creating действие отвергается.
     */
    private BigDecimal globalSimultaneousRiskPerDealPercent;

    /**
     * Максимальный множитель, которым стратегия вправе растянуть
     * катастрофический потолок сделки: объявленный деталью множитель
     * сверяется с этим числом на создании стратегии
     * (docs/spec/strategy-reference.json, величина
     * {@code catastrophicMultiplierWithinGlobal}). Пусто — создание
     * стратегии отвергается: объявленный множитель не с чем сверить.
     */
    private BigDecimal globalCatastrophicRiskPerDealMultiplier;

    /**
     * Сколько подряд ценово-убыточных закрытых сделок останавливают
     * торговлю (docs/rules/loss-streak-halt.md). Пусто — risk-creating
     * действие отвергается кодом {@code LOSS_LIMIT_NOT_CONFIGURED}.
     */
    private Integer globalConsecutiveLossLimit;
}
