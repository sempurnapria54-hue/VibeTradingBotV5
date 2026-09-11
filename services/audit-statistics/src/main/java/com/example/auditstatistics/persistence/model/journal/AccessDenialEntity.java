package com.example.auditstatistics.persistence.model.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция
 * {@link com.example.auditstatistics.domain.model.AccessDenial} — таблица
 * {@code access_denials} базы журнала
 * (docs/models/domain/other/AccessDenial.md §Персистентность).
 *
 * <p>Класс отказа хранится строкой значением {@code name()} доменного
 * перечня, без {@code @Enumerated}: енумы объявляются только в доменном
 * слое (.claude/rules/codestyle.md §«Слои моделей и enum'ы»).
 *
 * <p><b>Биржевые поля аудита остаются пустыми навсегда</b>, и это
 * названная цена бинарного правила состава, а не дефект модели: у
 * отвергнутого вызова биржевого домена нет вовсе, и пустота здесь означает
 * «события не было», а не «не добыто»
 * (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Ширины колонок заданы явно</b>, потому что значение
 * {@code surface} приходит от вызывающего: без потолка длинный путь ронял
 * бы вставку на ограничении колонки — то есть отказ доступа стирал бы
 * собственный след. Усечение делает писатель, потолок объявлен здесь и в
 * миграции.
 */
@Getter
@Setter
@Entity
@Table(name = "access_denials")
public class AccessDenialEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Внешняя идентичность строки; ключ дедупа ею не является. */
    @Column(name = "internal_id", nullable = false, updatable = false, length = 64)
    private String internalId;

    /** Куда стучались: метод и путь, усечённые писателем по этому потолку. */
    @Column(name = "surface", nullable = false, updatable = false, length = 256)
    private String surface;

    /** Класс отказа строкой — значение {@code name()} доменного перечня. */
    @Column(name = "outcome", nullable = false, updatable = false, length = 32)
    private String outcome;

    /** Принятый принципал; пусто ⟺ outcome = PRINCIPAL_ABSENT. */
    @Column(name = "principal", updatable = false, length = 64)
    private String principal;
}
