package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B6.4} — устаревшая строка состояния роняет предикат:
 * измеритель молчит
 * (.claude/tests/cases/statistics.md §«B6 — Полнота: предикат
 * непрерывности»).
 *
 * <p><b>Допустимый возраст строки СДВИНУТ, и это вход клетки, а не подкрутка
 * ради скорости.</b> Ожиданий у неё три, и два из них на штатной оси не
 * выразимы вовсе. Штатные пять минут совпадают с умолчанием сервиса, поэтому
 * на них клетка прошла бы и на реализации, читающей константу кода, — а
 * ожидание кейса говорит ровно обратное: порог берётся ВЕЛИЧИНОЙ
 * КОНФИГУРАЦИИ (docs/spec/durable-reception.json,
 * {@code receptionStateMaxAgeMs}). На сдвинутой оси строка возрастом в
 * пятнадцать секунд обязана быть устаревшей; при чтении константы она
 * осталась бы свежей, и предикат остался бы истинным.
 *
 * <p><b>Второе ожидание — порог мерится от МОМЕНТА ВЫДАЧИ</b>, и оно
 * предъявляется истечением свежести без единой записи: строка, свежая при
 * одном чтении, обязана стать устаревшей при следующем, хотя её не трогал
 * никто. На пяти минутах то же самое стоило бы прогону пяти минут.
 *
 * <p><b>Границу возраста читают с ДВУХ сторон, и это не избыточность.</b>
 * Свежесть объявлена как «возраст МЕНЬШЕ допустимого»
 * (docs/spec/durable-reception.json, {@code receptionStateFresh}), поэтому
 * одного устаревшего момента мало: реализация, отвечающая «не утверждаема»
 * всегда, прошла бы половину клетки. Моменты берутся с запасом в пять секунд
 * по обе стороны порога — у́же был бы гонкой с задержкой самого чтения.
 *
 * <p><b>Возраст ставится ПРЯМОЙ ЗАПИСЬЮ, и это durable-вход, а не подмена
 * выхода.</b> Единственный писатель момента обновления — тик, и ставит он
 * момент своего такта, то есть «сейчас»: состарить строку тропой ящика
 * нечем. Часы процесса при этом не двигаются ни в одной клетке
 * (.claude/tests/cases/statistics.md §«Чем достаются выходы»), а форма
 * строки объявлена домом и читается наружу
 * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»).
 *
 * <p><b>Прочие два конъюнкта предиката гасятся снятыми:</b> флаг остановки
 * снят, момент разрыва пуст — иначе ложь приходила бы по поводу, которого
 * клетка не ставила.
 */
class StaleStateRowBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её тема. */
    private static final String SLUG = "b6-4";

    /**
     * Допустимый возраст строки состояния у этого контекста.
     *
     * <p>Он и есть сдвинутая ось: величина заметно меньше умолчания сервиса,
     * и потому строка, устаревшая по ней, при чтении константы осталась бы
     * свежей.
     */
    private static final Duration STATE_MAX_AGE = Duration.ofSeconds(10);

    /** Запас по обе стороны порога: у́же он был бы гонкой с задержкой чтения. */
    private static final Duration MARGIN = Duration.ofSeconds(5);

    /** Запись момента обновления строки пары. */
    private static final String SET_UPDATED = """
            update reception_states set updated_at = ? where consumer_group = ? and topic = ?
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG,
                Map.of(StatisticsSubstrate.STATE_MAX_AGE_KEY, STATE_MAX_AGE.toSeconds() + "s"));
    }

    @Test
    @DisplayName("B6.4 — Устаревшая строка состояния роняет предикат: измеритель молчит")
    void aStaleStateRowDropsThePredicate() {
        givenReceptionStateRows();
        OffsetDateTime boundBefore = lowerBoundMoment();

        assertThat(pair(topic()).get(HALTED_COLUMN))
                .as("вход поставлен: флаг остановки снят").isEqualTo(Boolean.FALSE);
        assertThat(pair(topic()).get(GAP_COLUMN))
                .as("и момента разрыва нет: гасится ровно третий конъюнкт").isNull();
        assertThat(continuityClaimable())
                .as("строка только что обновлена тактом — непрерывность утверждаема")
                .isEqualTo(Boolean.TRUE);

        updatedAt(momentsAgo(STATE_MAX_AGE.plus(MARGIN)));

        assertThat(continuityClaimable())
                .as("строка старше допустимого возраста предикат роняет; при чтении константы "
                        + "кода она осталась бы свежей — порог взят ВЕЛИЧИНОЙ КОНФИГУРАЦИИ")
                .isEqualTo(Boolean.FALSE);
        assertThat(lowerBoundMoment())
                .as("граница на этом не меняется: устаревание строки её не двигает")
                .isEqualTo(boundBefore);

        updatedAt(momentsAgo(STATE_MAX_AGE.minus(MARGIN)));

        assertThat(continuityClaimable())
                .as("а моложе его — оставляет утверждаемой: граница читается с двух сторон")
                .isEqualTo(Boolean.TRUE);
        OffsetDateTime frozen = (OffsetDateTime) pair(topic()).get(UPDATED_COLUMN);

        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Boolean.FALSE, continuityClaimable()));

        assertThat(pair(topic()).get(UPDATED_COLUMN))
                .as("момент обновления не двинулся: свежесть истекла сама")
                .isEqualTo(frozen);
        assertThat(lowerBoundMoment())
                .as("и граница та же: порог мерится от МОМЕНТА ВЫДАЧИ, а не от записи")
                .isEqualTo(boundBefore);
    }

    /**
     * Ставит момент обновления строки своей пары.
     *
     * @param moment момент обновления, который строка получает
     */
    private void updatedAt(OffsetDateTime moment) {
        rows.write(SET_UPDATED, moment, consumerGroup(), topic());
    }
}
