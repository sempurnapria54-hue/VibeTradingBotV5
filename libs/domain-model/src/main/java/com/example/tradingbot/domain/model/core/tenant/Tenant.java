package com.example.tradingbot.domain.model.core.tenant;

import com.example.tradingbot.domain.model.Auditable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Тенант — единица владения капиталом, ключами и риск-аппетитом
 * (docs/models/domain/core/Tenant.md). Всё, чем система рискует,
 * принадлежит тенанту; пользователь тенантом не является — он в нём
 * состоит членством с ролью.
 *
 * <p>Форму несёт эта библиотека, владеет данными и пишет их сервис
 * {@code auth}. Чисел риск-аппетита здесь нет намеренно: они принадлежат
 * тенанту, но хранит и энфорсит их {@code trading-core} — единственный,
 * кто принимает риск.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tenant extends Auditable {

    /** Внутренний идентификатор. Границу сервиса не пересекает. */
    private Long id;

    /**
     * Идентичность тенанта наружу и между сервисами. Присваивается при
     * создании, дальше неизменяема: на неё ссылаются чужие базы и уже
     * опубликованные события.
     */
    private String internalId;

    /**
     * Имя тенанта, видимое человеку. Уникальным быть не обязано —
     * тенанты различаются {@link #internalId}.
     */
    private String name;

    /** Состояние тенанта. */
    private Status status;

    /**
     * Тенант набирает риск.
     *
     * <p>Предикат на модели, а не сравнение статуса в сервисе: вопрос
     * «можно ли набирать риск» задаёт не только ядро, и ответ обязан быть
     * один (.claude/rules/codestyle.md §«Вложенность и rich-модели»).
     */
    public Boolean isRiskTaking() {
        return Status.ACTIVE.equals(status);
    }

    /** Состояние тенанта. */
    public enum Status {

        /** Штатное состояние: тенант торгует, набирает риск, принимает членства. */
        ACTIVE,

        /**
         * Приостановлен. Набор риска прекращается, живые сделки
         * сопровождаются до терминала: приостановка тенанта — не причина
         * бросить открытую позицию. Принудительное закрытие есть торговое
         * действие и делается ручной операцией владельца, а не побочным
         * следствием административной приостановки.
         */
        SUSPENDED
    }
}
