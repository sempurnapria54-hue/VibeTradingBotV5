package com.example.tradingbot.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "algo_order", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_algo_order_exchange_instr_client_algo_order",
        columnNames = {"exchange_id", "instrument_id", "client_algo_order_id"}
    )
})
public class AlgoOrderEntity extends AuditableEntity {

    public static final int CLIENT_ALGO_ORDER_ID_LENGTH = 128;
    public static final int EXCHANGE_ALGO_ORDER_ID_LENGTH = 128;
    public static final int STATUS_LENGTH = 50;
    public static final int ALGO_TYPE_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "exchange_id", nullable = false, updatable = false, insertable = false)
    private Long exchangeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private ExchangeEntity exchange;

    @Column(name = "instrument_id", nullable = false, updatable = false, insertable = false)
    private Long instrumentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    @Column(name = "client_algo_order_id", nullable = false, length = CLIENT_ALGO_ORDER_ID_LENGTH)
    private String clientAlgoOrderId;

    @Column(name = "exchange_algo_order_id", length = EXCHANGE_ALGO_ORDER_ID_LENGTH)
    private String exchangeAlgoOrderId;

    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    private String status;

    @Column(name = "algo_type", length = ALGO_TYPE_LENGTH)
    private String algoType;
}
