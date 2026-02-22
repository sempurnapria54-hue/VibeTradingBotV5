package com.example.tradingbot.persistence.model;

import com.example.tradingbot.rest.model.request.CreateCandleGroupRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

import static com.example.tradingbot.util.Constant.Status.CandleGroup.CANDLE_GROUP_STATUS_CREATED;

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

    /** Внутренний идентификатор группы свечей. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Идентификатор инструмента-владельца группы свечей. */
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    /** Таймфрейм группы (например 1m/5m/1H). */
    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    /** Текущий статус жизненного цикла загрузки свечей. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private String status;

    /** Начальная граница исторического покрытия в UTC миллисекундах. */
    @Column(name = "coverage_start_ts", nullable = false)
    private Long coverageStartTs;

    /** Курсор backfill-синхронизации в UTC миллисекундах. */
    @Column(name = "backfill_cursor_ts")
    private Long backfillCursorTs;

    /** Время последней tail-синхронизации в UTC миллисекундах. */
    @Column(name = "last_tail_sync_ts")
    private Long lastTailSyncTs;

    /** Количество попыток синхронизации подряд. */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    /** Время последней успешной синхронизации. */
    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    /** Время последней ошибки синхронизации. */
    @Column(name = "last_error_at")
    private OffsetDateTime lastErrorAt;

    /** Код последней ошибки синхронизации. */
    @Column(name = "last_error_code")
    private String lastErrorCode;

    /** Текст последней ошибки синхронизации. */
    @Column(name = "last_error_message")
    private String lastErrorMessage;

    /** Владелец lease для распределённой синхронизации. */
    @Column(name = "lease_owner")
    private String leaseOwner;

    /** Время окончания lease в UTC миллисекундах. */
    @Column(name = "lease_until")
    private Long leaseUntil;

    public void initOnCreate(InstrumentEntity instrument, CreateCandleGroupRequest request) {
        setTimeframe(request.getTimeframe());
        setCoverageStartTs(request.getCoverageStartTs());
        setStatus(CANDLE_GROUP_STATUS_CREATED);
        setInstrumentId(instrument.getId());
    }
}
