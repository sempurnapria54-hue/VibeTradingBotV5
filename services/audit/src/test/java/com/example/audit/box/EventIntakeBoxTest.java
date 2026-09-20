package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

/**
 * Группа {@code B1} документа кейсов: приём события — строка журнала как
 * следствие (.claude/tests/cases/audit.md §«B1 — Приём события: строка
 * журнала как следствие»).
 *
 * <p><b>Вход каждой клетки — ЗАПИСЬ БРОКЕРА.</b> Конверт собирается
 * заголовками, тенант — ключом записи, содержимое — телом; собирает всё
 * это тест, а не продуктовый код: имена лежат у потребителя приватной
 * копией, и клетка проверяет именно их.
 *
 * <p><b>Предусловие «строки обеих пар есть» ставит ТИК, а не вставка.</b>
 * Состав пар ведёт только он (docs/rules/durable-consumer-reception.md
 * §«Строки пары ещё нет — не пишется ничего, и это верно у всех величин
 * приёма»), и вставленная руками строка говорила бы о нашей вставке, а не
 * о поведении сервиса.
 *
 * <p><b>Конец обработки наблюдается СМЕЩЕНИЕМ.</b> Строка журнала есть у
 * принятого, а у повтора, поглощённого ключом дедупа, её не появляется
 * вовсе: ожидание по числу строк на таких клетках истекало бы по таймауту
 * при исправной системе.
 */
class EventIntakeBoxTest extends SharedAuditBox {

    /** Тема первого производителя: в неё ходит большинство клеток. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Тема второго производителя: ею наблюдается раздельность пар. */
    private static final String STRATEGY = AuditSubstrate.STRATEGY_TOPIC;

    /** Колонка радиуса сделки. */
    private static final String DEAL_COLUMN = "deal_internal_id";

    /** Колонка радиуса определения стратегии. */
    private static final String STRATEGY_COLUMN = "strategy_internal_id";

    @Test
    @DisplayName("B1.1 — Штатный приём кладёт строку и двигает момент последнего принятого")
    void aRegularReceptionWritesTheRowAndMovesTheLastAcceptedMoment() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        OffsetDateTime beforeSending = now();
        Long strategyEndBefore = Wire.endOffset(STRATEGY);

        publish(CORE, "E-1", occurredAt, Bodies.reference());
        awaitConsumed(CORE);

        Map<String, Object> row = record();
        assertThat(row.get("event_id")).isEqualTo("E-1");
        assertThat(row.get("tenant_id")).isEqualTo(TENANT);
        assertThat(row.get("event_type")).isEqualTo(DEAL_OPENED);
        assertThat(instant(row, OCCURRED_COLUMN)).isEqualTo(occurredAt.toInstant());
        assertThat(row.get("version")).isEqualTo(Integer.valueOf(FORM_VERSION));
        assertThat(row.get("trace_context")).isEqualTo(envelope("E-1", occurredAt).get(TRACE_CONTEXT));
        assertThat(document(String.valueOf(row.get("content"))))
                .isEqualTo(document(Bodies.reference()));
        // Момент приёма ставит СТОРОНА ПРИЁМА: он отличается от момента
        // происшествия и лежит между подачей и ассертом.
        assertThat(instant(row, RECORDED_COLUMN)).isNotEqualTo(occurredAt.toInstant());
        assertThat(instant(row, RECORDED_COLUMN))
                .isBetween(beforeSending.toInstant(), now().toInstant());

        assertThat(instant(pair(CORE), LAST_ACCEPTED_COLUMN)).isEqualTo(occurredAt.toInstant());
        assertThat(pair(CORE).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        // У соседней пары не изменилось НИЧЕГО: величины одной пары в
        // другую не протекают.
        assertThat(pair(STRATEGY).get(LAST_ACCEPTED_COLUMN)).isNull();
        assertThat(pair(STRATEGY).get(GAP_COLUMN)).isNull();
        assertThat(pair(STRATEGY).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        // Наружу не ушло ничего: сервис не публикует и соседей не зовёт.
        assertThat(Wire.endOffset(STRATEGY)).isEqualTo(strategyEndBefore);
    }

    @Test
    @DisplayName("B1.2 — Тенант берётся ключом записи, а не заголовком")
    void theTenantComesFromTheRecordKeyRatherThanFromAHeader() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        Map<String, String> headers = new LinkedHashMap<>(envelope("E-2", occurredAt));
        headers.put("tenantId", SECOND_TENANT);

        Wire.publish(CORE, TENANT, headers, Bodies.reference());
        awaitConsumed(CORE);

        Map<String, Object> row = record();
        assertThat(row.get("tenant_id")).isEqualTo(TENANT);
        // Значение заголовка не попало НИКУДА: ни в одну колонку строки.
        assertThat(row.values().stream().map(String::valueOf))
                .noneMatch(value -> value.contains(SECOND_TENANT));

        assertThat(journal(SECOND_TENANT, occurredAt.minusHours(1), now()).records()).isEmpty();
        assertThat(journal(TENANT, occurredAt.minusHours(1), now()).records()).hasSize(1);
    }

    @Test
    @DisplayName("B1.3 — Колонки радиуса заполняются одноимённым компонентом верхнего уровня")
    void theRadiusColumnsAreFilledFromTheSameNamedTopLevelComponent() {
        givenReceptionStateRows();
        String content = Bodies.withRadius("A-1", "I-1", "D-1", "S-1");

        publish(CORE, "E-3", momentsAgo(Duration.ofMinutes(5)), content);
        awaitConsumed(CORE);

        Map<String, Object> row = record();
        assertThat(row.get("exchange_account_internal_id")).isEqualTo("A-1");
        assertThat(row.get("instrument_internal_id")).isEqualTo("I-1");
        assertThat(row.get(DEAL_COLUMN)).isEqualTo("D-1");
        assertThat(row.get(STRATEGY_COLUMN)).isEqualTo("S-1");
        // Вынос в колонку содержимого НЕ урезает.
        assertThat(document(String.valueOf(row.get("content")))).isEqualTo(document(content));
    }

    @Test
    @DisplayName("B1.4 — Идентичность на глубине колонки не даёт")
    void anIdentityAtDepthGivesNoColumn() {
        givenReceptionStateRows();
        String content = Bodies.nestedRadius("D-2", "S-2");

        publish(CORE, "E-4", momentsAgo(Duration.ofMinutes(5)), content);
        awaitConsumed(CORE);

        Map<String, Object> row = record();
        assertThat(row.get(DEAL_COLUMN)).isNull();
        assertThat(row.get(STRATEGY_COLUMN)).isNull();
        assertThat(pair(CORE).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        // Содержимое несёт вложенные значения целиком.
        assertThat(document(String.valueOf(row.get("content")))).isEqualTo(document(content));
    }

    @Test
    @DisplayName("B1.5 — Одноимённый компонент, не являющийся строкой, колонки не даёт")
    void aSameNamedComponentThatIsNotAStringGivesNoColumn() {
        givenReceptionStateRows();

        publish(CORE, "E-5a", momentsAgo(Duration.ofMinutes(6)), Bodies.dealAsObject());
        publish(CORE, "E-5b", momentsAgo(Duration.ofMinutes(5)), Bodies.dealAsNumber());
        awaitConsumed(CORE);

        List<Map<String, Object>> written = records();
        assertThat(written).hasSize(2);
        assertThat(written).allSatisfy(row -> assertThat(row.get(DEAL_COLUMN)).isNull());
        assertThat(pair(CORE).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B1.6 — Неизвестный класс события принимается наравне с известным")
    void anUnknownEventClassIsAcceptedJustLikeAKnownOne() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        Map<String, String> headers = new LinkedHashMap<>(envelope("E-6", occurredAt));
        headers.put(EVENT_TYPE, "CLASS_NO_PRODUCER_DECLARES");
        String content = Bodies.withRadius("A-6", "I-6", "D-6", "S-6");

        Wire.publish(CORE, TENANT, headers, content);
        awaitConsumed(CORE);

        Map<String, Object> row = record();
        assertThat(row.get("event_type")).isEqualTo("CLASS_NO_PRODUCER_DECLARES");
        assertThat(document(String.valueOf(row.get("content")))).isEqualTo(document(content));
        assertThat(row.get(DEAL_COLUMN)).isEqualTo("D-6");
        assertThat(pair(CORE).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        // Лаг не растёт: зафиксированное смещение догнало конец темы.
        assertThat(Wire.committedOffset(AuditSubstrate.CONSUMER_GROUP, CORE))
                .isEqualTo(Wire.endOffset(CORE));
    }

    @Test
    @DisplayName("B1.7 — Содержимое ложится как доставлено")
    void theContentIsStoredExactlyAsDelivered() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));

        publish(CORE, "E-7", occurredAt, Bodies.richDocument());
        awaitConsumed(CORE);

        // Читается ПОВЕРХНОСТЬЮ: у содержимого читатель объявлен, и он
        // получает документ, а не строку с экранированием.
        Map<String, Object> read = journal(TENANT, occurredAt.minusHours(1), now()).records().getFirst();
        assertThat(read.get("content")).isEqualTo(document(Bodies.richDocument()));
    }

    @Test
    @DisplayName("B1.8 — Пустой контекст трассировки законен")
    void anAbsentTraceContextIsLawful() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        Map<String, String> headers = new LinkedHashMap<>(envelope("E-8", occurredAt));
        headers.remove(TRACE_CONTEXT);

        Wire.publish(CORE, TENANT, headers, Bodies.reference());
        awaitConsumed(CORE);

        Map<String, Object> row = record();
        assertThat(row.get("trace_context")).isNull();
        // Отсутствие трассировки аномалией не объявляется ничем.
        assertThat(pair(CORE).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        assertThat(pair(CORE).get("lag_gap_at")).isNull();
    }

    @Test
    @DisplayName("B1.9 — Повторная доставка поглощается ключом и отказом не является")
    void aRedeliveryIsAbsorbedByTheKeyAndIsNotAFailure() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        publish(CORE, "E-9", occurredAt, Bodies.reference());
        awaitConsumed(CORE);
        Object recordedAtOfFirst = record().get(RECORDED_COLUMN);

        publish(CORE, "E-9", occurredAt, Bodies.reference());
        awaitConsumed(CORE);

        assertThat(rows.count(JOURNAL_TABLE)).isEqualTo(1L);
        assertThat(record().get(RECORDED_COLUMN)).isEqualTo(recordedAtOfFirst);
        // Обработка штатна: смещение продвинулось до конца темы, лаг пуст.
        assertThat(Wire.committedOffset(AuditSubstrate.CONSUMER_GROUP, CORE))
                .isEqualTo(Wire.endOffset(CORE));
        assertThat(pair(CORE).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B1.10 — Повтор с ИНЫМ содержимым лежащую строку не переписывает")
    void aRedeliveryWithOtherContentDoesNotOverwriteTheStoredRow() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(10));
        publish(CORE, "E-10", occurredAt, Bodies.reference());
        awaitConsumed(CORE);
        Map<String, Object> before = record();

        publish(CORE, "E-10", momentsAgo(Duration.ofMinutes(1)), Bodies.richDocument());
        awaitConsumed(CORE);

        assertThat(rows.count(JOURNAL_TABLE)).isEqualTo(1L);
        // Строка неизменяема ЦЕЛИКОМ: содержимое, момент происшествия,
        // момент приёма.
        assertThat(record()).isEqualTo(before);
    }

    @Test
    @DisplayName("B1.11 — Повтор снимает флаг остановки")
    void aRedeliveryClearsTheHaltFlag() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        publish(CORE, "E-11", occurredAt, Bodies.reference());
        awaitConsumed(CORE);
        haltReceptionOf(CORE);

        publish(CORE, "E-11", occurredAt, Bodies.reference());
        awaitConsumed(CORE);

        assertThat(pair(CORE).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        assertThat(rows.count(JOURNAL_TABLE)).isEqualTo(1L);
        assertThat(journal(TENANT, occurredAt.minusHours(1), now()).completeness()
                .get("continuityClaimable")).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B1.12 — Момент последнего принятого двигается только вперёд")
    void theLastAcceptedMomentMovesForwardOnly() {
        givenReceptionStateRows();
        OffsetDateTime later = momentsAgo(Duration.ofMinutes(5));
        OffsetDateTime earlier = momentsAgo(Duration.ofDays(1));
        publish(CORE, "E-12a", later, Bodies.reference());
        awaitConsumed(CORE);

        publish(CORE, "E-12b", earlier, Bodies.reference());
        awaitConsumed(CORE);

        assertThat(rows.count(JOURNAL_TABLE)).isEqualTo(2L);
        assertThat(instant(pair(CORE), LAST_ACCEPTED_COLUMN)).isEqualTo(later.toInstant());
        // Ряд возраста выводится из этой же колонки — и потому скачка в
        // сутки не показывает.
        givenReceptionStateRows();
        assertThat(ageRowOf(CORE)).isLessThan(Duration.ofHours(1).toMillis());
    }

    @Test
    @DisplayName("B1.13 — Пары двигаются раздельно")
    void thePairsMoveSeparately() {
        givenReceptionStateRows();
        OffsetDateTime coreMoment = momentsAgo(Duration.ofMinutes(20));
        OffsetDateTime strategyMoment = momentsAgo(Duration.ofMinutes(5));

        publish(CORE, "E-13a", coreMoment, Bodies.reference());
        publish(STRATEGY, "E-13b", strategyMoment, Bodies.reference());
        awaitConsumed(CORE);
        awaitConsumed(STRATEGY);

        assertThat(rows.count(JOURNAL_TABLE)).isEqualTo(2L);
        assertThat(instant(pair(CORE), LAST_ACCEPTED_COLUMN)).isEqualTo(coreMoment.toInstant());
        assertThat(instant(pair(STRATEGY), LAST_ACCEPTED_COLUMN)).isEqualTo(strategyMoment.toInstant());
        // Ряды экспорта несут по значению на тему, различённые меткой.
        givenReceptionStateRows();
        assertThat(ageRowOf(CORE)).isNotNull();
        assertThat(ageRowOf(STRATEGY)).isNotNull();
        assertThat(ageRowOf(CORE)).isGreaterThan(ageRowOf(STRATEGY));
    }

    @Test
    @DisplayName("B1.14 — Момент приёма ставит сторона приёма, а не конверт и не база")
    void theReceptionSideStampsTheReceptionMoment() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofDays(1));

        publish(CORE, "E-14", occurredAt, Bodies.reference());
        awaitConsumed(CORE);

        Map<String, Object> row = record();
        assertThat(instant(row, OCCURRED_COLUMN)).isEqualTo(occurredAt.toInstant());
        assertThat(instant(row, RECORDED_COLUMN))
                .isBetween(now().minusMinutes(1).toInstant(), now().toInstant());
        assertThat(Duration.between(instant(row, OCCURRED_COLUMN), instant(row, RECORDED_COLUMN)))
                .isBetween(Duration.ofHours(23), Duration.ofHours(25));

        // Оси разведены: нижняя граница полноты стои́т на оси ПРИЁМА, и
        // суточная давность происшествия её не опускает.
        Object lowerBound = journal(TENANT, occurredAt.minusHours(1), now())
                .completeness().get("lowerBound");
        assertThat(OffsetDateTime.parse(String.valueOf(lowerBound)).toInstant())
                .isAfter(occurredAt.toInstant());
    }

    /** Документ как разобранная карта: сравнение идёт по составу, а не по тексту. */
    private static Map<String, Object> document(String json) {
        return JsonParserFactory.getJsonParser().parseMap(json);
    }

    /**
     * Ставит флаг остановки приёма по паре — durable-вход клетки.
     *
     * <p>Сообщением его не поставить: отравленная запись заняла бы
     * единственный поток слушателя бесконечными повторами, и повтор,
     * который клетка подаёт следом, не дошёл бы до обработки вовсе.
     */
    private void haltReceptionOf(String topic) {
        rows.write("update reception_states set reception_halted = true where topic = ?", topic);
    }
}
