package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.OrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Запросы по строке ноги.
 *
 * <p>Ноги читаются ОДНИМ запросом на сделку и раскладываются по траншам в
 * памяти. Запрос на транш давал бы N+1 обращений на сетке из N траншей, а
 * число траншей задаёт стратегия — то есть росло бы вместе с ней.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByDealId(Long dealId);

    /**
     * Строка заявки по НАШЕМУ клиентскому идентификатору — операнд
     * детектора «локально терминальная сущность жива на бирже»
     * (docs/components/AnomalyJob.md §«Что ищет», {@code A8}).
     *
     * <p>Отсутствие строки ответом «терминальна» не является: это предмет
     * другого детектора, и путать «мы её закрыли» с «мы её не заводили»
     * нельзя.
     */
    Optional<OrderEntity> findByInternalId(String internalId);
}
