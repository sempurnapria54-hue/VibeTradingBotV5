package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.order.OrderEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByInternalId(String internalId);
}
