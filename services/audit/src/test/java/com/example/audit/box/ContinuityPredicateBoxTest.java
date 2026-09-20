package com.example.audit.box;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетки {@code B6.1}, {@code B6.2}, {@code B6.3}, {@code B6.4},
 * {@code B6.5}, {@code B6.6} и {@code B6.7} — предикат непрерывности на
 * штатном положении осей
 * (.claude/tests/cases/audit.md §«B6 — Полнота: предикат непрерывности»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>, как у соседней группы: все
 * семь клеток берут штатные оси у {@link SharedAuditBox} — подписку на обе
 * темы, включённый тик, допустимый возраст строки состояния величиной
 * субстрата, — и расходятся только состоянием строк пар, которое каждая
 * ставит себе сама.
 *
 * <p><b>Допустимый возраст предъявлен ГРАНИЦЕЙ, и обе её стороны — вход
 * одной клетки.</b> Свежесть объявлена как «возраст МЕНЬШЕ допустимого»
 * (docs/spec/durable-reception.json, {@code receptionStateFresh}), поэтому
 * утверждение о ней проверяется парой моментов вокруг границы, а не одним:
 * строка старше её на секунду и моложе на секунду обязаны дать разные
 * ответы. Ось конфигурации при этом никуда не сдвигается — двигается
 * ВОЗРАСТ строки ({@link AuditBox#pairUpdatedAt}), потому что тропа его не
 * производит: единственный писатель момента обновления — тик, а он ставит
 * момент своего такта, то есть «сейчас».
 *
 * <p><b>Три конъюнкта предиката разведены тремя клетками, и каждая гасит
 * ОДИН.</b> Остановка приёма, момент разрыва и устаревание строки суть
 * независимые поводы одной и той же лжи; слитые в одну клетку, они прошли
 * бы и на реализации, читающей лишь первый из них.
 *
 * <p><b>Граница полноты мерится у КАЖДОЙ из них, и это не довесок.</b> Обе
 * величины едут одной выдачей, и ложный предикат не имеет права ни
 * опустить границу, ни стереть её: читателю тогда сказали бы «журнал не
 * полон, и с какого момента — неизвестно», хотя момент известен
 * (docs/rules/durable-consumer-reception.md §«Величины едут рядом с
 * числами, а не отдельным запросом»).
 *
 * <p><b>Области у двух свёрток РАЗНЫЕ намеренно</b>, и на том стоя́т две
 * клетки: непрерывность считается только по подписанным парам — иначе
 * неопрашиваемая строка делала бы предикат ложным навсегда, — а максимум
 * моментов наблюдения по всем, включая снятые
 * (docs/spec/durable-reception.json, {@code pairsAllContinuous} против
 * {@code pairsObservedSince}).
 *
 * <p><b>Состояние «строки есть, а подписанной среди них нет» ставится
 * прямой записью, и тропы у него не существует по построению.</b> Живой
 * такт приводит состав к объявленной подписке и тем самым оставляет
 * подписанной хотя бы одну пару; состояние из второй ветви клетки
 * {@code B6.6} достижимо только там, где состав менялся без такта. Сам дом
 * при этом ветвь называет и различает — «строки есть, но все сняты с
 * подписки» — и предмет клетки в том, что с таким состоянием делает
 * СВЁРТКА, а не в том, кто его завёл.
 */
class ContinuityPredicateBoxTest extends SharedAuditBox {

    /** Тема первого производителя: в неё ходят клетки класса. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Тема второго производителя: ею предъявляется попарность предиката. */
    private static final String STRATEGY = AuditSubstrate.STRATEGY_TOPIC;

    /** Имя предиката непрерывности в объявленной полноте выдачи. */
    private static final String CLAIM_FIELD = "continuityClaimable";

    /** Имя величины границы в объявленной полноте выдачи. */
    private static final String BOUND_FIELD = "lowerBound";

    /** Имя идентичности события в строке выдачи. */
    private static final String EVENT_ID_FIELD = "eventId";

    /** Возраст события, с которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Окно чтения: то же, которым ящик читает объявленную полноту. */
    private static final Duration WINDOW = Duration.ofHours(1);

    /** Насколько раньше «сейчас» наблюдают свои темы пары клеток. */
    private static final Duration OBSERVED_AGO = Duration.ofHours(2);

    /** Тема, которой в объявленной подписке нет: её пару такт и снимает. */
    private static final String DEPARTED_TOPIC = "b6-5.departed";

    /** Вставка строки пары: колонки те же, которыми её заводит тик. */
    private static final String OPEN_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, ?, ?)
            """;

    /** Запись флага остановки приёма одной пары. */
    private static final String HALT_PAIR = """
            update reception_states set reception_halted = true
             where consumer_group = ? and topic = ?
            """;

    /** Запись момента разрыва одной пары. */
    private static final String SET_GAP = """
            update reception_states set lag_gap_at = ? where consumer_group = ? and topic = ?
            """;

    /** Запись момента наблюдения по всем строкам группы. */
    private static final String SET_OBSERVED = """
            update reception_states set observed_since = ? where consumer_group = ?
            """;

    /** Снятие признака подписки со всех строк группы. */
    private static final String UNSUBSCRIBE_ALL = """
            update reception_states set subscribed = false where consumer_group = ?
            """;

    @Test
    @DisplayName("B6.1 — Здоровое состояние: непрерывность утверждаема")
    void healthyStateClaimsContinuity() {
        givenReceptionStateRows();
        publish(CORE, "E-B6-1", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);

        Answer page = page();

        assertThat(pairs())
                .as("вход поставлен: обе пары подписаны, флаг снят, момент разрыва пуст")
                .hasSize(2)
                .allMatch(row -> Boolean.TRUE.equals(row.get(SUBSCRIBED_COLUMN)))
                .allMatch(row -> Boolean.FALSE.equals(row.get(HALTED_COLUMN)))
                .allMatch(row -> isNull(row.get(GAP_COLUMN)));
        assertThat(page.completeness().get(CLAIM_FIELD))
                .as("непрерывность утверждаема").isEqualTo(Boolean.TRUE);
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("а граница не пуста: обе величины говорят об одном состоянии").isNotNull();
        assertThat(page.records())
                .as("и обе приехали ТЕМ ЖЕ ответом, что и строки, а не отдельной точкой чтения")
                .hasSize(1);
        assertThat(page.records().getFirst().get(EVENT_ID_FIELD)).isEqualTo("E-B6-1");
    }

    @Test
    @DisplayName("B6.2 — Остановка приёма на одной паре роняет предикат")
    void haltOnOnePairDropsThePredicate() {
        givenReceptionStateRows();
        publish(CORE, "E-B6-2", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);
        OffsetDateTime boundBefore = lowerBoundMoment();

        rows.write(HALT_PAIR, consumerGroup(), CORE);

        Answer page = page();
        assertThat(pair(CORE).get(HALTED_COLUMN))
                .as("вход поставлен: приём остановлен у ОДНОЙ из двух пар").isEqualTo(Boolean.TRUE);
        assertThat(pair(STRATEGY).get(HALTED_COLUMN))
                .as("вторая пара здорова: свёртка «все» роняется одной").isEqualTo(Boolean.FALSE);
        assertThat(page.completeness().get(CLAIM_FIELD))
                .as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("граница при этом НЕ пуста: ложный предикат её не стирает").isNotNull();
        assertThat(lowerBoundMoment())
                .as("и не сдвинута: остановка приёма границу не двигает").isEqualTo(boundBefore);
        assertThat(page.records()).as("строки отдаются как есть").hasSize(1);
    }

    @Test
    @DisplayName("B6.3 — Момент разрыва на одной паре роняет предикат")
    void gapMomentOnOnePairDropsThePredicate() {
        givenReceptionStateRows();
        publish(CORE, "E-B6-3", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);
        OffsetDateTime boundBefore = lowerBoundMoment();

        rows.write(SET_GAP, now(), consumerGroup(), CORE);

        assertThat(pair(CORE).get(GAP_COLUMN))
                .as("вход поставлен: момент разрыва непуст у ОДНОЙ из двух пар").isNotNull();
        assertThat(pair(CORE).get(HALTED_COLUMN))
                .as("а флаг остановки снят: конъюнкт гасится ровно один").isEqualTo(Boolean.FALSE);
        assertThat(pair(STRATEGY).get(GAP_COLUMN))
                .as("у второй пары разрыва нет").isNull();
        assertThat(continuityClaimable())
                .as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
        assertThat(lowerBoundMoment())
                .as("граница не тронута: момент разрыва её не двигает").isEqualTo(boundBefore);
    }

    @Test
    @DisplayName("B6.4 — Устаревшая строка состояния роняет предикат: измеритель молчит")
    void staleStateRowDropsThePredicate() {
        givenReceptionStateRows();
        OffsetDateTime boundBefore = lowerBoundMoment();

        pairUpdatedAt(CORE, staleMoment());

        assertThat(pair(CORE).get(HALTED_COLUMN))
                .as("вход поставлен: флаг остановки снят").isEqualTo(Boolean.FALSE);
        assertThat(pair(CORE).get(GAP_COLUMN)).as("и разрыва нет").isNull();
        assertThat(continuityClaimable())
                .as("строка старше допустимого возраста на секунду предикат роняет")
                .isEqualTo(Boolean.FALSE);
        assertThat(lowerBoundMoment())
                .as("граница на этом не меняется").isEqualTo(boundBefore);

        pairUpdatedAt(CORE, freshMoment());

        assertThat(continuityClaimable())
                .as("а моложе его на секунду — оставляет утверждаемой: граница читается с двух сторон")
                .isEqualTo(Boolean.TRUE);
        assertThat(lowerBoundMoment())
                .as("и на обратном ходе граница та же").isEqualTo(boundBefore);
    }

    @Test
    @DisplayName("B6.5 — Отписанная пара предиката не роняет")
    void unsubscribedPairDoesNotDropThePredicate() {
        givenReceptionStateRows();
        rows.write(OPEN_PAIR, consumerGroup(), DEPARTED_TOPIC, momentsAgo(OBSERVED_AGO),
                Boolean.FALSE, now());

        tick();

        assertThat(pair(DEPARTED_TOPIC).get(SUBSCRIBED_COLUMN))
                .as("вход поставлен ТРОПОЙ: такт снял с подписки тему, которой в ней нет")
                .isEqualTo(Boolean.FALSE);
        rows.write(SET_GAP, now(), consumerGroup(), DEPARTED_TOPIC);
        rows.write(HALT_PAIR, consumerGroup(), DEPARTED_TOPIC);
        pairUpdatedAt(DEPARTED_TOPIC, staleMoment());

        assertThat(pair(DEPARTED_TOPIC).get(GAP_COLUMN))
                .as("снятая пара несёт непустой момент разрыва").isNotNull();
        assertThat(pair(DEPARTED_TOPIC).get(HALTED_COLUMN))
                .as("остановленный приём").isEqualTo(Boolean.TRUE);
        assertThat(instant(pair(DEPARTED_TOPIC), UPDATED_COLUMN))
                .as("и устаревший момент обновления — то есть все три конъюнкта разом")
                .isBefore(momentsAgo(AuditSubstrate.STATE_MAX_AGE).toInstant());
        assertThat(pairs())
                .as("обе подписанные пары при этом здоровы")
                .filteredOn(row -> Boolean.TRUE.equals(row.get(SUBSCRIBED_COLUMN)))
                .hasSize(2);
        assertThat(continuityClaimable())
                .as("свёртка идёт только по ПОДПИСАННЫМ: при обратном фильтре предикат "
                        + "стал бы ложен навсегда")
                .isEqualTo(Boolean.TRUE);
        assertThat(pairs()).as("строка снятой пары при этом цела").hasSize(3);
    }

    @Test
    @DisplayName("B6.6 — Пустая область квантора даёт «не утверждаема», а не «дыры нет»")
    void emptyQuantifierAreaIsNotClaimableRatherThanGapFree() {
        assertThat(pairs()).as("вход первой ветви: строк состояния нет вовсе").isEmpty();
        assertThat(continuityClaimable())
                .as("свёртка «все» по пустой коллекции истинна, а ответ читателю — ЛОЖЬ")
                .isEqualTo(Boolean.FALSE);
        assertThat(lowerBound())
                .as("и граница пуста: обе величины говорят об одном состоянии одно и то же")
                .isNull();

        givenReceptionStateRows();
        rows.write(UNSUBSCRIBE_ALL, consumerGroup());

        assertThat(pairs())
                .as("вход второй ветви: строки есть, но подписанной среди них нет ни одной")
                .hasSize(2)
                .allMatch(row -> Boolean.FALSE.equals(row.get(SUBSCRIBED_COLUMN)))
                .allMatch(row -> nonNull(row.get(OBSERVED_COLUMN)));
        assertThat(continuityClaimable())
                .as("ответ тот же: область квантора пуста, а не здорова").isEqualTo(Boolean.FALSE);
        assertThat(lowerBound())
                .as("граница пуста, хотя максимум моментов наблюдения по строкам есть: "
                        + "пустая ветвь отмеряется ПОДПИСАННЫМИ парами")
                .isNull();
    }

    @Test
    @DisplayName("B6.7 — Величины едут свои, а не соседские")
    void valuesAreItsOwnRatherThanANeighboursOnes() {
        givenReceptionStateRows();
        OffsetDateTime ourObserved = momentsAgo(OBSERVED_AGO);
        rows.write(SET_OBSERVED, ourObserved, consumerGroup());
        assertThat(strangerRows())
                .as("вход поставлен: строк чужой группы в базе нет ни одной").isZero();
        OffsetDateTime boundBefore = lowerBoundMoment();
        assertThat(boundBefore.toInstant())
                .as("граница выведена из строк СВОЕЙ группы").isEqualTo(ourObserved.toInstant());

        rows.write(OPEN_PAIR, strangerGroup(), CORE, now(), Boolean.TRUE, now());

        assertThat(strangerRows()).as("строка чужой группы заведена").isEqualTo(1L);
        assertThat(continuityClaimable())
                .as("остановленный приём ЧУЖОЙ группы предиката не роняет")
                .isEqualTo(Boolean.TRUE);
        assertThat(lowerBoundMoment())
                .as("а позднейший момент наблюдения чужой группы границы не поднимает")
                .isEqualTo(boundBefore);
    }

    /** Страница журнала тенанта за окно, которым ящик читает полноту. */
    private Answer page() {
        return journal(TENANT, momentsAgo(WINDOW), now());
    }

    /** Имя группы, которой в конфигурации этого сервиса нет. */
    private String strangerGroup() {
        return consumerGroup() + ".stranger";
    }

    /** Сколько строк состояния несут чужое имя группы. */
    private Long strangerRows() {
        return pairs().stream()
                .filter(row -> Objects.equals(String.valueOf(row.get(GROUP_COLUMN)), strangerGroup()))
                .count();
    }
}
