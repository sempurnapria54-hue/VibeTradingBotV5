package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка транша сделки (таблица deal_tranches) — единицы принятия и
 * сопровождения риска.
 *
 * <p>Ноги транша живут своими таблицами по {@code deal_tranche_id};
 * слагаемые экспозиции — колонки, сама экспозиция производна и не
 * хранится (docs/models/domain/aggregate/DealTranche.md).
 *
 * <p><b>Переоткрытие идёт ТЕМ ЖЕ траншем</b>, а не новой строкой, поэтому
 * номер эпизода — колонка: без него строки прошлого эпизода неотличимы от
 * строк текущего.
 */
@Getter
@Setter
@Entity
@Table(name = "deal_tranches")
public class DealTrancheEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    /** Объявление, по которому транш материализован; пусто у восстановленного. */
    @Column(name = "strategy_tranche_id")
    private Long strategyTrancheId;

    /** Уровень экземпляра в сетке объявления; пусто у нешаблонного. */
    @Column(name = "level")
    private Integer level;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "episode_seq", nullable = false)
    private Integer episodeSeq;

    @Column(name = "entry_step_type")
    private String entryStepType;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "entry_filled", precision = 36, scale = 18)
    private BigDecimal entryFilled;

    @Column(name = "reduce_only_filled", precision = 36, scale = 18)
    private BigDecimal reduceOnlyFilled;

    @Column(name = "protection_closed", precision = 36, scale = 18)
    private BigDecimal protectionClosed;
}
