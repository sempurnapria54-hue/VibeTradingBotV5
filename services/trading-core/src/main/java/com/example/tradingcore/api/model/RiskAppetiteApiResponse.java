package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Числа риск-аппетита, ПРИНЯТЫЕ ядром при старте из конфигурации окружения
 * (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ»).
 *
 * <p><b>Пустое поле — «число не принято»</b>: в окружении его нет либо приём
 * его отверг. Читается оно как отказ того, что на числе стоит, а не как ноль
 * (docs/rules/absent-value-semantics.md).
 *
 * <p>Тенанта ответ не называет: числа одни на всех тенантов окружения.
 */
@Getter
@Setter
public class RiskAppetiteApiResponse {

    @Schema(description = "Потолок одновременного риска сделки, проценты базы риска сделки;"
            + " сомножитель глобального кумулятивного потолка. Пусто — число не принято")
    private BigDecimal globalSimultaneousRiskPerDealPercent;

    @Schema(description = "Потолок живого риска биржевого счёта, проценты живой базы счёта."
            + " Пусто — число не принято")
    private BigDecimal globalSimultaneousRiskPerAccountPercent;

    @Schema(description = "Потолок живого риска тенанта, проценты базы тенанта (сумма баз его счетов)."
            + " Пусто — число не принято")
    private BigDecimal globalSimultaneousRiskPerTenantPercent;

    @Schema(description = "Предел множителя кумулятивного потолка сделки: сделка за жизнь берёт не больше"
            + " него, помноженного на процент сделки. Пусто — число не принято")
    private BigDecimal globalCumulativeRiskPerDealMultiplier;

    @Schema(description = "Предел плеча: потолок нотинала сделки в долях базы и верхняя граница плеча пары."
            + " Пусто — число не принято")
    private BigDecimal globalMaxLeverage;

    @Schema(description = "Предел подряд идущих ценово-убыточных закрытых сделок счёта. Пусто — число не принято")
    private Integer globalConsecutiveLossLimit;
}
