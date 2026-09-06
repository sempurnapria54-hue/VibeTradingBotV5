package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.AttachedAlgoOrderEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Запросы по строке встроенной защиты.
 *
 * <p>Защиты грузятся ПАКЕТОМ по перечню родителей, а не запросом на ногу:
 * иначе загрузка графа давала бы N+1 обращений на сделке с N ногами
 * (.claude/rules/codestyle.md §Выборка данных).
 */
public interface AttachedAlgoOrderRepository extends JpaRepository<AttachedAlgoOrderEntity, Long> {

    List<AttachedAlgoOrderEntity> findByOrderIdIn(Collection<Long> orderIds);
}
