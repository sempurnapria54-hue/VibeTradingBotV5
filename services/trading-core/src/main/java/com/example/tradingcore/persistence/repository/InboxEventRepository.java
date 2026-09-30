package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.InboxEventEntity;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Запросы по отметкам обработанных событий.
 *
 * <p><b>Наследует {@code Repository}, а не {@code JpaRepository}:</b> тропа у
 * отметки одна — вставка, поглощающая конфликт по ключу; унаследованные
 * {@code save} и {@code existsBy…} завели бы в коде ровно ту форму дедупа
 * «проверить, потом записать», которую ключ и снимает
 * (docs/rules/idempotency-via-unique.md).
 */
public interface InboxEventRepository extends Repository<InboxEventEntity, Long> {

    /**
     * Безопасная вставка отметки по идентичности события; возвращает число
     * вставленных строк — единица означает, что обработка первая, ноль —
     * что событие уже обработано.
     *
     * <p><b>Конкурент, пришедший вторым, ждёт исхода первого, а не падает:</b>
     * вставка по занятому ключу в открытой чужой транзакции ждёт её конца и
     * поглощается при фиксации либо проходит при откате. Поэтому вставка
     * ставится ПЕРВЫМ ходом применения, до следствия.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into inbox_events (event_id, event_type, consumed_at)
            values (:eventId, :eventType, :consumedAt)
            on conflict on constraint uk_inbox_event_id do nothing
            """)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("eventType") String eventType,
                       @Param("consumedAt") OffsetDateTime consumedAt);
}
