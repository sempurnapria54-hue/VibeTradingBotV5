package com.example.tradingbot.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sync_execution_environment_report_anomaly")
public class SynchronizeExecutionEnvironmentReportAnomalyEntity {

    public static final int INST_ID_LENGTH = 64;
    public static final int TYPE_LENGTH = 64;
    public static final int SEVERITY_LENGTH = 16;
    public static final int SUMMARY_LENGTH = 512;

    /** Внутренний идентификатор аномалии. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Идентификатор отчёта, к которому относится аномалия. */
    @Column(name = "report_id", nullable = false, updatable = false, insertable = false)
    private Long reportId;

    /** Ссылка на родительский отчёт синхронизации. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private SynchronizeExecutionEnvironmentReportEntity report;

    /** Идентификатор инструмента (instId), если аномалия относится к инструменту. */
    @Column(name = "inst_id", length = INST_ID_LENGTH)
    private String instId;

    /** Тип аномалии. */
    @Column(name = "type", nullable = false, length = TYPE_LENGTH)
    private String type;

    /** Уровень серьёзности аномалии. */
    @Column(name = "severity", nullable = false, length = SEVERITY_LENGTH)
    private String severity;

    /** Краткое текстовое описание аномалии. */
    @Column(name = "summary", nullable = false, length = SUMMARY_LENGTH)
    private String summary;

    /** Детали аномалии в формате JSON. */
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb")
    private String detailsJson;

    /** Время создания записи аномалии. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
