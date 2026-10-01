package com.example.auth.persistence.repository;

import com.example.auth.persistence.model.ExchangeAccountEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по строке ExchangeAccount. */
public interface ExchangeAccountRepository extends JpaRepository<ExchangeAccountEntity, Long> {

    Optional<ExchangeAccountEntity> findByInternalId(String internalId);

    /** Счета тенанта — реестр, который читает владелец. */
    List<ExchangeAccountEntity> findAllByTenantId(String tenantId);

    /**
     * Точечная запись момента и автора изменения строки — при условии
     * статуса.
     *
     * <p><b>Мимо сущности, потому что ни одна колонка строки не меняется:</b>
     * сохранение неизменённой сущности грязной её не делает, и слушатель
     * аудита не сработал бы вовсе. Автора и момент поэтому ставит сам запрос
     * (docs/models/domain/other/Auditable.md §«Системные поля и точечная запись»).
     *
     * <p><b>Охрана статуса стои́т в самом запросе</b>, а не только у
     * вызывающего: запись берёт замок строки, и ход, переводящий счёт в
     * другой статус параллельно, упорядочивается с ней базой — проверка
     * чтением до записи этого не давала бы.
     *
     * @return число записанных строк: {@code 0} — строка не в ожидаемом статусе
     */
    @Modifying
    @Query("""
            update ExchangeAccountEntity a set a.modifiedAt = :modifiedAt, a.modifiedBy = :modifiedBy
            where a.id = :id and a.status = :expectedStatus""")
    Integer markModifiedInStatus(@Param("id") Long id,
                                 @Param("expectedStatus") String expectedStatus,
                                 @Param("modifiedAt") OffsetDateTime modifiedAt,
                                 @Param("modifiedBy") String modifiedBy);
}
