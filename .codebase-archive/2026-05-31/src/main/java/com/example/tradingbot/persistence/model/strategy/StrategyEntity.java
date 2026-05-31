package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "strategies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_strategies_internal_id", columnNames = {"internal_id"})
})
public class StrategyEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "version", nullable = false, updatable = false)
    private Integer version;

    @Column(name = "status", nullable = false)
    private String status;

    @OrderBy("marketPhaseType ASC, id ASC")
    @OneToMany(mappedBy = "strategy", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StrategyDetailsEntity> detailEntities;
}
