package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетка {@code B12.4} — группа своя, а не журнальная
 * (.claude/tests/cases/statistics.md §«B12 — Конфигурация и схема как вход»).
 *
 * <p><b>Класс живёт на ШТАТНОМ положении осей, и своего контекста ему не
 * нужно.</b> Вторая сторона здесь не второй процесс, а второе ИМЯ ГРУППЫ на
 * брокере: смещения групп независимы по построению протокола, и предъявить
 * это можно читателем самого кейса ({@link Wire#readAllAsGroup}). Поднимать
 * ради этого второй контекст значило бы объявить ось конфигурации, которой
 * клетка не ставит.
 *
 * <p><b>Чужая сторона названа именем группы ЖУРНАЛА, и это не произвол.</b>
 * Ровно её читает сосед, подписанный на ту же тему
 * (services/audit, {@code reception.group-id}); совпади имена, часть темы
 * осталась бы непрочитанной у одного из двух, и ни один признак об этом не
 * сказал бы (docs/models/domain/other/StatisticsFact.md §«Подписка, группа и
 * позиция чтения»).
 *
 * <p><b>Считается ВСЯ тема, а не только записи клетки.</b> Тема штатного
 * контекста общая всем классам прогона, и её начало лежит раньше этой
 * клетки; клейм «часть темы непрочитанной не остаётся ни у одной» — про
 * хвост, а не про долю, поэтому обе стороны сверяются с КОНЦОМ темы. Свои
 * записи клетка при этом называет поимённо: без них отрицание сошлось бы и
 * на пустой теме.
 *
 * <p><b>Читатель кейса смещений НЕ фиксирует</b>, и потому наблюдатель
 * ничего на брокере не оставляет: повторный прогон класса застаёт то же
 * состояние группы, что и первый.
 */
class IndependentConsumerGroupsBoxTest extends SharedStatisticsBox {

    /** Имя группы соседа, читающего ту же тему: журнал аудита. */
    private static final String JOURNAL_GROUP = "audit.journal";

    /** Возраст событий, которыми клетка ходит в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Идентичности записей, положенных этой клеткой. */
    private static final List<String> OWN = List.of("E-B12-4-A", "E-B12-4-B");

    @Test
    @DisplayName("B12.4 — Группа своя, а не журнальная")
    void theConsumerGroupIsItsOwnRatherThanTheJournalOne() {
        OWN.forEach(eventId -> publish(eventId, DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY)));
        awaitConsumed();
        givenReceptionStateRows();

        Long end = Wire.endOffset(topic());
        List<String> readByJournal = Wire.readAllAsGroup(JOURNAL_GROUP, topic());

        assertThat(consumerGroup())
                .as("имя группы статистики от журнального отличается: иначе одна из "
                        + "двух сторон получила бы часть темы непрочитанной")
                .isNotEqualTo(JOURNAL_GROUP);
        assertThat(Wire.committedOffset(consumerGroup(), topic()))
                .as("группа статистики дочитала тему до конца")
                .isEqualTo(end);
        assertThat((long) readByJournal.size())
                .as("группа журнала получила ВСЕ записи темы, а не остаток от соседней: "
                        + "смещения у групп независимы")
                .isEqualTo(end);
        assertThat(readByJournal)
                .as("вход положен: обе записи клетки видны и второй стороне")
                .containsAll(OWN);
        assertThat(dealFacts())
                .as("та же пара записей принята и стороной статистики")
                .extracting(row -> row.get("event_id"))
                .containsAll(OWN);
        assertThat(pair(topic()).get(GROUP_COLUMN))
                .as("имя группы статистики есть операнд ключа её строки состояния: "
                        + "ключ строки — пара «группа × тема»")
                .isEqualTo(consumerGroup());
    }
}
