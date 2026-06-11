package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.order.AttachedAlgoOrderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachedAlgoOrderRepository extends JpaRepository<AttachedAlgoOrderEntity, Long> {

    List<AttachedAlgoOrderEntity> findByOrderId(Long orderId);
}
