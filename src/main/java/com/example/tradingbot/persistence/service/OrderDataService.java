package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.model.search_params.OrderSearchParams;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.repository.OrderRepository;
import com.example.tradingbot.persistence.specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.Constant.ErrorCode.ORDER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderDataService {

    private final OrderRepository orderRepository;
    private final OrderMapper mapper;

    @Transactional
    public Order save(Order order) {
        OrderEntity data = mapper.domainToData(order);
        OrderEntity saved = orderRepository.save(data);
        return mapper.dataToDomain(saved);
    }

    public Order findRequiredByInternalId(String internalOrderId) {
        OrderEntity data = orderRepository.findByInternalId(internalOrderId)
                                          .orElseThrow(() -> new RuntimeException(ORDER_NOT_FOUND));
        return mapper.dataToDomain(data);
    }

    public Page<Order> search(OrderSearchParams params, Pageable pageable) {
        Page<OrderEntity> data = orderRepository.findAll(
                OrderSpecification.bySearchParams(params),
                pageable
        );
        return mapper.dataToDomain(data);
    }
}
