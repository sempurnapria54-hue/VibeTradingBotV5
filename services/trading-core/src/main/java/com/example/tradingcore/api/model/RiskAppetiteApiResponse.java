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
 * <p><b>Все шесть полей заданы всегда:</b> непринятый набор роняет старт
 * ядра, и отвечать с пустым числом некому.
 *
 * <p>Тенанта ответ не называет: числа одни на всех тенантов окружения.
 */
@Getter
@Setter
public class RiskAppetiteApiResponse {

    @Schema(description = "Потолок одновременного риска сделки, проценты базы риска сделки;"
            + " сомножитель глобального кумулятивного потолка")
    private BigDecimal globalSimultaneousRiskPerDealPercent;

    @Schema(description = "Потолок живого риска биржевого счёта, проценты живой базы счёта")
    private BigDecimal globalSimultaneousRiskPerAccountPercent;

    @Schema(description = "Потолок живого риска тенанта, проценты базы тенанта (сумма баз его счетов)")
    private BigDecimal globalSimultaneousRiskPerTenantPercent;

    @Schema(description = "Предел множителя кумулятивного потолка сделки: сделка за жизнь берёт не больше"
            + " него, помноженного на процент сделки")
    private BigDecimal globalCumulativeRiskPerDealMultiplier;

    @Schema(description = "Предел плеча: потолок нотинала сделки в долях базы и верхняя граница плеча пары")
    private BigDecimal globalMaxLeverage;

    @Schema(description = "Предел подряд идущих ценово-убыточных закрытых сделок счёта")
    private Integer globalConsecutiveLossLimit;
}
