package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.order.AttachedAlgoOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachedAlgoOrderRepository extends JpaRepository<AttachedAlgoOrderEntity, Long> {

    List<AttachedAlgoOrderEntity> findByOrderId(Long orderId);

    /** Проекция ссылки на родителя: строку защиты ради одного поля не тянем. */
    @Query("select a.orderId from AttachedAlgoOrderEntity a where a.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);
}
