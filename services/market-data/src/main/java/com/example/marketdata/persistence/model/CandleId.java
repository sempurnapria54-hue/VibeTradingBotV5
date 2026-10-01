package com.example.marketdata.persistence.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Составной ключ свечи: группа сбора плюс время открытия бара.
 *
 * <p>Суррогатного ключа у ряда нет намеренно: свечи лежат гипертаблицей,
 * и форму её ключа задаёт docs/rules/persistence-representation.md
 * §«Ключ гипертаблицы». Естественный ключ вдобавок и есть механизм
 * идемпотентности загрузки (docs/rules/idempotency-via-unique.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CandleId implements Serializable {

    /** ID группы свечей. */
    private Long candleGroupId;

    /** Время открытия свечи, UTC миллисекунды. */
    private Long openTimestamp;
}
