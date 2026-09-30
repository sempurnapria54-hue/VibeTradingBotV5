package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.AlgoOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по строке отдельной условной заявки. */
public interface AlgoOrderRepository extends JpaRepository<AlgoOrderEntity, Long> {

    /**
     * Отдельные условные заявки сделки в объявленном порядке: от последней
     * сработавшей на площадке к первой, несработавшие и сработавшие без
     * наблюдённого момента — после них, от поздней заведённой к ранней.
     *
     * <p>Порядок делает чтение воспроизводимым и причины выхода транша не
     * несёт: последнюю сработавшую защиту выбирает сама модель по фактам
     * защит (docs/lifecycles/DealTranche.md §«Инициатор выхода транша
     * читается по его фактам»). Пустой момент уходит в конец явно: у
     * убывающей сортировки Postgres ставит пустоту первой.
     */
    @Query("""
            select a from AlgoOrderEntity a
            where a.dealId = :dealId
            order by a.externalTriggerTime desc nulls last, a.id desc
            """)
    List<AlgoOrderEntity> findByDealId(@Param("dealId") Long dealId);

    /** Строка condition-заявки по нашему клиентскому идентификатору — тот же операнд. */
    Optional<AlgoOrderEntity> findByInternalId(String internalId);
}
