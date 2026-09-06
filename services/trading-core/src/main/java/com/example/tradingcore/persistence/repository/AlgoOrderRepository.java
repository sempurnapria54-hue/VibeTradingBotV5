package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.AlgoOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы по строке отдельной условной заявки. */
public interface AlgoOrderRepository extends JpaRepository<AlgoOrderEntity, Long> {

    List<AlgoOrderEntity> findByDealId(Long dealId);

    /** Строка condition-заявки по нашему клиентскому идентификатору — тот же операнд. */
    Optional<AlgoOrderEntity> findByInternalId(String internalId);
}
