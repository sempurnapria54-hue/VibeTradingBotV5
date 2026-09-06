package com.example.tradingcore.persistence.service;

import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.mapping.OrderMapper;
import com.example.tradingcore.persistence.model.AttachedAlgoOrderEntity;
import com.example.tradingcore.persistence.model.OrderEntity;
import com.example.tradingcore.persistence.repository.AttachedAlgoOrderRepository;
import com.example.tradingcore.persistence.repository.OrderRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для ноги.
 *
 * <p>Встроенная защита хранится дочерними строками по {@code order_id} —
 * каскадной коллекции у строки ноги нет, оркестрацию держит этот сервис.
 *
 * <p><b>Защиты грузятся ПАКЕТОМ на все ноги сделки.</b> Запрос на ногу
 * давал бы N+1 обращений там, где число ног растёт вместе со стратегией
 * (.claude/rules/codestyle.md §«Выборка данных»).
 */
@Service
@RequiredArgsConstructor
public class OrderDataService {

    private final OrderRepository repository;
    private final AttachedAlgoOrderRepository attachedAlgoOrderRepository;
    private final OrderMapper mapper;

    /**
     * Пишет ногу вместе с её встроенными защитами: у защиты своя строка, и
     * без неё нога сохранилась бы без объявленного покрытия.
     */
    @Transactional
    public Order save(Order order) {
        OrderEntity saved = repository.save(mapper.domainToPersistence(order));
        Order result = mapper.persistenceToDomain(saved);
        result.setAttachedAlgoOrders(saveAttached(saved.getId(), order.getAttachedAlgoOrders()));
        return result;
    }

    /**
     * Нога вместе со своими встроенными защитами; нет — исключение.
     * Читатель — исполнитель, которому нога назначена командой: пустая
     * цель у него означает наш дефект, а не отсутствие факта.
     */
    @Transactional(readOnly = true)
    public Order getRequiredById(Long id) {
        OrderEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
        Order order = mapper.persistenceToDomain(entity);
        order.setAttachedAlgoOrders(loadAttached(List.of(entity)).get(id));
        return order;
    }

    /** Встроенная защита по своему идентификатору; нет — исключение. */
    @Transactional(readOnly = true)
    public AttachedAlgoOrder getRequiredAttachedById(Long id) {
        return attachedAlgoOrderRepository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Attached protection not found: " + id));
    }

    /**
     * Пишет ОДНУ уже заведённую встроенную защиту: родитель у неё есть, и
     * оркестрация коллекции здесь не нужна.
     */
    @Transactional
    public AttachedAlgoOrder saveAttached(AttachedAlgoOrder attached) {
        return mapper.persistenceToDomain(
                attachedAlgoOrderRepository.save(mapper.domainToPersistence(attached)));
    }

    /**
     * Заявка по нашему клиентскому идентификатору; пусто — строки нет.
     *
     * <p>Встроенные защиты сюда не доливаются: читателю нужен статус
     * самой ноги, а не её граф (.claude/rules/codestyle.md §«Выборка
     * данных»).
     */
    @Transactional(readOnly = true)
    public Optional<Order> findByInternalId(String internalId) {
        return repository.findByInternalId(internalId).map(mapper::persistenceToDomain);
    }

    /** Ноги сделки со своими встроенными защитами — вход сборки графа. */
    @Transactional(readOnly = true)
    public List<Order> findByDealId(Long dealId) {
        List<OrderEntity> entities = repository.findByDealId(dealId);
        if (isEmpty(entities)) {
            return List.of();
        }
        Map<Long, List<AttachedAlgoOrder>> attachedByOrder = loadAttached(entities);
        return entities.stream()
                .map(entity -> withAttached(entity, attachedByOrder))
                .collect(Collectors.toList());
    }

    private Order withAttached(OrderEntity entity, Map<Long, List<AttachedAlgoOrder>> attachedByOrder) {
        Order order = mapper.persistenceToDomain(entity);
        order.setAttachedAlgoOrders(attachedByOrder.get(entity.getId()));
        return order;
    }

    /**
     * Защиты всех переданных ног одним запросом, разложенные по родителю.
     * Нога без защиты в раскладке отсутствует — и получает пустое поле, а
     * не пустой список: пустой список означал бы «защит нет», тогда как
     * различать эти два случая по коллекции нельзя.
     */
    private Map<Long, List<AttachedAlgoOrder>> loadAttached(List<OrderEntity> orders) {
        List<Long> orderIds = orders.stream()
                .map(OrderEntity::getId)
                .collect(Collectors.toList());
        return attachedAlgoOrderRepository.findByOrderIdIn(orderIds).stream()
                .collect(groupingBy(AttachedAlgoOrderEntity::getOrderId,
                        Collectors.mapping(mapper::persistenceToDomain, Collectors.toList())));
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
}
