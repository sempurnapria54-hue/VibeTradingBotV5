package com.example.statistics.persistence.model.facts;

import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Составной ключ сделочного факта: идентичность события плюс ось времени
 * своего зерна.
 *
 * <p>Суррогатного ключа у ряда нет намеренно. Факты лежат гипертаблицей, а
 * всякий уникальный индекс гипертаблицы — включая первичный ключ — обязан
 * нести колонку разбиения: суррогат в одиночку ключом быть не может, а
 * вместе с осью времени он не нужен. Естественный ключ вдобавок и есть
 * отметка обработанного
 * (docs/models/domain/other/StatisticsFact.md §«Отметка обработанного»,
 * docs/rules/idempotency-via-unique.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DealFactId implements Serializable {

    /** Идентичность принятого события; она же отметка обработанного. */
    private String eventId;

    /** Момент терминала сделки — ось времени таблицы и колонка разбиения. */
    private OffsetDateTime closedAt;
}
