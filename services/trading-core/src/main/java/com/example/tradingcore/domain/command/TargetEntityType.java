package com.example.tradingcore.domain.command;

/**
 * Тип runtime-сущности, на которую нацелено исполнение действия.
 *
 * <p>Хранится КОЛОНКОЙ, а не в навесе: тип цели — операнд ключа
 * уникальности (docs/models/domain/other/DealActionState.md §Енумы).
 */
public enum TargetEntityType {

    /** Обычная заявка. */
    ORDER,

    /** Отдельная условная заявка. */
    ALGO_ORDER,

    /** Эпизод позиции. */
    POSITION,

    /** Сама сделка — цель системных действий. */
    DEAL,

    /** Снимок средств счёта. */
    BALANCE,

    /** Исполнение без runtime-цели: идентификатор цели пуст. */
    NONE
}
