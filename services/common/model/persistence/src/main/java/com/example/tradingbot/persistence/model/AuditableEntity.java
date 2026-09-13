package com.example.tradingbot.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Базовый тип audit-полей persistence-слоя — <b>один на все сервисы со
 * своей базой</b>. Свой в каждом СЛОЕ, с постфиксом слоя
 * (.claude/rules/codestyle.md §«Auditable по слоям»); доменный
 * {@code Auditable} здесь не переиспользуется, а api-слой несёт
 * {@code AuditableApiResponse}.
 *
 * <p><b>Состав колонок бинарен: либо все шесть, либо ни одной</b>
 * (docs/models/domain/other/Auditable.md §«Правило состава колонок»).
 * Именно этот инвариант и требует единственного носителя: пока форма
 * стояла копией у каждого владельца базы, частичный набор у одного из них
 * был неотличим от полного — каждая копия верна сама по себе, а
 * расхождение видно только тому, кто откроет все шесть.
 *
 * <p><b>Наследует его сущность со своей строкой, и не всякая.</b> Запись о
 * происшествии, которое случилось однажды и правок не знает (строка
 * журнала событий), полей аудита не несёт вовсе: три системных поля
 * остались бы пустыми навсегда, а бинарность состава запрещает взять
 * половину.
 *
 * <p><b>Отображение обязано ВИДЕТЬ этот класс.</b> Он лежит вне пакетов
 * сервиса, поэтому владелец, объявляющий отображение явно, называет его
 * пакет в области сканирования наравне со своим; владелец с одним
 * отображением делает то же через {@code @EntityScan}. Сущностей здесь нет
 * — только этот тип, — поэтому попадание пакета в область двух соседних
 * отображений столкновением не является.
 *
 * <p>Системные поля проставляет JPA auditing ({@code JpaAuditConfig} у
 * каждого владельца); биржевые {@code external*} — код, производящий
 * данные. Время — UTC (docs/rules/time-utc.md).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "modified_at")
    private OffsetDateTime modifiedAt;

    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;

    @Column(name = "external_created_at")
    private OffsetDateTime externalCreatedAt;

    @Column(name = "external_modified_at")
    private OffsetDateTime externalModifiedAt;
}
