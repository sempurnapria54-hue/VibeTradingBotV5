package com.example.statistics.integration.internal.event.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Принятое событие в форме слоя сообщения — то, что статистика достала из
 * конверта и содержимого (.claude/rules/codestyle.md §«Слой сообщения:
 * внутренняя шина»).
 *
 * <p><b>Содержимого как доставлено форма НЕ несёт, и это несущий
 * инвариант, а не экономия.</b> Строка, хранящая содержимое, есть журнал, а
 * журнал у системы один и лежит у своего владельца
 * (docs/models/domain/other/StatisticsFact.md §«Почему это не второй
 * журнал»). Поэтому здесь стоя́т <b>разобранные операнды зёрен</b> — ровно
 * те, которые объявлены домом величин
 * (docs/rules/statistics-aggregates.md), — и ничего сверх.
 *
 * <p><b>Форма одна на оба зерна, а таблиц две.</b> Разбирает содержимое
 * читатель конверта, а <b>раскладывает</b> класс ровно в одну таблицу
 * маппер: класс, попавший в две, оставил бы две отметки обработанного
 * (там же, §«Отметка обработанного»).
 *
 * <p><b>Читателя из JSON у формы нет:</b> она собирается на границе приёма
 * и живёт только внутри прохода, поэтому {@code @Value}, а не запись
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
@Value
@Builder
public class StatisticsEventMessage {

    /** Идентичность принятого события; она же отметка обработанного. */
    String eventId;

    /** Тенант-владелец события; на проводе приезжает ключом записи. */
    String tenantId;

    /** Класс события — имя из перечня производителя. */
    String eventType;

    /** Момент происшествия из конверта. Время UTC. */
    OffsetDateTime occurredAt;

    /** Версия формы содержимого из конверта. */
    Integer version;

    /** Биржевой счёт: ключ обоих зёрен. */
    String exchangeAccountInternalId;

    /** Определение стратегии: ключ сделочного зерна; пусто законно. */
    String strategyInternalId;

    // --- операнды сделочного зерна (docs/rules/statistics-aggregates.md) ---

    /** Расчётная валюта результата; пусто — «валюта не резолвилась». */
    String resultCurrency;

    /** Сделка приняла риск: популяция долей — она (там же, §«Популяция долей»). */
    Boolean tookRisk;

    /** Чистый результат сделки. */
    BigDecimal netResult;

    /** Комиссия. */
    BigDecimal fee;

    /** Финансирование. */
    BigDecimal funding;

    /** Штраф ликвидации. */
    BigDecimal liquidationPenalty;

    /** Плановый риск: знаменатель R. */
    BigDecimal plannedRisk;

    /** Граф сделки полон: конъюнкт доступности ценового результата. */
    Boolean graphComplete;

    /** Исход закрытия: ликвидация, принудительное сокращение, неопределён. */
    String closeOutcome;

    /** Состояние сверки P&amp;L. */
    String reconciliationStatus;

    /** Полнота разбивки движений средств. */
    String breakdownIncomplete;

    /** Доступность базы риска. */
    String riskBenchmarkAvailability;

    // --- разрезы зерна происшествий ---

    /** Жёсткость поднятой ступени защиты; пусто — разрез у класса не определён. */
    String holdRung;

    /** Критичность отчёта о происшествии; пусто — разрез у класса не определён. */
    String anomalySeverity;

    /** Код операции: им различается ручная тропа, а не актором строки. */
    String operationCode;
}
