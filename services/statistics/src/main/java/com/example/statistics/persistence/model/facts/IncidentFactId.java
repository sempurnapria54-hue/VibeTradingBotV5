package com.example.statistics.persistence.model.facts;

import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Составной ключ факта происшествия: идентичность события плюс ось времени
 * своего зерна.
 *
 * <p>Форма и довод те же, что у соседа по зерну ({@link DealFactId}): ряд
 * лежит гипертаблицей, и колонка разбиения обязана входить в первичный ключ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IncidentFactId implements Serializable {

    /** Идентичность принятого события; она же отметка обработанного. */
    private String eventId;

    /** Момент происшествия из конверта — ось времени таблицы. */
    private OffsetDateTime occurredAt;
}
