package com.example.auth.domain.model;

import com.example.tradingbot.domain.model.Auditable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Членство — пользователь × тенант × роль
 * (docs/architecture/tenant-and-exchange.md §«Пользователи и роли»).
 *
 * <p>Форма живёт у сервиса, а не в общем артефакте: членство читает и
 * пишет только {@code auth}, а прочие сервисы получают из него уже
 * резолвленный контекст тенанта и роль (docs/architecture/contracts.md).
 *
 * <p><b>Роль есть с первого дня, различает — со второго субъекта.</b>
 * При одном субъекте она всегда {@link Role#OWNER}: перечень закрыт
 * решением держателя, и таблица без колонки потребовала бы миграции ровно
 * тогда, когда появится второй субъект.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Membership extends Auditable {

    /** Внутренний идентификатор. Границу сервиса не пересекает. */
    private Long id;

    /** Идентичность членства между сервисами. */
    private String internalId;

    /** Идентификатор пользователя у провайдера идентичности. */
    private String userId;

    /** {@code internalId} тенанта. */
    private String tenantId;

    /** Роль пользователя в этом тенанте. */
    private Role role;

    /** Членство даёт владение тенантом. */
    public Boolean isOwning() {
        return Role.OWNER.equals(role);
    }

    /**
     * Роль в тенанте. Состав закрыт решением держателя 2026-09-04
     * (ARCH-Q4); граница {@code TRADER} / {@code VIEWER} — деньги.
     */
    public enum Role {

        /** То же, что {@code TRADER}, плюс ключи счетов, приглашения и числа риск-аппетита. */
        OWNER,

        /** Делает то, что двигает капитал: активирует стратегии, выполняет ручные действия с позициями. */
        TRADER,

        /** Читает и не двигает капитал. */
        VIEWER
    }
}
