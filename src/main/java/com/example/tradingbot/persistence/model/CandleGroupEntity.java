package com.example.tradingbot.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "candle_group", uniqueConstraints = {
    @UniqueConstraint(name = "uk_candle_group_instrument_timeframe", columnNames = {"instrument_id", "timeframe"})
})
public class CandleGroupEntity extends AuditableEntity {

    public static final int TIMEFRAME_LENGTH = 16;
    public static final int STATUS_LENGTH = 32;
    public static final int ERROR_CODE_LENGTH = 32;
    public static final int ERROR_MESSAGE_LENGTH = 1024;
    public static final int LEASE_OWNER_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "instrument_id", nullable = false, updatable = false, insertable = false)
    private Long instrumentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    @Column(name = "timeframe", nullable = false, length = TIMEFRAME_LENGTH)
    private String timeframe;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    private CandleGroupStatus status;

    @Column(name = "coverage_start_ts", nullable = false)
    private Long coverageStartTs;

    @Column(name = "backfill_cursor_ts")
    private Long backfillCursorTs;

    @Column(name = "last_tail_sync_ts")
    private Long lastTailSyncTs;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @Column(name = "last_error_at")
    private OffsetDateTime lastErrorAt;

    @Column(name = "last_error_code", length = ERROR_CODE_LENGTH)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = ERROR_MESSAGE_LENGTH)
    private String lastErrorMessage;

    @Column(name = "lease_owner", length = LEASE_OWNER_LENGTH)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Long leaseUntil;
}
