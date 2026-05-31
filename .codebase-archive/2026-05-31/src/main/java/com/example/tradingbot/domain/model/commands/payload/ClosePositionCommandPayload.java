package com.example.tradingbot.domain.model.commands.payload;

import com.example.tradingbot.domain.model.commands.ServiceCommandPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosePositionCommandPayload implements ServiceCommandPayload {

    /**
     * Локальный id позиции. Может быть null, если закрывается активная позиция из DealContext.
     */
    private Long positionId;

    /**
     * Доля закрытия позиции в процентах. Null означает полный выход.
     */
    private BigDecimal closeFractionPercents;
}
