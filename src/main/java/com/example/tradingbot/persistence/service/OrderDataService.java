package com.example.tradingbot.persistence.service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.model.order.AttachedAlgoOrderEntity;
import com.example.tradingbot.persistence.model.order.OrderEntity;
import com.example.tradingbot.persistence.repository.AttachedAlgoOrderRepository;
import com.example.tradingbot.persistence.repository.OrderRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Order}. Вложенный attached
 * protection хранится дочерними строками attached_algo_orders по
 * order_id — оркестрацию сохранения/загрузки коллекции держит этот
 * DataService (в OrderEntity cascade-коллекции нет).
 */
@Service
@RequiredArgsConstructor
public class OrderDataService {

    private final OrderRepository repository;
    private final AttachedAlgoOrderRepository attachedAlgoOrderRepository;
    private final OrderMapper mapper;

    @Transactional
    public Order save(Order order) {
        OrderEntity saved = repository.save(mapper.domainToPersistence(order));
        Order result = mapper.persistenceToDomain(saved);
        result.setAttachedAlgoOrders(saveAttached(saved.getId(), order.getAttachedAlgoOrders()));
        return result;
    }

    @Transactional(readOnly = true)
    public Order getRequiredByInternalId(String internalId) {
        OrderEntity entity = repository.findByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + internalId));
        Order order = mapper.persistenceToDomain(entity);
        order.setAttachedAlgoOrders(loadAttached(entity.getId()));
        return order;
    }

    @Transactional(readOnly = true)
    public Order getRequiredById(Long id) {
        OrderEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
        Order order = mapper.persistenceToDomain(entity);
        order.setAttachedAlgoOrders(loadAttached(entity.getId()));
        return order;
    }

    private List<AttachedAlgoOrder> saveAttached(Long orderId, List<AttachedAlgoOrder> attached) {
        if (isEmpty(attached)) {
            return null;
        }
        return attached.stream()
                .map(item -> saveAttachedItem(orderId, item))
                .collect(Collectors.toList());
    }

    private AttachedAlgoOrder saveAttachedItem(Long orderId, AttachedAlgoOrder item) {
        AttachedAlgoOrderEntity entity = mapper.domainToPersistence(item);
        entity.setOrderId(orderId);
        return mapper.persistenceToDomain(attachedAlgoOrderRepository.save(entity));
    }

    private List<AttachedAlgoOrder> loadAttached(Long orderId) {
        List<AttachedAlgoOrderEntity> entities = attachedAlgoOrderRepository.findByOrderId(orderId);
        if (isEmpty(entities)) {
            return null;
        }
        return entities.stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
