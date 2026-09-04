package com.example.tradingbot.domain.safety;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Журналируемый объект расследования и обработки safety-инцидента: фиксирует
 * аномалию (расхождение локального и биржевого состояния либо нарушение
 * риск-политики/инварианта), её обработку реактивным холдом и слепки
 * состояния до/после kill-switch. Не валидатор и не команда — аудит-журнал
 * (docs/models/domain/other/AnomalyReport.md, docs/lifecycles/AnomalyReport.md).
 * Статусы и переходы — на {@link Status}. Наследует audit-поля от
 * {@link Auditable}.
 */
@Getter
@Setter
@NoArgsConstructor
public class AnomalyReport extends Auditable {

    /** Внутренний идентификатор отчёта. */
    private Long id;

    /** Межсервисный идентификатор отчёта. */
    private String internalId;

    /** Биржа, на которой обнаружена аномалия (обязательно). */
    private Long exchangeId;

    /** Инструмент аномалии; {@code null}, если однозначно не определён. */
    private Long instrumentId;

    /**
     * Сущность, о которой отчёт, — четвёртая величина ключа дедупа у
     * отчёта БЕЗ блокировки: его состояние держится на сущности, а не на
     * объекте радиуса (docs/models/domain/other/AnomalyReport.md).
     */
    private String subjectExternalId;

    /** Скоуп реакции: инструмент (L3) или биржа (L4). Снимает неоднозначность instrumentId=null. */
    private HoldScope scope;

    /** Текущий статус обработки (см. lifecycle). */
    private Status status;

    /** Критичность аномалии (политика блокировки торговли до разбора). */
    private Severity severity;

    /** Машинно-читаемый код аномалии. */
    private String code;

    /** Текст непредвиденной ошибки обработки; заполняется при {@code status = ERROR}. */
    private String message;

    /** Снимок локального состояния (БД) до обработки (jsonb). */
    private String internalBefore;

    /** Снимок внешнего состояния (биржа) до обработки (jsonb). */
    private String externalBefore;

    /** Снимок локального состояния (БД) после обработки (jsonb). */
    private String internalAfter;

    /** Снимок внешнего состояния (биржа) после обработки (jsonb). */
    private String externalAfter;

    /** Статус обработки инцидента реактивным контуром. */
    public enum Status {

        /** Создан при обнаружении; заполнены before-слепки. */
        CREATED,

        /** Обработка начата. */
        IN_PROGRESS,

        /** Kill-switch отработал (между before- и after-слепками). */
        KILL_SWITCH_EXECUTED,

        /** Терминал: автообработка завершена, after-слепки собраны. */
        COMPLETED,

        /** Терминал-ошибка: непредвиденный сбой обработки; заполнен {@code message}. */
        ERROR
    }

    /** Критичность аномалии: задаёт политику блокировки торговли (enforcement — на статусе инструмента/биржи). */
    public enum Severity {

        /** Критичная: торговля по scope остаётся запрещённой до ручного разбора. */
        CRITICAL,

        /** Некритичная: после kill-switch торговля может быть снова разрешена. */
        NON_CRITICAL
    }
}
