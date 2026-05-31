package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.order.AttachedAlgoOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface AttachedAlgoOrderRepository extends JpaRepository<AttachedAlgoOrderEntity, Long> {

    @Query(value = """
            select aao.*
            from attached_algo_orders aao
            where aao.order_id = :orderId
            order by aao.id desc
            """, nativeQuery = true)
    List<AttachedAlgoOrderEntity> findAllByOrderId(@Param("orderId") Long orderId);

    @Query(value = """
            select aao.*
            from attached_algo_orders aao
            join orders o on o.id = aao.order_id
            join deals d on d.id = o.deal_id
            where d.instrument_id = :instrumentId
              and aao.status in (:statuses)
            order by aao.id desc
            """, nativeQuery = true)
    List<AttachedAlgoOrderEntity> findAllByInstrumentIdAndStatuses(@Param("instrumentId") Long instrumentId,
                                                                   @Param("statuses") Set<String> statuses);
}
