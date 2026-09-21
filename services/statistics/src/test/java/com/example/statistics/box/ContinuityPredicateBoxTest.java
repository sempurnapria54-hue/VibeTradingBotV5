package com.example.statistics.box;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B6.1}, {@code B6.2}, {@code B6.3}, {@code B6.5} и
 * {@code B6.6} — предикат непрерывности на штатном положении осей
 * (.claude/tests/cases/statistics.md §«B6 — Полнота: предикат
 * непрерывности»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>, как у соседних групп: все
 * пять клеток берут штатные оси — включённый тик, такт, до которого прогон не
 * доживает, подписку на одну заведённую тему, допустимый возраст строки
 * состояния величиной субстрата, — и расходятся только состоянием строк пар,
 * которое каждая ставит себе сама.
 *
 * <p><b>Клетки о СВЕЖЕСТИ строки здесь нет, и это не раскладка по вкусу.</b>
 * Третий конъюнкт предиката спрашивает, что допустимый возраст берётся
 * величиной конфигурации сервиса, а не константой кода; предъявить это на
 * штатных пяти минутах нечем — они совпадают с умолчанием сервиса, и клетка
 * прошла бы на реализации, читающей константу. Клетка {@code B6.4} поэтому
 * живёт своим контекстом ({@link StaleStateRowBoxTest}) со сдвинутой осью.
 *
 * <p><b>Клетки, ПЕРЕТРЯХИВАЮЩЕЙ назначение партиций, здесь нет тоже.</b>
 * Рябь от входа и выхода чужого участника группы переживает клетку: строка
 * пары получает новый момент наблюдения тогда, когда соседняя клетка уже
 * читает границу. Ни одна клетка этого класса назначения не трогает.
 *
 * <p><b>Три конъюнкта предиката разведены разными клетками, и каждая гасит
 * ОДИН.</b> Остановка приёма, момент разрыва и устаревание строки суть
 * независимые поводы одной и той же лжи; слитые в одну клетку, они прошли бы
 * и на реализации, читающей лишь первый из них.
 *
 * <p><b>Граница полноты мерится у каждой клетки, и это не довесок.</b> Обе
 * величины едут одной выдачей, и ложный предикат не имеет права ни опустить
 * границу, ни стереть её: читателю тогда сказали бы «числам верить нельзя, и
 * с какого момента — неизвестно», хотя момент известен
 * (docs/rules/durable-consumer-reception.md §«Величины едут рядом с числами, а
 * не отдельным запросом»).
 *
 * <p><b>Состояния строк ставятся прямой записью, и это durable-ВХОД, а не
 * подмена выхода.</b> Флаг остановки ставит отказ обработки, занимающий
 * единственный поток слушателя до конца прогона; момент разрыва производится
 * только перетряхиванием назначения либо доставкой с пропуском — оба хода
 * двигают заодно и смещения; момент наблюдения тик ставит своим тактом, то
 * есть «сейчас». Форма строки при этом объявлена домом и читается наружу
 * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»), поэтому запись по колонкам говорит о том же, о
 * чём читает ассерт.
 *
 * <p><b>Вторая ветвь пустой области квантора — «строки есть, а подписанной
 * среди них нет» — держится клеткой {@code B5.3}</b>
 * ({@link CompletenessLowerBoundBoxTest}), где она поставлена снятием
 * подписки со всех строк и прочитана обеими величинами разом. Здесь берётся
 * ветвь «строк нет вовсе»: повторять уже предъявленное состояние значило бы
 * мерить одно дважды.
 */
class ContinuityPredicateBoxTest extends StatisticsBox {

    /** Краткое имя клеток: из него строятся их группа и их тема. */
    private static final String SLUG = "b6-1";

    /** Биржевой счёт — обязательный ключ сделочного зерна. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — второй компонент ключа зерна. */
    private static final String STRATEGY = "S-1";

    /** Имя предиката непрерывности в объявленной полноте выдачи. */
    private static final String CLAIM_FIELD = "continuityClaimable";

    /** Имя границы полноты в объявленной полноте выдачи. */
    private static final String BOUND_FIELD = "lowerBound";

    /**
     * Сколько суток назад лежат факты, которые проход обязан собрать.
     *
     * <p>Момент у них — ПОЛНОЧЬ: сутки сегодняшние по факту, легшему в их
     * середине, покрыты частично, и строк агрегатов по ним не появляется
     * вовсе ({@link StatisticsBox#midnightDaysAgo}).
     */
    private static final Integer BUCKET_DAYS_BACK = 1;

    /** Насколько раньше «сейчас» наблюдается пара объявленной подписки. */
    private static final Duration SUBSCRIBED_SINCE_AGO = Duration.ofHours(2);

    /** Насколько раньше «сейчас» наблюдается ушедшая тема: её момент ПОЗДНЕЙШИЙ. */
    private static final Duration DEPARTED_SINCE_AGO = Duration.ofHours(1);

    /** Тема, которой в объявленной подписке нет: её пару такт и снимает. */
    private static final String DEPARTED_TOPIC = "b6-5.departed";

    /** Запись момента наблюдения одной пары. */
    private static final String SET_PAIR_OBSERVED = """
            update reception_states set observed_since = ? where consumer_group = ? and topic = ?
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

    /** Вставка строки ушедшей пары: колонки те же, которыми её заводит тик. */
    private static final String OPEN_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, false, ?)
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B6.1 — Здоровое состояние: непрерывность утверждаема")
    void healthyStateClaimsContinuity() {
        givenReceptionStateRows();
        givenCollectedFact("E-B6-1");

        Answer page = aggregates(DEAL_GRAIN, TENANT);

        assertThat(pairs())
                .as("вход поставлен: пара подписана, флаг остановки снят, момент разрыва пуст")
                .hasSize(1)
                .allMatch(row -> Boolean.TRUE.equals(row.get(SUBSCRIBED_COLUMN)))
                .allMatch(row -> Boolean.FALSE.equals(row.get(HALTED_COLUMN)))
                .allMatch(row -> isNull(row.get(GAP_COLUMN)));
        assertThat(instant(pair(topic()), UPDATED_COLUMN))
                .as("а строка обновлена недавним тактом: третий конъюнкт тоже не гасится")
                .isAfter(momentsAgo(StatisticsSubstrate.STATE_MAX_AGE).toInstant());
        assertThat(page.completeness().get(CLAIM_FIELD))
                .as("непрерывность утверждаема").isEqualTo(Boolean.TRUE);
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("а граница не пуста: обе величины говорят об одном состоянии").isNotNull();
        assertThat(page.dealRows())
                .as("и обе приехали ТЕМ ЖЕ ответом, что и строки, а не отдельным запросом")
                .hasSize(1);
    }

    @Test
    @DisplayName("B6.2 — Остановка приёма на паре роняет предикат")
    void haltOnAPairDropsThePredicate() {
        givenReceptionStateRows();
        givenCollectedFact("E-B6-2");
        OffsetDateTime boundBefore = lowerBoundMoment();

        rows.write(HALT_PAIR, consumerGroup(), topic());

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(pair(topic()).get(HALTED_COLUMN))
                .as("вход поставлен: приём по подписанной паре остановлен")
                .isEqualTo(Boolean.TRUE);
        assertThat(pair(topic()).get(GAP_COLUMN))
                .as("а момент разрыва пуст: гасится ровно один конъюнкт").isNull();
        assertThat(page.completeness().get(CLAIM_FIELD))
                .as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
        assertThat(page.dealRows())
                .as("строки при этом отдаются: ложь означает «не утверждаема», а не «страницы нет»")
                .hasSize(1);
        assertThat(lowerBoundMoment())
                .as("и граница не сдвинута: остановка приёма её не двигает").isEqualTo(boundBefore);
    }

    @Test
    @DisplayName("B6.3 — Момент разрыва на паре роняет предикат")
    void gapMomentOnAPairDropsThePredicate() {
        givenReceptionStateRows();
        givenCollectedFact("E-B6-3");
        OffsetDateTime boundBefore = lowerBoundMoment();

        rows.write(SET_GAP, now(), consumerGroup(), topic());

        assertThat(pair(topic()).get(GAP_COLUMN))
                .as("вход поставлен: момент разрыва записан").isNotNull();
        assertThat(pair(topic()).get(HALTED_COLUMN))
                .as("а флаг остановки снят: гасится ровно один конъюнкт").isEqualTo(Boolean.FALSE);
        assertThat(continuityClaimable())
                .as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
        assertThat(lowerBoundMoment())
                .as("граница не тронута: момент разрыва её не двигает").isEqualTo(boundBefore);

        publish("E-B6-3-AFTER", DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();
        tick();

        assertThat(rows.count(DEAL_FACTS))
                .as("приём восстановлен: событие после разрыва принято и следствие легло")
                .isEqualTo(2L);
        assertThat(pair(topic()).get(GAP_COLUMN))
                .as("гасящего писателя у величины нет: момент разрыва пережил и приём, и такт")
                .isNotNull();
        assertThat(continuityClaimable())
                .as("и ложь держится после восстановления приёма: дыра есть безвозвратная потеря")
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B6.5 — Отписанная пара предиката не роняет")
    void anUnsubscribedPairDoesNotDropThePredicate() {
        givenReceptionStateRows();
        OffsetDateTime departedSince = momentsAgo(DEPARTED_SINCE_AGO);
        rows.write(SET_PAIR_OBSERVED, momentsAgo(SUBSCRIBED_SINCE_AGO), consumerGroup(), topic());
        rows.write(OPEN_PAIR, consumerGroup(), DEPARTED_TOPIC, departedSince, now());

        tick();

        assertThat(pair(DEPARTED_TOPIC).get(SUBSCRIBED_COLUMN))
                .as("вход поставлен ТРОПОЙ: такт снял с подписки тему, которой в ней нет")
                .isEqualTo(Boolean.FALSE);
        rows.write(HALT_PAIR, consumerGroup(), DEPARTED_TOPIC);
        rows.write(SET_GAP, now(), consumerGroup(), DEPARTED_TOPIC);

        assertThat(pair(DEPARTED_TOPIC).get(HALTED_COLUMN))
                .as("у снятой пары остановлен приём").isEqualTo(Boolean.TRUE);
        assertThat(pair(DEPARTED_TOPIC).get(GAP_COLUMN))
                .as("и записан момент разрыва — то есть оба конъюнкта разрыва разом").isNotNull();
        assertThat(pair(topic()).get(SUBSCRIBED_COLUMN))
                .as("подписанная пара при этом здорова").isEqualTo(Boolean.TRUE);
        assertThat(continuityClaimable())
                .as("область квантора — ПОДПИСАННЫЕ пары: при обратном фильтре предикат стал бы "
                        + "ложен навсегда")
                .isEqualTo(Boolean.TRUE);
        assertThat(pairs()).as("строка отписанной пары при этом жива").hasSize(2);
        assertThat(lowerBoundMoment().toInstant())
                .as("и её момент наблюдения по-прежнему держит границу: области двух величин "
                        + "разведены намеренно")
                .isEqualTo(departedSince.toInstant())
                .isAfter(instant(pair(topic()), OBSERVED_COLUMN));
    }

    @Test
    @DisplayName("B6.6 — Пустая область квантора даёт «не утверждаема», а не «дыры нет»")
    void anEmptyQuantifierAreaIsNotClaimableRatherThanGapFree() {
        assertThat(pairs())
                .as("вход поставлен: подписанных пар нет ни одной — строк состояния нет вовсе")
                .isEmpty();

        Answer page = aggregates(DEAL_GRAIN, TENANT);

        assertThat(page.completeness().get(CLAIM_FIELD))
                .as("свёртка «все» по пустой коллекции истинна, а читателю отдаётся ЛОЖЬ: "
                        + "«не проверяли» от «проверили, всё в порядке» не отличалось бы ничем")
                .isEqualTo(Boolean.FALSE);
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("граница на том же состоянии пуста: обе величины говорят об одном состоянии "
                        + "одно и то же")
                .isNull();
        assertThat(page.dealRows())
                .as("строки агрегатов при этом отдаются перечнем, а не отказом").isEmpty();
    }

    /**
     * Кладёт сделочный факт названного события и собирает по нему агрегат.
     *
     * @param eventId идентичность события
     */
    private void givenCollectedFact(String eventId) {
        publish(eventId, DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();
        recompute();
    }
}
