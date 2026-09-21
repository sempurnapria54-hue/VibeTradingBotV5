package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B1} документа кейсов: приём события — факт своего зерна как
 * следствие (.claude/tests/cases/statistics.md §«B1 — Приём события: факт
 * своего зерна как следствие»).
 *
 * <p><b>Вход каждой клетки — ЗАПИСЬ БРОКЕРА.</b> Конверт собирается
 * заголовками, тенант — ключом записи, содержимое — телом; собирает всё это
 * тест, а не продуктовый код: имена лежат у потребителя приватной копией, и
 * клетка проверяет именно их.
 *
 * <p><b>Предусловие «строка пары есть» ставит ТИК, а не вставка.</b> Состав
 * пар ведёт только он (docs/rules/durable-consumer-reception.md §«Строки пары
 * ещё нет — не пишется ничего, и это верно у всех величин приёма»), и
 * вставленная руками строка говорила бы о нашей вставке, а не о поведении
 * сервиса.
 *
 * <p><b>Конец обработки наблюдается СМЕЩЕНИЕМ.</b> Строка факта есть у
 * принятого несомого класса, а у повтора и у события ненесомого класса её не
 * появляется вовсе: ожидание по числу строк на таких клетках истекало бы по
 * таймауту при исправной системе.
 *
 * <p><b>Строка АГРЕГАТА асинхронным следом не является.</b> Её производит
 * такт пересчёта, который подаёт сам тест; клетка, ждущая её появления,
 * ждала бы того, чего без такта не бывает.
 *
 * <p><b>Клетки, чей предмет доходит до строки агрегата, кладут событие в
 * ПОЛНОЧЬ суток окна.</b> Проход не пишет суток, начавшихся раньше первого
 * факта ряда, и факт в середине суток оставил бы их непокрытыми — то есть
 * клетка краснела бы по охране отбора, а не по своему предмету
 * ({@link StatisticsBox#midnightDaysAgo}).
 */
class EventIntakeBoxTest extends SharedStatisticsBox {

    /** Биржевой счёт — обязательный ключ обоих зёрен. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — компонент ключа сделочного зерна. */
    private static final String STRATEGY = "S-1";

    /** Сутки окна, в полночь которых кладут событие клетки о пересчёте. */
    private static final Integer BUCKET_DAYS_BACK = 1;

    @Test
    @DisplayName("B1.1 — Штатный приём терминального события кладёт сделочный факт")
    void aRegularTerminalReceptionWritesTheDealFact() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        Long endBefore = Wire.endOffset(topic());

        publish("E-1", DEAL_CLOSED, occurredAt, Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();

        Map<String, Object> fact = dealFact();
        assertThat(fact.get("event_id")).isEqualTo("E-1");
        assertThat(fact.get("tenant_id")).isEqualTo(TENANT);
        assertThat(fact.get("exchange_account_internal_id")).isEqualTo(ACCOUNT);
        assertThat(fact.get("strategy_internal_id")).isEqualTo(STRATEGY);
        assertThat(fact.get("result_currency")).isEqualTo(Bodies.CURRENCY);
        assertThat(instant(fact, CLOSED_COLUMN)).isEqualTo(occurredAt.toInstant());
        // Операнды результата равны поданным — все, а не выборочно.
        assertThat(decimal(fact, "net_result")).isEqualByComparingTo(Bodies.NET_RESULT);
        assertThat(decimal(fact, "fee")).isEqualByComparingTo(Bodies.FEE);
        assertThat(decimal(fact, "funding")).isEqualByComparingTo(Bodies.FUNDING);
        assertThat(decimal(fact, "liquidation_penalty"))
                .isEqualByComparingTo(Bodies.LIQUIDATION_PENALTY);
        assertThat(decimal(fact, "planned_risk")).isEqualByComparingTo(Bodies.PLANNED_RISK);
        assertThat(fact.get("took_risk")).isEqualTo(Boolean.TRUE);
        assertThat(fact.get("graph_complete")).isEqualTo(Boolean.TRUE);
        assertThat(fact.get("close_outcome")).isEqualTo(Bodies.NORMAL_EXIT);
        assertThat(fact.get("reconciliation_status")).isEqualTo("MATCHED");
        assertThat(fact.get("breakdown_incomplete")).isEqualTo("COMPLETE");
        assertThat(fact.get("risk_benchmark_availability")).isEqualTo("AVAILABLE");
        // Класс раскладывается ровно в одну таблицу.
        assertThat(incidentFacts()).as("зерно происшествий не тронуто").isEmpty();

        assertThat(instant(pair(topic()), LAST_ACCEPTED_COLUMN)).isEqualTo(occurredAt.toInstant());
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);

        // СТРОК АГРЕГАТОВ НЕ ПОЯВИЛОСЬ НИ ОДНОЙ: их пишет такт пересчёта, а
        // события его не двигают. Читается это ПОВЕРХНОСТЬЮ — объявленным
        // читателем агрегатов, — и колонками рядом.
        assertThat(aggregates(DEAL_GRAIN, TENANT).dealRows()).isEmpty();
        assertThat(aggregates(INCIDENT_GRAIN, TENANT).incidentRows()).isEmpty();
        assertThat(rows.count(DEAL_AGGREGATES)).isZero();
        assertThat(rows.count(INCIDENT_AGGREGATES)).isZero();

        // Наружу не ушло ничего: в тему сервис не дописывает, своих тем не
        // заводит, а единственная чужая поверхность прогона видела только
        // обращения ЗА КЛЮЧАМИ. Непустота перечня — базовый гейт: пустой он
        // сделал бы утверждение верным на пустом месте, и обеспечивает её
        // чтение поверхности выше.
        //
        // КОНЕЦ ТЕМЫ ЧИТАЕТСЯ ДЕЛЬТОЙ СВОЕГО ОКНА, а не абсолютным числом:
        // тема штатного положения осей общая всем клеткам класса, и её
        // записи копятся за весь прогон — абсолютное «одна запись» было бы
        // утверждением о порядке методов, а не о поведении сервиса.
        assertThat(Wire.endOffset(topic())).isEqualTo(endBefore + 1);
        assertThat(Wire.topicNames()).noneMatch(name -> name.startsWith("statistics"));
        assertThat(identity.paths()).isNotEmpty();
        assertThat(identity.paths())
                .allMatch(path -> "/jwks".equals(path) || path.startsWith("/.well-known/"));
    }

    @Test
    @DisplayName("B1.2 — Ось времени сделочного факта — момент происшествия конверта")
    void theTimeAxisOfTheDealFactIsTheEnvelopeOccurrenceMoment() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = midnightDaysAgo(BUCKET_DAYS_BACK).plusHours(3);
        OffsetDateTime beforeSending = now();

        publish("E-2", DEAL_CLOSED, occurredAt, Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();

        Map<String, Object> fact = dealFact();
        assertThat(instant(fact, CLOSED_COLUMN)).isEqualTo(occurredAt.toInstant());
        // Момент приёма лежал бы между подачей и ассертом — и колонки под него
        // у строки нет вовсе: ось времени у факта ОДНА.
        assertThat(occurredAt.toInstant()).isBefore(beforeSending.toInstant());
        assertThat(rows.momentColumnNames(DEAL_FACTS)).containsExactly(CLOSED_COLUMN);

        // Строка легла в кусок гипертаблицы ПО ЭТОЙ оси.
        Map<String, Object> chunk = rows.chunkCovering(DEAL_FACTS, occurredAt);
        assertThat(chunk).as("кусок, накрывающий момент оси").isNotEmpty();
        assertThat(((OffsetDateTime) chunk.get("range_start")).toInstant())
                .isBeforeOrEqualTo(occurredAt.toInstant());
        assertThat(((OffsetDateTime) chunk.get("range_end")).toInstant())
                .isAfter(occurredAt.toInstant());
    }

    @Test
    @DisplayName("B1.3 — Тенант берётся ключом записи, а не заголовком и не содержимым")
    void theTenantComesFromTheRecordKeyRatherThanFromAHeaderOrTheContent() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = midnightDaysAgo(BUCKET_DAYS_BACK);
        Map<String, String> headers = new LinkedHashMap<>(envelope("E-3", DEAL_CLOSED, occurredAt));
        headers.put("tenantId", SECOND_TENANT);

        Wire.publish(topic(), TENANT, headers,
                Bodies.dealClosedWithTenantField(ACCOUNT, THIRD_TENANT));
        awaitConsumed();

        Map<String, Object> fact = dealFact();
        assertThat(fact.get("tenant_id")).isEqualTo(TENANT);
        // Ни значение заголовка, ни значение содержимого не попали НИКУДА.
        assertThat(fact.values().stream().map(String::valueOf))
                .noneMatch(value -> value.contains(SECOND_TENANT) || value.contains(THIRD_TENANT));

        recompute();

        assertThat(aggregates(DEAL_GRAIN, TENANT).dealRows()).hasSize(1);
        assertThat(aggregates(DEAL_GRAIN, SECOND_TENANT).dealRows()).isEmpty();
        assertThat(aggregates(DEAL_GRAIN, THIRD_TENANT).dealRows()).isEmpty();
    }

    @Test
    @DisplayName("B1.5 — Пустое определение стратегии — законное значение ключа")
    void anAbsentStrategyDefinitionIsALawfulKeyValue() {
        givenReceptionStateRows();

        publish("E-5", DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK),
                Bodies.dealClosedWithoutStrategy(ACCOUNT));
        awaitConsumed();

        assertThat(dealFact().get("strategy_internal_id")).isNull();
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);

        // Пустой компонент ключа находится ПОВТОРНЫМ прогоном: строка одна, а
        // не две (клауза `nulls not distinct`).
        recompute();
        recompute();

        assertThat(rows.count(DEAL_AGGREGATES)).isEqualTo(1L);
        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed).hasSize(1);
        assertThat(handed.getFirst().get("strategyInternalId")).isNull();
    }

    @Test
    @DisplayName("B1.6 — Несомый класс происшествия кладёт факт происшествия и только его")
    void aCarriedIncidentClassWritesTheIncidentFactAndOnlyIt() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));

        publish("E-6a", DEAL_OPENED, occurredAt, Bodies.incident(ACCOUNT));
        publish("E-6b", ORDER_DECIDED, occurredAt, Bodies.incident(ACCOUNT));
        publish("E-6c", HOLD_RAISED, occurredAt, Bodies.holdRaised(ACCOUNT, Bodies.HARD, Bodies.MANUAL_HALT_REQUESTED));
        publish("E-6d", ANOMALY_REPORTED, occurredAt,
                Bodies.anomalyReported(ACCOUNT, Bodies.CRITICAL, Bodies.MANUAL_HALT_REQUESTED));
        awaitConsumed();

        List<Map<String, Object>> facts = incidentFacts();
        assertThat(facts).hasSize(4);
        assertThat(facts.stream().map(row -> row.get("event_type")))
                .containsExactly(DEAL_OPENED, ORDER_DECIDED, HOLD_RAISED, ANOMALY_REPORTED);
        assertThat(facts).allSatisfy(row -> {
            assertThat(instant(row, OCCURRED_COLUMN)).isEqualTo(occurredAt.toInstant());
            assertThat(row.get("tenant_id")).isEqualTo(TENANT);
            assertThat(row.get("exchange_account_internal_id")).isEqualTo(ACCOUNT);
        });
        // Класс раскладывается ровно в одну таблицу.
        assertThat(dealFacts()).as("сделочного зерна эти классы не касаются").isEmpty();
        // Колонок идентичности радиуса у зерна происшествий нет ВОВСЕ.
        assertThat(rows.columnNames(INCIDENT_FACTS))
                .doesNotContain("deal_internal_id", "instrument_internal_id",
                        "strategy_internal_id");
    }

    @Test
    @DisplayName("B1.7 — Класс, признаку не отвечающий, факта не порождает, но момент двигает")
    void aClassOutsideTheCarriedSetWritesNoFactButMovesTheMoment() {
        givenReceptionStateRows();
        OffsetDateTime earlier = momentsAgo(Duration.ofHours(2));
        OffsetDateTime later = momentsAgo(Duration.ofMinutes(5));
        givenReceptionOf(topic(), earlier, Boolean.FALSE);

        publish("E-7", NOT_CARRIED, later, Bodies.incident(ACCOUNT));
        awaitConsumed();

        assertThat(dealFacts()).isEmpty();
        assertThat(incidentFacts()).isEmpty();
        assertThat(instant(pair(topic()), LAST_ACCEPTED_COLUMN)).isEqualTo(later.toInstant());
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        // Лаг убыл: зафиксированное смещение догнало конец темы.
        assertThat(Wire.committedOffset(consumerGroup(), topic())).isEqualTo(Wire.endOffset(topic()));

        // Повтор той же записи по-прежнему не оставляет следствия.
        publish("E-7", NOT_CARRIED, later, Bodies.incident(ACCOUNT));
        awaitConsumed();

        assertThat(dealFacts()).isEmpty();
        assertThat(incidentFacts()).isEmpty();
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B1.8 — Неизвестный класс события ведёт себя как ненесомый, а не как отказ")
    void anUnknownEventClassBehavesLikeANonCarriedOneRatherThanAFailure() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        Integer logMark = AppLog.mark();

        publish("E-8", "CLASS_NO_PRODUCER_DECLARES", occurredAt, Bodies.incident(ACCOUNT));
        awaitConsumed();

        assertThat(dealFacts()).isEmpty();
        assertThat(incidentFacts()).isEmpty();
        assertThat(instant(pair(topic()), LAST_ACCEPTED_COLUMN)).isEqualTo(occurredAt.toInstant());
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        assertThat(Wire.committedOffset(consumerGroup(), topic())).isEqualTo(Wire.endOffset(topic()));
        // В журнал ушло не более записи уровня отладки: отбор у потребителя
        // штатен, и жалоба превратила бы штатную ветвь в аномалию.
        assertThat(AppLog.alarmsSince(logMark)).isEmpty();
    }

    @Test
    @DisplayName("B1.9 — Повторная доставка поглощается ключом события и отказом не является")
    void aRedeliveryIsAbsorbedByTheEventKeyAndIsNotAFailure() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        publish("E-9d", DEAL_CLOSED, occurredAt, Bodies.dealClosed(ACCOUNT, STRATEGY));
        publish("E-9i", DEAL_OPENED, occurredAt, Bodies.incident(ACCOUNT));
        awaitConsumed();
        Map<String, Object> dealBefore = dealFact();
        Map<String, Object> incidentBefore = incidentFacts().getFirst();

        publish("E-9d", DEAL_CLOSED, occurredAt, Bodies.dealClosed(ACCOUNT, STRATEGY));
        publish("E-9i", DEAL_OPENED, occurredAt, Bodies.incident(ACCOUNT));
        awaitConsumed();

        // Повтор поглощён на строке КАЖДОГО зерна.
        assertThat(rows.count(DEAL_FACTS)).isEqualTo(1L);
        assertThat(rows.count(INCIDENT_FACTS)).isEqualTo(1L);
        assertThat(dealFact()).isEqualTo(dealBefore);
        assertThat(incidentFacts().getFirst()).isEqualTo(incidentBefore);
        // Обработка штатна: исключения нет, смещение продвинулось, флаг снят.
        assertThat(Wire.committedOffset(consumerGroup(), topic())).isEqualTo(Wire.endOffset(topic()));
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B1.10 — Повтор с ИНЫМ содержимым лежащую строку не переписывает")
    void aRedeliveryWithOtherContentDoesNotOverwriteTheStoredRow() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = midnightDaysAgo(BUCKET_DAYS_BACK);
        publish("E-10", DEAL_CLOSED, occurredAt, Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();
        Map<String, Object> before = dealFact();

        publish("E-10", DEAL_CLOSED, occurredAt, Bodies.dealClosedWith(ACCOUNT, "EUR", "99.5"));
        awaitConsumed();

        assertThat(rows.count(DEAL_FACTS)).isEqualTo(1L);
        assertThat(dealFact()).as("строка неизменяема целиком").isEqualTo(before);
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);

        // После пересчёта числа собраны по ПЕРВОЙ редакции.
        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed).hasSize(1);
        assertThat(handed.getFirst().get("resultCurrency")).isEqualTo(Bodies.CURRENCY);
        assertThat(aggregates(DEAL_GRAIN, TENANT).number("netResultSum"))
                .isEqualByComparingTo(Bodies.NET_RESULT);
    }

    @Test
    @DisplayName("B1.11 — Момент последнего принятого двигается только вперёд")
    void theLastAcceptedMomentMovesForwardOnly() {
        givenReceptionStateRows();
        OffsetDateTime later = momentsAgo(Duration.ofMinutes(5));
        OffsetDateTime earlier = momentsAgo(Duration.ofDays(1));
        givenReceptionOf(topic(), later, Boolean.TRUE);

        publish("E-11", DEAL_CLOSED, earlier, Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();

        assertThat(rows.count(DEAL_FACTS)).isEqualTo(1L);
        assertThat(instant(pair(topic()), LAST_ACCEPTED_COLUMN)).isEqualTo(later.toInstant());
        // Флаг снят НЕЗАВИСИМО от движения момента: у приёма это две величины.
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B1.12 — Повтор снимает флаг остановки")
    void aRedeliveryClearsTheHaltFlag() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        givenReceptionOf(topic(), null, Boolean.TRUE);

        publish("E-12", DEAL_CLOSED, occurredAt, Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();

        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);

        publish("E-12", DEAL_CLOSED, occurredAt, Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();

        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        assertThat(rows.count(DEAL_FACTS)).isEqualTo(1L);
        // Предикат непрерывности по паре перестал быть ложным по поводу
        // остановки.
        assertThat(aggregates(DEAL_GRAIN, TENANT).completeness().get("continuityClaimable"))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B1.13 — Разрезы класса пусты у классов, их не несущих")
    void theClassCutsAreEmptyForClassesThatDoNotCarryThem() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));

        publish("E-13a", DEAL_OPENED, occurredAt, Bodies.incident(ACCOUNT));
        publish("E-13b", HOLD_RAISED, occurredAt,
                Bodies.holdRaised(ACCOUNT, Bodies.HARD, Bodies.MANUAL_HALT_REQUESTED));
        awaitConsumed();

        List<Map<String, Object>> facts = incidentFacts();
        assertThat(facts).hasSize(2);
        Map<String, Object> opened = facts.getFirst();
        assertThat(opened.get("hold_rung")).isNull();
        assertThat(opened.get("anomaly_severity")).isNull();
        assertThat(opened.get("operation_code")).isNull();
        Map<String, Object> raised = facts.getLast();
        assertThat(raised.get("hold_rung")).isEqualTo(Bodies.HARD);
        assertThat(raised.get("operation_code")).isEqualTo(Bodies.MANUAL_HALT_REQUESTED);
        assertThat(raised.get("anomaly_severity"))
                .as("критичности у этого класса нет, и заглушкой она не подменена").isNull();
    }

    @Test
    @DisplayName("B1.14 — Пустой операнд содержимого остаётся пустым и нулём не гасится")
    void anAbsentContentOperandStaysEmptyAndIsNotZeroed() {
        givenReceptionStateRows();

        publish("E-14", DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK),
                Bodies.dealClosedWithoutMeasures(ACCOUNT));
        awaitConsumed();

        Map<String, Object> fact = dealFact();
        assertThat(fact.get("net_result")).isNull();
        assertThat(fact.get("fee")).isNull();
        assertThat(fact.get("funding")).isNull();
        assertThat(fact.get("liquidation_penalty")).isNull();
        // Плановый риск того же содержимого тоже не приехал — и нулём не стал.
        assertThat(fact.get("planned_risk")).isNull();
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.dealRows()).hasSize(1);
        assertThat(page.dealRows().getFirst().get("closedDeals")).isEqualTo(1);
        assertThat(page.dealRows().getFirst().get("riskBearingDeals")).isEqualTo(1);
        assertThat(page.dealRows().getFirst().get("resultUnavailableDeals")).isEqualTo(1);
        // В денежные суммы сделка не попала: сумма пустого множества — ноль.
        assertThat(page.number("resultBeforeFundingSum")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.number("netResultSum")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("B1.15 — Числовой операнд читается текстом, а не двоичным типом узла")
    void aNumericOperandIsReadAsTextRatherThanAsABinaryNodeType() {
        givenReceptionStateRows();
        OffsetDateTime midnight = midnightDaysAgo(BUCKET_DAYS_BACK);
        String zero = "0.000000000000000000";

        publish("E-15a", DEAL_CLOSED, midnight,
                Bodies.dealClosedWithMeasures(ACCOUNT, "0.1", zero, "0.1"));
        publish("E-15b", DEAL_CLOSED, midnight.plusHours(1),
                Bodies.dealClosedWithMeasures(ACCOUNT, "0.2", zero, "0.2"));
        awaitConsumed();

        // Колонки факта несут поданное ДОСЛОВНО.
        assertThat(dealFacts()).hasSize(2);
        assertThat(decimal(dealFacts().getFirst(), "net_result"))
                .isEqualByComparingTo(new BigDecimal("0.1"));
        assertThat(decimal(dealFacts().getLast(), "net_result"))
                .isEqualByComparingTo(new BigDecimal("0.2"));

        recompute();

        // Суммы сходятся с РУЧНЫМ сложением, без двоичной погрешности:
        // 0.1 + 0.2 у двоичного типа с плавающей точкой не равно 0.3.
        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.number("resultBeforeFundingSum"))
                .isEqualByComparingTo(new BigDecimal("0.3"));
        assertThat(page.number("netResultSum")).isEqualByComparingTo(new BigDecimal("0.3"));
        assertThat(page.number("feeSum")).isEqualByComparingTo(new BigDecimal("0.3"));
    }

    @Test
    @DisplayName("B1.16 — Пустой контекст трассировки законен")
    void anAbsentTraceContextIsLawful() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        Map<String, String> headers = new LinkedHashMap<>(envelope("E-16", DEAL_CLOSED, occurredAt));
        headers.remove(TRACE_CONTEXT);
        Integer logMark = AppLog.mark();

        Wire.publish(topic(), TENANT, headers, Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();

        assertThat(rows.count(DEAL_FACTS)).isEqualTo(1L);
        // Отсутствие трассировки аномалией не объявляется ничем: ни отказом,
        // ни строкой состояния, ни записью журнала.
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        assertThat(pair(topic()).get(GAP_COLUMN)).isNull();
        assertThat(AppLog.alarmsSince(logMark)).isEmpty();
        // Колонки под трассировку у факта нет вовсе.
        assertThat(rows.columnNames(DEAL_FACTS)).doesNotContain("trace_context");
        assertThat(rows.columnNames(INCIDENT_FACTS)).doesNotContain("trace_context");
    }

    @Test
    @DisplayName("B1.17 — Содержимого как доставлено факт не хранит")
    void theFactDoesNotStoreTheContentAsDelivered() {
        givenReceptionStateRows();

        publish("E-17", DEAL_CLOSED, momentsAgo(Duration.ofMinutes(5)),
                Bodies.dealClosedRichDocument(ACCOUNT));
        awaitConsumed();

        Map<String, Object> fact = dealFact();
        // Колонки, хранящей тело целиком, нет НИ В ОДНОЙ из двух таблиц — и
        // читается это ТИПОМ, а не именем.
        assertThat(rows.documentColumnNames(DEAL_FACTS)).isEmpty();
        assertThat(rows.documentColumnNames(INCIDENT_FACTS)).isEmpty();
        // Неразложенные поля не сохранены нигде.
        assertThat(fact.values().stream().map(String::valueOf))
                .noneMatch(value -> value.contains("series")
                        || value.contains("nested")
                        || value.contains("альфа"));
        // Поверхности, отдающей строку факта наружу, не существует: перечень
        // объявленных маршрутов её не несёт. Непустота перечня — базовый гейт.
        List<String> declared = declaredRoutes(get(SURFACE_DESCRIPTION, TENANT).body());
        assertThat(declared).contains(AGGREGATE_ROWS);
        assertThat(declared).noneMatch(route -> route.contains("fact"));
    }

    /**
     * Маршруты, объявленные описанием поверхности.
     *
     * <p><b>Читаются ключами объекта путей, а не поиском слова по телу.</b>
     * Слово «факт» встречается в описаниях полей законно — предмет утверждения
     * в том, что МАРШРУТА с ним нет.
     *
     * @param description тело описания поверхности дословно
     */
    private static List<String> declaredRoutes(String description) {
        Matcher routes = Pattern.compile("\"(/[A-Za-z0-9/_{}.-]*)\"\\s*:\\s*\\{")
                .matcher(description);
        List<String> collected = new ArrayList<>();
        while (routes.find()) {
            collected.add(routes.group(1));
        }
        return collected;
    }
}
