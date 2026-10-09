package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Числа риск-аппетита — оси окружения, какими их приносит манифест
 * (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»; оси — docs/architecture/platform.md). Принимает их
 * {@link com.example.tradingcore.domain.service.RiskAppetiteService} при
 * старте; здесь только сырая форма.
 *
 * <p><b>Поля строковые намеренно.</b> Значение, которое не читается числом,
 * — опечатка манифеста, то есть число вне области определения, и старт
 * роняет правило приёма, называя все непринятые числа разом и каждое — с
 * причиной. Числовое поле свойства уронило бы контекст на связывании раньше
 * — на первом же негодном значении и сообщением связывателя, а не правила.
 *
 * <p><b>Пустое значение</b> — ключ манифеста окружения, для которого
 * держатель чисел не назвал; приём читает его как «число не задано», и ядро
 * в таком окружении не стартует.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "risk-appetite")
public class RiskAppetiteProperties {

    /** Потолок одновременного риска сделки, проценты базы риска сделки. */
    private String globalSimultaneousRiskPerDealPercent;

    /** Потолок живого риска биржевого счёта, проценты живой базы счёта. */
    private String globalSimultaneousRiskPerAccountPercent;

    /** Потолок живого риска тенанта, проценты базы тенанта. */
    private String globalSimultaneousRiskPerTenantPercent;

    /** Предел множителя кумулятивного потолка сделки. */
    private String globalCumulativeRiskPerDealMultiplier;

    /** Предел плеча: брутто-плечо сделки к базе и верхняя граница плеча пары. */
    private String globalMaxLeverage;

    /** Сколько подряд ценово-убыточных закрытых сделок останавливают торговлю счёта. */
    private String globalConsecutiveLossLimit;
}
