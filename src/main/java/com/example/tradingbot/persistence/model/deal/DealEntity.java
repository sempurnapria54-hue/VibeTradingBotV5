package com.example.tradingbot.persistence.model.deal;

import com.example.tradingbot.persistence.model.deal.algo_order.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.deal.order.OrderEntity;
import com.example.tradingbot.persistence.model.deal.position.PositionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "deals", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_internal_id",
                columnNames = {"internal_id"}
        )
})
public class DealEntity {

    /**
     * Внутренний идентификатор.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Межсервисный идентификатор.
     */
    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    /**
     * Идентификатор инструмента.
     */
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    /**
     * Текущий внутренний статус.
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Причина закрытия.
     */
    @Column(name = "close_reason")
    private String closeReason;

    /**
     * Сколько принесла сделка. Если убыток, то отрицательное число.
     */
    @Column(name = "result_profit", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal resultProfit;

    /**
     * Список ордеров.
     */
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private List<OrderEntity> orderEntities;

    /**
     * Список алго-ордеров.
     */
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private List<AlgoOrderEntity> algoOrderEntities;

    /**
     * Список позиций.
     */
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private List<PositionEntity> positionEntities;

}
