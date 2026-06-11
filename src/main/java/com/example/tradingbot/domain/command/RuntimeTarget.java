package com.example.tradingbot.domain.command;

import lombok.Value;

/**
 * Куда нацелено действие сделки — какую runtime-сущность оно
 * породило/затрагивает. Вложенный value-объект DealActionState (без
 * своего состояния смысла не имеет); персистится jsonb на строке
 * DealActionState. Заполняется executor'ами CREATE_* / REFRESH_POSITION.
 * См. docs/models/domain/other/DealActionState.md.
 */
@Value
public class RuntimeTarget {

    /** Тип runtime-сущности, на которую нацелено действие. */
    TargetEntityType entityType;

    /** Локальный id этой сущности; null для NONE. */
    Long entityId;
}
