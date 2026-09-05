package com.example.marketdata.persistence.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Составной ключ невосполнимого среза: инструмент плюс метка времени
 * ПЛОЩАДКИ. Наша метка приёма ключом не является — она измеряет задержку,
 * а не идентифицирует момент рынка.
 *
 * <p>Суррогата нет по тому же доводу, что у свечи: срезы лежат
 * гипертаблицей, и уникальный индекс обязан нести колонку
 * партиционирования.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MarketSnapshotId implements Serializable {

    /** Инструмент, чей срез снят. */
    private Long instrumentId;

    /** Метка времени площадки, UTC миллисекунды. */
    private Long externalTimestamp;
}
