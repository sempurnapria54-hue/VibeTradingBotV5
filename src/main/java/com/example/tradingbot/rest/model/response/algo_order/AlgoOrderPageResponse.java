package com.example.tradingbot.rest.model.response.algo_order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlgoOrderPageResponse {

    public Page<AlgoOrder> algoOrders;
}
