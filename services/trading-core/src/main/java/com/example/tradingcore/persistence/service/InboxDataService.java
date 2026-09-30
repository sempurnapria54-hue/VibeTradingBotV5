package com.example.tradingcore.persistence.service;

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

    /**
     * Отметить событие обработанным, если отметки ещё нет; {@code true} —
     * отметка легла этим вызовом, то есть обработка первая и следствие
     * применяется, {@code false} — повторная доставка, следствия нет.
     *
     * <p><b>Решает вставка по ключу, а не проверка перед ней</b>
     * (docs/rules/idempotency-via-unique.md): проверка «обработано ли уже»
     * не атомарна, и конкурентный второй ход прошёл бы её до фиксации
     * первого.
     */
    public Boolean markConsumedIfAbsent(String eventId, String eventType) {
        return repository.insertIfAbsent(eventId, eventType, OffsetDateTime.now(ZoneOffset.UTC)) > 0;
    }
}
