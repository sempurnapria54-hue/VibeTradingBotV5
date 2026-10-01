package com.example.tradingbot.domain.model.core.balance;

/**
 * Режим счёта площадки в снимке средств — посылка контура «только свои
 * средства»: контур держит {@link #FUTURES}, режим, в котором площадка
 * займа не даёт. Иной режим преконтроль отвергает на всяком проверяемом
 * действии (docs/rules/trading-constraints.md). Перевод словаря площадки в
 * этот перечень — граница коннектора
 * (docs/integrations/okx/contracts/account-config.md,
 * docs/models/mapping/Balance.md). См.
 * docs/models/domain/core/BalanceContainer.md.
 */
public enum AccountMode {

    /** Спотовый режим: деривативы на счёте не торгуются. */
    SPOT,

    /** Фьючерсный режим: маржа каждой позиции — свои средства, займа площадка не даёт. Режим контура. */
    FUTURES,

    /** Мультивалютная маржа: площадка допускает заём под обеспечение других валют. */
    MULTI_CURRENCY_MARGIN,

    /** Портфельная маржа: маржа считается по портфелю, заём допускается. */
    PORTFOLIO_MARGIN
}
