package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B5.4} и {@code B5.5} — пара, снятая с подписки, и нижняя
 * граница полноты (.claude/tests/cases/audit.md §«B5 — Полнота: нижняя
 * граница»).
 *
 * <p><b>Состав подписки — ВХОД обеих клеток</b>, поэтому контекст у них
 * свой: подписку контейнер читает при подъёме, и сузить её на живом
 * контексте нечем. Заведённых у брокера тем две, объявленных подпиской —
 * одна: ровно то состояние, в котором такт снимает признак подписки с
 * ушедшей темы, а строку её не удаляет.
 *
 * <p><b>Обе клетки стоя́т на ОДНОМ положении осей</b>, поэтому живут одним
 * классом; расходятся они тем, что лежит в журнале: у первой он пуст —
 * границу тогда держат одни моменты наблюдения, — у второй в нём есть
 * строки, и клетка утверждает, что сужение подписки их не трогает.
 *
 * <p><b>Моменты наблюдения ставятся прямой записью, и это durable-ВХОД, а
 * не подмена выхода.</b> Тик заводит строку моментом своего такта, то есть
 * «сейчас»; клеткам же нужен ПОРЯДОК двух моментов — у ушедшей темы
 * позднейший, — а его тропа не производит: обе строки заводятся одним
 * тактом и получают один момент. Форма строки объявлена домом и читается
 * наружу (docs/rules/durable-consumer-reception.md §«Строка состояния
 * приёма — таблица `reception_states`»), поэтому запись по колонкам
 * говорит о том же, о чём читает ассерт.
 *
 * <p><b>Момент ушедшей темы ПОЗЖЕ момента оставшейся — не выдумка, а
 * предмет клетки:</b> тема присоединяется к подписке группы позже, чем её
 * производитель начал производить, и ровно на этом стои́т операнд
 * наблюдения (docs/rules/durable-consumer-reception.md §«Нижняя граница»).
 * Исключи такую строку из максимума — и граница опустится, то есть журнал
 * пообещает больше, чем несёт.
 *
 * <p><b>Остановка приёма у ушедшей пары поставлена намеренно.</b> Без неё
 * утверждение «непрерывность считается только по подписанным» было бы
 * пустым: предикат остался бы истинным при любой области квантора.
 *
 * <p><b>«Строки журнала снятой темы» наблюдаются как строки журнала
 * вообще, и это свойство формы, а не послабление:</b> строка журнала темы
 * не несёт ни одной колонкой (docs/models/domain/other/AuditRecord.md
 * §Персистентность), и отличить её строку от соседской нечем — ни здесь,
 * ни читателю. Утверждение клетки при этом целое: сужение подписки не
 * удаляет из журнала ничего.
 */
class UnsubscribedPairBoundBoxTest extends AuditBox {

    /** Краткое имя клеток: из него строятся их группа и их темы. */
    private static final String SLUG = "b5-4";

    /** Насколько раньше «сейчас» наблюдается оставшаяся в подписке тема. */
    private static final Duration SUBSCRIBED_SINCE_AGO = Duration.ofHours(2);

    /** Насколько раньше «сейчас» наблюдается ушедшая тема: её момент ПОЗДНЕЙШИЙ. */
    private static final Duration DEPARTED_SINCE_AGO = Duration.ofHours(1);

    /** Насколько раньше обоих моментов наблюдения принята строка журнала. */
    private static final Duration RECORDED_AGO = Duration.ofHours(3);

    /** Возраст события, которым клетка ходит в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Окно чтения: то же, которым ящик читает объявленную полноту. */
    private static final Duration WINDOW = Duration.ofHours(1);

    /** Вставка строки пары: колонки те же, которыми её заводит тик. */
    private static final String OPEN_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, ?, ?)
            """;

    /** Запись момента наблюдения одной пары. */
    private static final String SET_OBSERVED = """
            update reception_states set observed_since = ? where consumer_group = ? and topic = ?
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                List.of(AuditSubstrate.ownCoreTopic(SLUG), AuditSubstrate.ownStrategyTopic(SLUG)),
                List.of(AuditSubstrate.ownCoreTopic(SLUG)),
                Map.of());
    }

    @Test
    @DisplayName("B5.4 — Момент наблюдения берётся по ВСЕМ строкам, включая отписанные")
    void theObservationMaximumCoversDepartedRowsAsWell() {
        givenReceptionStateRows();
        OffsetDateTime departedSince = givenPairMoments(Boolean.TRUE);

        tick();

        assertThat(rows.count(JOURNAL_TABLE)).as("вход поставлен: журнал пуст").isZero();
        assertThat(pair(departed()).get(SUBSCRIBED_COLUMN))
                .as("пара ушедшей темы снята с подписки").isEqualTo(Boolean.FALSE);
        assertThat(instant(pair(departed()), OBSERVED_COLUMN))
                .as("а момент её наблюдения — позднейший из двух")
                .isEqualTo(departedSince.toInstant())
                .isAfter(instant(pair(subscribed()), OBSERVED_COLUMN));
        assertThat(lowerBoundMoment().toInstant())
                .as("момент снятой с подписки пары из максимума НЕ исключается")
                .isEqualTo(departedSince.toInstant());
        assertThat(pair(departed()).get(HALTED_COLUMN))
                .as("вход второй половины поставлен: приём по снятой паре остановлен")
                .isEqualTo(Boolean.TRUE);
        assertThat(continuityClaimable())
                .as("непрерывность считается только по ПОДПИСАННЫМ: остановка снятой её не роняет")
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B5.5 — Снятие темы с подписки границу не опускает")
    void narrowingTheSubscriptionDoesNotLowerTheBound() {
        givenReceptionStateRows();
        publish(subscribed(), "E-JOURNALLED", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(subscribed());
        recordedEarlier("E-JOURNALLED", momentsAgo(RECORDED_AGO));
        OffsetDateTime departedSince = givenPairMoments(Boolean.FALSE);
        OffsetDateTime boundBefore = lowerBoundMoment();
        assertThat(pairs())
                .as("вход поставлен: граница измерена при ДВУХ подписанных парах")
                .hasSize(2)
                .allMatch(row -> Boolean.TRUE.equals(row.get(SUBSCRIBED_COLUMN)));
        assertThat(boundBefore.toInstant())
                .as("и равна она позднейшему моменту наблюдения, а не моменту приёма")
                .isEqualTo(departedSince.toInstant());
        Long recordsBefore = rows.count(JOURNAL_TABLE);

        tick();

        assertThat(pair(departed()).get(SUBSCRIBED_COLUMN))
                .as("сужение подписки применено").isEqualTo(Boolean.FALSE);
        assertThat(pairs()).as("строка снятой темы в базе осталась").hasSize(2);
        assertThat(lowerBoundMoment().toInstant())
                .as("граница НЕ уменьшилась: строка снятой темы из максимума не выпала")
                .isEqualTo(boundBefore.toInstant());
        assertThat(rows.count(JOURNAL_TABLE))
                .as("строки журнала сужение подписки не удаляет").isEqualTo(recordsBefore);
        assertThat(journal(TENANT, momentsAgo(WINDOW), now()).records())
                .as("и из выборки они не пропали").hasSize(1);
    }

    /**
     * Разводит моменты наблюдения двух пар: у оставшейся в подписке —
     * ранний, у ушедшей — позднейший.
     *
     * @param halted стои́т ли у ушедшей пары флаг остановки приёма
     * @return момент наблюдения ушедшей пары — позднейший из двух
     */
    private OffsetDateTime givenPairMoments(Boolean halted) {
        OffsetDateTime departedSince = momentsAgo(DEPARTED_SINCE_AGO);
        rows.write(SET_OBSERVED, momentsAgo(SUBSCRIBED_SINCE_AGO), consumerGroup(), subscribed());
        rows.write(OPEN_PAIR, consumerGroup(), departed(), departedSince, halted, now());
        return departedSince;
    }

    /** Тема, оставшаяся в объявленной подписке. */
    private String subscribed() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }

    /** Тема, которой в объявленной подписке нет: её пару такт и снимает. */
    private String departed() {
        return AuditSubstrate.ownStrategyTopic(SLUG);
    }
}
