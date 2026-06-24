package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.DealEntity;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
