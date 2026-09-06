package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы по отметкам обработанных событий. */
public interface InboxEventRepository extends JpaRepository<InboxEventEntity, Long> {

    /** Событие с такой идентичностью уже обработано. */
    Boolean existsByEventId(String eventId);
}
