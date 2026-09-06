package com.example.tradingcore.domain.command.payload;

import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры добычи снимка средств: расчётная валюта, за которой идёт
 * чтение.
 *
 * <p>Счёт и инструмент приезжают контекстом прохода; валюта названа
 * параметром, потому что читается она у площадки по валюте, а не по
 * инструменту (docs/components/RefreshBalanceExecutor.md).
 */
@Value
public class RefreshBalanceCommandPayload implements ServiceCommandPayload {

    /** Расчётная валюта, за остатком которой идёт чтение. */
    String settleCurrency;
}
