package com.example.strategies.persistence.repository;

import com.example.strategies.persistence.model.OutboxEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по строке outbox. */
public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    /**
     * Неопубликованные строки окном <b>в порядке записи</b>
     * (docs/components/OutboxRelayJob.md §«Чтение — окном»).
     *
     * <p><b>Порядок несущий:</b> события одного тенанта обязаны прийти в
     * порядке происшествия, а ключ партиции — тенант, и
     * переупорядочивание на публикации сломало бы то, ради чего ключ
     * выбран.
     *
     * <p><b>Упор в окно неполнотой прохода не считается:</b> остаток
     * заберёт следующий тик, а наблюдаемость даёт метрика глубины outbox.
     */
    @Query("select o from OutboxEntity o where o.publishedAt is null order by o.id asc")
    List<OutboxEntity> findUnpublished(Pageable pageable);

    /**
     * Отметка публикации — точечным запросом, а не записью строки целиком:
     * прочие колонки строки неизменяемы по построению, и переписывать их
     * незачем.
     */
    @Modifying
    @Query("""
            update OutboxEntity o set o.publishedAt = :publishedAt
            where o.id = :id and o.publishedAt is null""")
    int markPublished(@Param("id") Long id, @Param("publishedAt") OffsetDateTime publishedAt);
}
