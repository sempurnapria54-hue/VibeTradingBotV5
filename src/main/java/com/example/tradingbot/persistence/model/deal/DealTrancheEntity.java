package com.example.tradingbot.persistence.model.deal;

import com.example.tradingbot.persistence.model.AuditableEntity;
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
 * Persistence-проекция {@link com.example.tradingbot.domain.model.aggregate.deal.DealTranche}
 * (таблица deal_tranches). deal_id — скалярная колонка с FK на deals;
 * статус хранится строкой (значение = name() доменного enum). Слагаемые
 * экспозиции — колонки: экспозиция производна и сама не хранится.
 * См. docs/models/domain/aggregate/DealTranche.md.
 */
@Getter
@Setter
@Entity
@Table(name = "deal_tranches")
public class DealTrancheEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, unique = true)
    private String internalId;

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "episode_seq", nullable = false)
    private Integer episodeSeq;

    @Column(name = "entry_filled", precision = 36, scale = 18)
    private BigDecimal entryFilled;

    @Column(name = "reduce_only_filled", precision = 36, scale = 18)
    private BigDecimal reduceOnlyFilled;

    @Column(name = "protection_closed", precision = 36, scale = 18)
    private BigDecimal protectionClosed;
}
