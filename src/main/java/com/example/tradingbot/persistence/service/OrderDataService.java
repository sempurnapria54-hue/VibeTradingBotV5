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

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    public Optional<Order> findByExternalId(String externalId) {
        return orderRepository.findByExternalId(externalId)
                              .map(mapper::dataToDomain);
    }

    public List<Order> findByInstrumentId(Long instrumentId) {
        return orderRepository.findAllByInstrumentId(instrumentId)
                              .stream()
                              .map(mapper::dataToDomain)
                              .toList();
    }

    public List<Order> findAllByInstrumentIdAndStatuses(Long instrumentId, Set<String> statuses) {
        return orderRepository.findAllByInstrumentIdAndStatuses(instrumentId, statuses)
                              .stream()
                              .map(mapper::dataToDomain)
                              .toList();
    }

    public Page<Order> search(OrderSearchParams params, Pageable pageable) {
        Page<OrderEntity> data = orderRepository.findAll(
                OrderSpecification.bySearchParams(params),
                pageable
        );
        return mapper.dataToDomain(data);
    }
}
