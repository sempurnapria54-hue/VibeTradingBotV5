package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B6} документа кейсов: событие как ВЫХОД — содержимое и
 * конверт (.claude/tests/cases/strategies.md).
 *
 * <p><b>Ассерт здесь прямой по строке outbox, и это названная цена, а не
 * умолчание.</b> Поверхности чтения событий у владельца определений нет
 * вовсе: копию ядро заводит СОБЫТИЕМ, а не запросом к чужой базе
 * (docs/architecture/data-ownership.md §Раскладка), и другого наблюдателя
 * содержимого у ящика не существует.
 *
 * <p><b>Содержимое — предмет ассерта, а не подробность.</b> Недоложенное
 * поле снимка наблюдается У ЧУЖОГО СЕРВИСА: потребитель заводит по нему
 * копию и ведёт сделки при недоступном владельце. Поэтому клетки идут по
 * СОСТАВУ содержимого, а не по факту его наличия.
 *
 * <p><b>Конверт и содержимое разведены.</b> Конверт — колонки строки, и
 * он один на все события платформы; содержимое — JSONB, и его форму знает
 * только производитель класса. Клетка, спутавшая их, мерила бы второй
 * носитель формы конверта.
 */
class DefinitionEventBoxTest extends SharedStrategiesBox {

    /** Имя поля содержимого, в котором едет снимок дерева. */
    private static final String DEFINITION = "definition";

    /** Состав содержимого переходов, не везущих дерева. */
    private static final List<String> LIFECYCLE_FIELDS = List.of("strategyInternalId",
            "exchangeAccountInternalId", "instrumentInternalId", "actor");

    /**
     * Сколько отвергнутых переходов проходит клетка {@code B6.9}: полный
     * набор, названный её предусловиями — {@code B4.1}, {@code B4.2},
     * {@code B4.3}, {@code B5.2}, {@code B5.4}, {@code B5.5}, {@code B5.8}.
     *
     * <p>Число пиннуто, потому что клейм клетки — о НАБОРЕ: «ни один
     * отвергнутый переход события не пишет» верно и над частью набора, и
     * без счёта выпавший вход остался бы незаметен.
     */
    private static final Integer REJECTED_TRANSITIONS = 7;

    /**
     * Объявленный состав колонок строки outbox: конверт плюс тема,
     * содержимое, отметка публикации и ключ строки.
     */
    private static final List<String> OUTBOX_COLUMNS = List.of("id", "event_id", "tenant_id",
            "event_type", "version", "occurred_at", "trace_context", "topic", "payload",
            "published_at");

    @Test
    @DisplayName("B6.1 — Содержимое активации несёт радиус верхним уровнем и снимок рядом")
    void b6_1_theActivationContentCarriesTheRadiusOnTopAndTheSnapshotBeside() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);

        Map<String, Object> content = contentOf(event(ACTIVATED));

        assertThat(content.keySet())
                .as("четыре компонента радиуса верхним уровнем плюс снимок")
                .containsExactlyInAnyOrder("strategyInternalId", "exchangeAccountInternalId",
                        "instrumentInternalId", "actor", DEFINITION);
        assertThat(content).containsEntry("strategyInternalId", internalId);
        assertThat(String.valueOf(content.get("exchangeAccountInternalId"))).isNotBlank();
        assertThat(String.valueOf(content.get("instrumentInternalId"))).isNotBlank();
        assertThat(nested(content, DEFINITION))
                .as("внутри снимка определение зовётся internalId и по имени колонки не находится")
                .containsEntry("internalId", internalId);
    }

    /**
     * Ожидание взято из дома: числовые ключи баз границу сервиса не
     * пересекают (docs/architecture/data-ownership.md §Идентификаторы).
     * Снимок едет доменным деревом, и поле технического ключа у узла
     * остаётся — но без значения: копию без ключей строит маппер формы.
     * Перепись пустых значений не считает, поэтому клетка мерит ровно
     * значение ключа, а не имя поля (находка F-5 закрыта).
     *
     * <p><b>Сравнение состава идёт «не меньше», а не «поровну», и причина
     * названа.</b> Предмет клетки — что ни один узел дерева не опущен;
     * снимок же несёт СВЕРХ ответа поверхности колонки аудита каждого
     * узла — тридцать узлов вместо одного корня, — и это отдельная
     * находка того же механизма, что F-5 (F-7 того же документа). Строгое
     * равенство сделало бы клетку красной по двум причинам сразу, и ни
     * одну из них нельзя было бы прочитать по падению.
     */
    @Test
    @DisplayName("B6.2 — Снимок едет деревом целиком, а не ссылкой на него")
    void b6_2_theSnapshotTravelsAsTheWholeTreeNotAsAReferenceToIt() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);

        Map<String, Integer> snapshot =
                census(nested(contentOf(event(ACTIVATED)), DEFINITION));
        Map<String, Integer> given = census(get(STRATEGIES + "/" + internalId, TENANT).asObject());

        given.forEach((node, count) -> assertThat(snapshot.getOrDefault(node, 0))
                .as("узел «%s» снимком не опущен", node)
                .isGreaterThanOrEqualTo(count));
        assertThat(snapshot)
                .as("значений ключей базы владельца в снимке нет по дому ни у одного узла (F-5)")
                .doesNotContainKey("id");
    }

    @Test
    @DisplayName("B6.3 — Содержимое прочих переходов дерева не несёт")
    void b6_3_theContentOfOtherTransitionsCarriesNoTree() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);
        assertThat(moveTo(internalId, TENANT, "DELETED").status()).isEqualTo(200);

        Map<String, Object> deactivated = contentOf(event(DEACTIVATED));
        Map<String, Object> deleted = contentOf(event(DELETED));

        assertThat(deactivated.keySet()).containsExactlyInAnyOrderElementsOf(LIFECYCLE_FIELDS);
        assertThat(deleted.keySet())
                .as("у обоих классов форма одна: определение неизменяемо и уже лежит у читателя")
                .containsExactlyInAnyOrderElementsOf(LIFECYCLE_FIELDS);
        assertThat(deactivated).doesNotContainKey(DEFINITION);
        assertThat(deleted).doesNotContainKey(DEFINITION);
    }

    @Test
    @DisplayName("B6.4 — Актор едет содержимым и равен предъявленному принципалу")
    void b6_4_theActorTravelsInTheContentAndEqualsThePresentedPrincipal() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);

        Map<String, Object> activated = event(ACTIVATED);
        Map<String, Object> deactivated = event(DEACTIVATED);

        assertThat(contentOf(activated))
                .as("значение — класс актора, а не идентификатор пользователя")
                .containsEntry("actor", IdentityStub.PRINCIPAL);
        assertThat(contentOf(deactivated)).containsEntry("actor", IdentityStub.PRINCIPAL);
        assertThat(activated.keySet())
                .as("конверт поля актора не несёт: актор — предмет содержимого")
                .doesNotContain("actor");
        assertThat(deactivated.keySet()).doesNotContain("actor");
    }

    @Test
    @DisplayName("B6.5 — Конверт несёт весь объявленный состав и ничего сверх")
    void b6_5_theEnvelopeCarriesTheWholeDeclaredCompositionAndNothingMore() {
        peerResolvesEverything();
        givenActive(TENANT);

        Map<String, Object> event = event(ACTIVATED);

        assertThat(event.keySet())
                .as("полей сверх объявленного состава нет ни одного")
                .containsExactlyInAnyOrderElementsOf(OUTBOX_COLUMNS);
        assertThat(String.valueOf(event.get("event_id"))).isNotBlank();
        assertThat(event).containsEntry("tenant_id", TENANT);
        assertThat(event).containsEntry("event_type", ACTIVATED);
        assertThat(event.get("version")).isNotNull();
        assertThat(((OffsetDateTime) event.get("occurred_at")).getOffset())
                .as("шкала одна — UTC")
                .isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("B6.6 — Контекст трассировки пуст, и пустота означает отсутствие")
    void b6_6_theTraceContextIsAbsentAndItsAbsenceMeansThereWasNone() {
        peerResolvesEverything();
        givenActive(TENANT);
        Wire.Mark mark = Wire.mark();

        assertThat(event(ACTIVATED).get("trace_context"))
                .as("значение ОТСУТСТВУЕТ, а не равно пустой строке")
                .isNull();

        assertThat(tickRelay().status()).isEqualTo(202);
        awaitPublished(1);

        List<Wire.Published> published = Wire.publishedSince(mark);
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().headers())
                .as("пустой заголовок не ставится вовсе: его отсутствие и есть «трассировки не было»")
                .doesNotContainKey("traceContext");
    }

    @Test
    @DisplayName("B6.7 — Тема выводится производителем, а не приходит параметром")
    void b6_7_theTopicIsDerivedByTheProducerNotPassedIn() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);
        assertThat(moveTo(internalId, TENANT, "DELETED").status()).isEqualTo(200);

        List<Object> topics = events().stream().map(row -> row.get("topic")).toList();

        assertThat(topics).hasSize(3);
        assertThat(topics)
                .as("тема одна на производителя и род: смешение родов теряло бы факты компакцией")
                .containsOnly(StrategiesSubstrate.FACTS_TOPIC);
    }

    @Test
    @DisplayName("B6.8 — Идентичности событий различны, а идентичность определения общая")
    void b6_8_theEventIdentitiesDifferWhileTheDefinitionIdentityIsShared() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);
        assertThat(moveTo(internalId, TENANT, "DELETED").status()).isEqualTo(200);

        List<Map<String, Object>> written = events();

        assertThat(written.stream().map(row -> row.get("event_id")).toList())
                .as("потребитель дедуплицирует по идентичности события")
                .hasSize(3)
                .doesNotHaveDuplicates();
        assertThat(written.stream().map(row -> contentOf(row).get("strategyInternalId")).toList())
                .as("предмет у трёх событий один")
                .containsOnly(internalId);
        assertThat(written.stream().map(row -> row.get("event_type")).toList())
                .as("классы соответствуют целевым статусам и идут в порядке переходов")
                .containsExactly(ACTIVATED, DEACTIVATED, DELETED);
    }

    @Test
    @DisplayName("B6.9 — Отвергнутый переход события не пишет")
    void b6_9_aRejectedTransitionWritesNoEvent() {
        peerResolvesEverything();
        // Статусы предусловий ставятся ПРЯМОЙ записью, а не тропой ящика,
        // и основание у этого — сам предмет клетки: состоявшийся переход
        // пишет свою строку, и поставленное тропой предусловие отняло бы
        // у ожидания «в outbox ни одной строки» его смысл.
        String draft = given(TENANT);
        String deleted = given(TENANT);
        String active = given(TENANT, Bodies.onInstrument("i2-usdt-swap"));
        String activeOnThird = given(TENANT, Bodies.onInstrument("i3-usdt-swap"));
        String rival = given(TENANT, Bodies.onInstrument("i3-usdt-swap"));
        String unresolvable = given(TENANT, Bodies.onInstrument("i4-usdt-swap"));
        String withoutNumbers = given(TENANT, Bodies.onInstrument("i5-usdt-swap"));
        setStatus(deleted, "DELETED");
        setStatus(active, "ACTIVE");
        setStatus(activeOnThird, "ACTIVE");

        List<Answer> rejected = new ArrayList<>(List.of(
                moveTo(draft, TENANT, "INACTIVE"),
                moveTo(deleted, TENANT, "ACTIVE"),
                moveTo(active, TENANT, "CREATED"),
                moveTo(rival, TENANT, "ACTIVE")));
        peer.answers(PEER_PAIR_CHECKS, Feed.pairCheck(Boolean.TRUE, Boolean.FALSE, Boolean.TRUE));
        rejected.add(moveTo(unresolvable, TENANT, "ACTIVE"));
        peerResolvesEverything();
        peer.answers(PEER_RISK_APPETITES + "/" + TENANT,
                Feed.riskAppetite(TENANT, GLOBAL_SIMULTANEOUS_PERCENT, null));
        rejected.add(moveTo(withoutNumbers, TENANT, "ACTIVE"));
        peerResolvesEverything();
        peer.answers(PEER_PAIR_CHECKS, 503, "{}");
        rejected.add(moveTo(draft, TENANT, "ACTIVE"));

        assertThat(rejected.stream().map(Answer::status).toList())
                .as("отвергнут ВЕСЬ набор, а не часть его")
                .hasSize(REJECTED_TRANSITIONS)
                .allSatisfy(status -> assertThat(status).isNotEqualTo(200));
        assertThat(rows.count(OUTBOX_TABLE))
                .as("половины «статус без события» не существует ни в одном прогоне")
                .isZero();
        assertThat(statusOf(draft, TENANT)).isEqualTo("CREATED");
        assertThat(statusOf(deleted, TENANT)).isEqualTo("DELETED");
        assertThat(statusOf(active, TENANT)).isEqualTo("ACTIVE");
        assertThat(statusOf(rival, TENANT)).isEqualTo("CREATED");
        assertThat(statusOf(unresolvable, TENANT)).isEqualTo("CREATED");
        assertThat(statusOf(withoutNumbers, TENANT)).isEqualTo("CREATED");
    }

    /** Прямая постановка статуса: предусловие мимо приложения. */
    private void setStatus(String internalId, String status) {
        rows.write("update strategies set status = ? where internal_id = ?", status, internalId);
    }

    /** Вложенный объект содержимого по имени поля. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> content, String field) {
        return (Map<String, Object>) content.get(field);
    }

    /**
     * Перепись узлов документа: сколько раз встречается каждое имя поля
     * на всей глубине.
     *
     * <p><b>Перепись, а не сравнение документов целиком:</b> снимок и
     * ответ поверхности — разные слои, и совпадать дословно они не
     * обязаны; совпадать обязан СОСТАВ дерева, то есть то, что ни один
     * его узел не опущен.
     */
    private static Map<String, Integer> census(Object node) {
        Map<String, Integer> counted = new LinkedHashMap<>();
        collect(node, counted);
        return counted;
    }

    private static void collect(Object node, Map<String, Integer> counted) {
        if (node instanceof Map<?, ?> object) {
            object.forEach((name, value) -> {
                if (Objects.nonNull(value)) {
                    counted.merge(String.valueOf(name), 1, Integer::sum);
                }
                collect(value, counted);
            });
            return;
        }
        if (node instanceof List<?> items) {
            items.forEach(item -> collect(item, counted));
        }
    }
}
