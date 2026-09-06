package com.example.tradingcore.persistence.service;

import com.example.tradingcore.persistence.model.InboxEventEntity;
import com.example.tradingcore.persistence.repository.InboxEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Граница domain ↔ persistence для отметок обработанных событий.
 *
 * <p><b>Собственной транзакции методы не открывают.</b> Отметка ложится
 * ТОЙ ЖЕ транзакцией, что и следствие события: отметка без следствия
 * потеряла бы событие навсегда, а следствие без отметки применилось бы
 * дважды (docs/rules/idempotency-via-unique.md).
 */
@Service
@RequiredArgsConstructor
public class InboxDataService {

    private final InboxEventRepository repository;

    /** Событие уже обработано. */
    public Boolean isConsumed(String eventId) {
        return repository.existsByEventId(eventId);
    }

    /** Отметить событие обработанным. */
    public void markConsumed(String eventId, String eventType) {
        InboxEventEntity entity = new InboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setConsumedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(entity);
    }
}
