package com.example.tradingbot.persistence.model.deal.algo_order;

import com.example.tradingbot.persistence.converter.AlgoOrderConditionJsonbConverter;
import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "algo_orders", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_algo_orders_internal_id",
                columnNames = {"internal_id"}
        )
})
public class AlgoOrderEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Идентификатор сделки-владельца algo-ордера.
     */
    @Column(name = "deal_id", nullable = false, updatable = false)
    private Long dealId;

    /**
     * Межсервисный идентификатор algo-ордера (идемпотентность).
     */
    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    /**
     * Текущий внутренний статус жизненного цикла algo-ордера.
     * Пример: CREATED/PENDING/ACTIVE/CLOSED/FAILED.
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Причина закрытия algo-ордера.
     */
    @Column(name = "close_reason")
    private String closeReason;

    /**
     * Доменный тип условия (строка вместо enum).
     * Дублируется с condition.type для фильтрации/логирования.
     */
    @Column(name = "condition_type", nullable = false, updatable = false)
    private String conditionType;

    /**
     * Объём algo-ордера.
     * Для close-algo может быть null, если закрываем через closeFraction.
     */
    @Column(name = "size", precision = PRICE_PRECISION, scale = PRICE_SCALE, updatable = false)
    private BigDecimal size;

    /**
     * Сторона algo-ордера в домене (строка вместо enum): BUY/SELL.
     */
    @Column(name = "direction", updatable = false)
    private String direction;

    /**
     * Идентификатор algo-ордера на бирже (algoId).
     */
    @Column(name = "external_id")
    private String externalId;

    /**
     * Биржевой тип algo-ордера (ordType): conditional | oco | move_order_stop | trigger ...
     */
    @Column(name = "external_type")
    private String externalType;

    /**
     * Состояние algo-ордера на стороне биржи (state), например live/pause.
     */
    @Column(name = "external_status")
    private String externalStatus;

    /**
     * Сторона algo-ордера на бирже (buy/sell).
     */
    @Column(name = "external_direction")
    private String externalDirection;

    /**
     * Сторона позиции на бирже (posSide): net | long | short.
     */
    @Column(name = "external_position_side")
    private String externalPositionSide;

    /**
     * Условие algo-ордера (общий тип) в JSONB.
     * <p>
     * Внутренние поля condition не должны меняться после создания,
     * но external-поля внутри condition (например TriggerPrice.external*) могут обновляться,
     * поэтому column updatable=true.
     */
    @Convert(converter = AlgoOrderConditionJsonbConverter.class)
    @Column(name = "condition", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private Condition condition;

}
