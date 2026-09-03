package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.exchange.ExchangeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRepository extends JpaRepository<ExchangeEntity, Long> {

    Optional<ExchangeEntity> findByInternalId(String internalId);

    @Query("select e.internalId from ExchangeEntity e where e.id = :id")
    Optional<String> findInternalIdById(@Param("id") Long id);

    @Query("select e.id from ExchangeEntity e where e.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);

    /** Проекция id бирж в заданном статусе — для каскадного фильтра входов (TRADE_BLOCKED). */
    @Query("select e.id from ExchangeEntity e where e.status = :status")
    List<Long> findIdsByStatus(@Param("status") String status);

    /** Проекция id бирж в любом из статусов — фильтр входов читает обе ступени лестницы. */
    @Query("select e.id from ExchangeEntity e where e.status in :statuses")
    List<Long> findIdsByStatusIn(@Param("statuses") List<String> statuses);

    /**
     * Счёт подряд идущих неполных проходов детекции: инкремент либо
     * сброс в ноль одним запросом. Читать-менять-писать здесь не годится:
     * счёт ведёт периодический проход, и потеря инкремента отодвигала бы
     * предел слепоты молча.
     */
    @Modifying
    @Query("update ExchangeEntity e set e.blindPassCount = case when :complete = true then 0 "
            + "else e.blindPassCount + 1 end where e.id = :id")
    int updateBlindPassCount(@Param("id") Long id, @Param("complete") Boolean complete);

    @Query("select e.blindPassCount from ExchangeEntity e where e.id = :id")
    Optional<Integer> findBlindPassCountById(@Param("id") Long id);

    /** Гардированный statusный переход (только из ожидаемого {@code from}); возвращает число затронутых строк. */
    @Modifying
    @Query("update ExchangeEntity e set e.status = :to where e.id = :id and e.status = :from")
    int updateStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);
}
