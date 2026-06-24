package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByInternalId(String internalId);

    List<InstrumentEntity> findByStatus(String status);

    List<InstrumentEntity> findByStatusIn(Collection<String> statuses);

    /** Гардированный statusный переход (только из ожидаемого {@code from}); возвращает число затронутых строк. */
    @Modifying
    @Query("update InstrumentEntity i set i.status = :to where i.id = :id and i.status = :from")
    int updateStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);

    /** Проекция: JSONB-навес внешних правил по id — без вытягивания всей сущности. */
    @Query("select i.externalRules from InstrumentEntity i where i.id = :id")
    Optional<String> findExternalRulesById(@Param("id") Long id);

    @Query("select distinct i from InstrumentEntity i left join fetch i.candleGroups where i.id = :id")
    Optional<InstrumentEntity> findByIdWithCandleGroups(@Param("id") Long id);

    @Query("select i.internalId from InstrumentEntity i where i.id = :id")
    Optional<String> findInternalIdById(@Param("id") Long id);

    @Query("select i.id from InstrumentEntity i where i.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);
}
