package com.example.tradingbot.domain.command.calc;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Рассчитанный размер действия (контракты). Минимальный контракт входа
 * ServiceCommandFactory; полный CalculatedSize (с разложением sizing) —
 * у калькулятора шага 5. См. docs/components/models/CalculatedSize.md.
 */
@Value
@Builder
public class CalculatedSize {

    /** Размер в контрактах (для SWAP/FUTURES). */
    BigDecimal sizeContracts;
}
