package com.example.marketdata.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция заказанной идентичности вычисления индикатора
 * (таблица indicator_configs): тип, таймфрейм и канонические параметры.
 *
 * <p>Реестр и есть ответ на «что считать»: джоба обходит его строки, а не
 * настройки стратегий, которых у market-data нет
 * (docs/models/domain/other/IndicatorValue.md §«Ключевание —
 * идентичностью вычисления»).
 *
 * <p>Параметры лежат дважды и по разным поводам: {@code params_canonical}
 * — нормализованная форма, по которой идентичность сравнивается;
 * {@code params} — форма, из которой восстанавливается объект параметров
 * для калькулятора. Первое — ключ, второе — значение.
 */
@Getter
@Setter
@Entity
@Table(name = "indicator_configs")
public class IndicatorConfigEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "indicator_type", nullable = false, updatable = false)
    private String indicatorType;

    @Column(name = "timeframe", nullable = false, updatable = false)
    private String timeframe;

    @Column(name = "params_canonical", nullable = false, updatable = false)
    private String paramsCanonical;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, updatable = false)
    private String params;
}
