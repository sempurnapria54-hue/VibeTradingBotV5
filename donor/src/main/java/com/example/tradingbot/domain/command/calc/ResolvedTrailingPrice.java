package com.example.tradingbot.domain.command.calc;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Резолв цен трейлинг-стопа (раздел CalculatedPrice, без него смысла не
 * имеет): цена активации + callback (ratio/percent или spread). При
 * отсутствии порога активации {@code activationPrice = null} (на биржу не
 * отправляется). См. docs/components/models/CalculatedPrice.md,
 * docs/components/PriceCalculator.md (§Trailing).
 */
@Value
@Builder
public class ResolvedTrailingPrice {

    /** Цена активации трейлинга; null — активен сразу (не отправляется). */
    BigDecimal activationPrice;

    /** Callback в процентах (биржевой callback ratio). */
    BigDecimal callbackRatio;

    /** Callback абсолютным spread'ом; null для процентного режима. */
    BigDecimal callbackSpread;
}
