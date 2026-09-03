package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.DealEntity;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealRepository extends JpaRepository<DealEntity, Long> {

    Optional<DealEntity> findByInternalId(String internalId);

    List<DealEntity> findByStatusNotInOrderByIdAsc(Collection<String> statuses, Pageable pageable);

    boolean existsByInstrumentIdAndStatusNotIn(Long instrumentId, Collection<String> statuses);

    /**
     * Активные (не-terminal) сделки всех инструментов биржи — для каскадного
     * exchange-scoped kill-switch (L4). Join по {@code instrumentId} →
     * {@link InstrumentEntity} (FK-связи на entity нет — связь по id).
     */
    @Query("""
            select d from DealEntity d, InstrumentEntity i
            where d.instrumentId = i.id and i.exchangeId = :exchangeId and d.status not in :statuses
            order by d.id asc""")
    List<DealEntity> findActiveByExchangeId(@Param("exchangeId") Long exchangeId,
                                            @Param("statuses") Collection<String> statuses);

    /**
     * Монотонное вперёд движение порога доказанного покрытия. Охрана
     * стои́т в самом запросе, а не в вызывающем коде: число наблюдений
     * равно числу закрывшихся эпизодов, и порог обязан накрывать
     * движения всех — откат назад стёр бы покрытие раннего эпизода
     * (docs/models/domain/aggregate/Deal.md §Персистентность).
     */
    @Modifying
    @Query("""
            update DealEntity d set d.coverageProvenThrough = :observedAt
            where d.id = :dealId
              and (d.coverageProvenThrough is null or d.coverageProvenThrough < :observedAt)""")
    int advanceCoverageProvenThrough(@Param("dealId") Long dealId,
                                     @Param("observedAt") OffsetDateTime observedAt);

    /**
     * Write-once нижней границы окна линковки движений: охрана write-once
     * стои́т в самом запросе — заполненная граница повторной записью не
     * перетирается (docs/models/domain/aggregate/Deal.md).
     */
    @Modifying
    @Query("""
            update DealEntity d set d.billsWindowBegin = :observedAt
            where d.id = :dealId and d.billsWindowBegin is null""")
    int applyBillsWindowBegin(@Param("dealId") Long dealId,
                              @Param("observedAt") OffsetDateTime observedAt);

    /**
     * Монотонное вперёд движение метки «движения добыты по …»: охрана в
     * самом запросе — откат назад стёр бы факт более глубокой добычи
     * (docs/models/domain/aggregate/Deal.md, billsFetchedThrough).
     */
    @Modifying
    @Query("""
            update DealEntity d set d.billsFetchedThrough = :fetchedThrough
            where d.id = :dealId
              and (d.billsFetchedThrough is null or d.billsFetchedThrough < :fetchedThrough)""")
    int advanceBillsFetchedThrough(@Param("dealId") Long dealId,
                                   @Param("fetchedThrough") OffsetDateTime fetchedThrough);
}
