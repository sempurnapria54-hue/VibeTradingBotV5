package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B6.8} — предикат живёт записью, а не метрикой
 * (.claude/tests/cases/audit.md §«B6 — Полнота: предикат непрерывности»).
 *
 * <p><b>Выключатель тика — ВХОД клетки</b>, поэтому контекст у неё свой:
 * его читает сам тик при каждом такте, а положение оси приезжает при
 * подъёме. Им и уносятся ряды экспорта: единственный их писатель — тик, и
 * снятый выключатель не оставляет ни одного
 * (docs/components/ReceptionStateJob.md §«Ряды экспорта пишет тот же тик»).
 *
 * <p><b>Предмет здесь ДРУГОЙ, чем у соседней клетки того же положения
 * оси.</b> {@code B3.7} спрашивает, что при снятом выключателе не
 * заводится ни строки, ни ряда; эта — что обе величины полноты считаются
 * по КОЛОНКАМ строк, и отсутствие рядов на них не влияет ни в одну
 * сторону. Носитель операндов объявлен durable-строкой именно поэтому:
 * метрика ротируется, и сослаться при разборе можно только на запись
 * (docs/rules/durable-consumer-reception.md §«Предикат непрерывности»).
 *
 * <p><b>Строки пар ставятся прямой записью, и это durable-ВХОД, а не
 * подмена выхода.</b> Завести их тиком ЭТОГО контекста невозможно по
 * построению — выключатель снят, а он и есть их единственный писатель, —
 * и ровно в этом состоит предусловие клетки: строки целы и свежи, а рядов
 * нет. Форма строки при этом объявлена домом и читается наружу
 * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»), поэтому запись по колонкам говорит о том
 * же, о чём читает ассерт.
 *
 * <p><b>Вторая половина клетки — та же выдача СПУСТЯ допустимый
 * возраст.</b> Она предъявляет, что ложь приходит по колонке: рядов не
 * было ни до, ни после, а ответ переменился — значит операнд читался из
 * строки.
 */
class PredicateWithoutSeriesBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b6-8";

    /** Насколько раньше «сейчас» наблюдаются темы клетки. */
    private static final Duration OBSERVED_AGO = Duration.ofHours(2);

    /** Окно чтения: то же, которым ящик читает объявленную полноту. */
    private static final Duration WINDOW = Duration.ofHours(1);

    /** Вставка строки пары: колонки те же, которыми её заводит тик. */
    private static final String OPEN_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, false, ?)
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                Map.of(AuditSubstrate.STATE_TICK_ENABLED_KEY, "false"));
    }

    @Test
    @DisplayName("B6.8 — Предикат живёт записью, а не метрикой")
    void thePredicateLivesInTheRowRatherThanInTheSeries() {
        tick();
        assertThat(rows.count(RECEPTION_TABLE))
                .as("вход поставлен: при снятом выключателе строк не заводит никто").isZero();
        assertThat(receptionRowCount())
                .as("и рядов экспорта нет ни одного: их единственный писатель — тик").isZero();

        OffsetDateTime observedSince = momentsAgo(OBSERVED_AGO);
        for (String topic : subscription()) {
            rows.write(OPEN_PAIR, consumerGroup(), topic, observedSince, now());
        }

        assertThat(pairs())
                .as("строки состояния целы и свежи по моменту обновления").hasSize(2);
        assertThat(receptionRowCount()).as("а рядов по-прежнему нет").isZero();
        assertThat(continuityClaimable())
                .as("непрерывность утверждаема: обе величины посчитаны по КОЛОНКАМ, а не по рядам")
                .isEqualTo(Boolean.TRUE);
        assertThat(lowerBoundMoment().toInstant())
                .as("и граница выведена из колонки момента наблюдения")
                .isEqualTo(observedSince.toInstant());

        for (String topic : subscription()) {
            pairUpdatedAt(topic, staleMoment());
        }

        assertThat(receptionRowCount())
                .as("рядов не было ни до, ни после: ответ переменился не из-за них").isZero();
        assertThat(continuityClaimable())
                .as("спустя допустимый возраст предикат ложен — уже по колонке, а не по метрике")
                .isEqualTo(Boolean.FALSE);
        assertThat(lowerBoundMoment().toInstant())
                .as("граница при этом цела: устаревание строки её не двигает")
                .isEqualTo(observedSince.toInstant());
        assertThat(journal(TENANT, momentsAgo(WINDOW), now()).completeness())
                .as("обе величины по-прежнему едут одной выдачей")
                .containsKeys("lowerBound", "continuityClaimable");
    }
}
