package com.example.tradingbot.persistence.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sync_execution_environment_report")
public class SynchronizeExecutionEnvironmentReportEntity {

    public static final int TRIGGER_LENGTH = 16;
    public static final int SEVERITY_LENGTH = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "exchange_id", nullable = false, updatable = false, insertable = false)
    private Long exchangeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private ExchangeEntity exchange;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "trigger", nullable = false, length = TRIGGER_LENGTH)
    private String trigger;

    @Column(name = "has_anomalies", nullable = false)
    private boolean hasAnomalies;

    @Column(name = "max_severity", nullable = false, length = SEVERITY_LENGTH)
    private String maxSeverity;

    @Column(name = "database_before_json", nullable = false, columnDefinition = "jsonb")
    private String databaseBeforeJson;

    @Column(name = "exchange_before_json", nullable = false, columnDefinition = "jsonb")
    private String exchangeBeforeJson;

    @Column(name = "database_after_json", columnDefinition = "jsonb")
    private String databaseAfterJson;

    @Column(name = "exchange_after_json", columnDefinition = "jsonb")
    private String exchangeAfterJson;

    @OneToMany(mappedBy = "report", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SynchronizeExecutionEnvironmentReportAnomalyEntity> anomalies = new ArrayList<>();
}
