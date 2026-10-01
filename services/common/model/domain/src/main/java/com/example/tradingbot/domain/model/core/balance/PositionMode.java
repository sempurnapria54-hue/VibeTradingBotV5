package com.example.tradingbot.domain.model.core.balance;

/**
 * Режим позиций счёта площадки в снимке средств — вторая посылка контура:
 * контур держит {@link #NET}, на нём стоят константа стороны позиции у
 * адаптера площадки и правило «позиция одна на пару»
 * (docs/rules/trading-constraints.md). Перевод словаря площадки — граница
 * коннектора (docs/integrations/okx/contracts/account-config.md). См.
 * docs/models/domain/core/BalanceContainer.md.
 */
public enum PositionMode {

    /** Нетто-режим: одна позиция на инструмент, сторона выражена знаком размера. Режим контура. */
    NET,

    /** Раздельные длинная и короткая позиции на одном инструменте. */
    LONG_SHORT
}
