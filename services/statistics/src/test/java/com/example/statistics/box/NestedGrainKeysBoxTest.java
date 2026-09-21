package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B1.4} документа кейсов: ключи зерна берутся одноимёнными
 * компонентами ВЕРХНЕГО уровня содержимого
 * (.claude/tests/cases/statistics.md §«B1.4 — Ключи зерна берутся
 * одноимёнными компонентами верхнего уровня содержимого»).
 *
 * <p><b>Класс равен КЛЕТКЕ, и это не отступление от правила «класс на
 * группу», а его применение.</b> Вторая запись клетки неполна по построению:
 * биржевой счёт лежит у неё на глубине, а обязательный ключ зерна потому пуст
 * — то есть клетка ЛОМАЕТ приём. Отравленное сообщение повторяется без
 * ограничения числа попыток ({@code ReceptionErrorHandler}) и занимает
 * единственный поток слушателя: контекст, в котором оно легло, не принимает
 * больше ничего. Соседние клетки группы в этом контексте наблюдали бы исход
 * этой, и их отрицания сошлись бы по ЛОЖНОЙ причине — сообщение до обработки
 * просто не дошло бы.
 *
 * <p><b>Пара «группа + тема» у класса своя</b>
 * ({@link StatisticsSubstrate#registerOwn}): своя группа читает тему с начала,
 * поэтому без своей темы одиночка переиграла бы всё, что положили соседние
 * классы прогона.
 *
 * <p><b>Контекст закрывается вместе с классом</b> ({@link DirtiesContext}), и
 * довод механический: контексты каркас теста кэширует и не закрывает, а
 * застрявший потребитель продолжает биться в брокер и в базу каждую паузу
 * повтора до конца прогона.
 *
 * <p><b>РАДИУСА остановки эта клетка не наблюдает, и это свойство предмета,
 * а не пропуск.</b> Подписка статистики объявлена одной темой — операндов
 * зерна не несёт ни один класс второго производителя
 * (docs/models/domain/other/StatisticsFact.md §«Признак несомого класса»), —
 * и утверждение «флаг лёг у своей пары, а не у группы» на одной паре не
 * выразимо вовсе.
 */
@DirtiesContext
class NestedGrainKeysBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него субстрат строит её группу и её тему. */
    private static final String SLUG = "b1-4";

    /** Биржевой счёт первой записи — на верхнем уровне содержимого. */
    private static final String TOP_LEVEL_ACCOUNT = "ACCOUNT-TOP";

    /** Определение стратегии первой записи — там же. */
    private static final String TOP_LEVEL_STRATEGY = "STRATEGY-TOP";

    /** Биржевой счёт второй записи — внутри вложенного объекта. */
    private static final String NESTED_ACCOUNT = "ACCOUNT-NESTED";

    /** Определение стратегии второй записи — там же. */
    private static final String NESTED_STRATEGY = "STRATEGY-NESTED";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B1.4 — Ключи зерна берутся одноимёнными компонентами верхнего уровня")
    void theGrainKeysComeFromSameNamedTopLevelComponents() {
        givenReceptionStateRows();
        OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));
        publish("E-4a", DEAL_CLOSED, occurredAt,
                Bodies.dealClosed(TOP_LEVEL_ACCOUNT, TOP_LEVEL_STRATEGY));
        awaitConsumed();

        Map<String, Object> written = dealFact();
        assertThat(written.get("exchange_account_internal_id")).isEqualTo(TOP_LEVEL_ACCOUNT);
        assertThat(written.get("strategy_internal_id")).isEqualTo(TOP_LEVEL_STRATEGY);

        publish("E-4b", DEAL_CLOSED, occurredAt,
                Bodies.dealClosedNestedKeys(NESTED_ACCOUNT, NESTED_STRATEGY));
        awaitHalted();

        // Строки у второй записи не появилось ВОВСЕ: биржевой счёт обязателен,
        // а на верхнем уровне одноимённого компонента нет — вход неполон.
        assertThat(rows.count(DEAL_FACTS)).isEqualTo(1L);
        assertThat(incidentFacts()).isEmpty();
        assertThat(dealFact()).as("лежащая строка не тронута").isEqualTo(written);
        // Обхода дерева не произошло: значений с глубины нет ни в одной колонке.
        assertThat(dealFact().values().stream().map(String::valueOf))
                .noneMatch(value -> value.contains(NESTED_ACCOUNT)
                        || value.contains(NESTED_STRATEGY));
        // Приём встал на этой записи: флаг лёг, смещение не продвинулось.
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.TRUE);
        assertThat(Wire.endOffset(topic())).isEqualTo(2L);
        assertThat(Wire.committedOffset(consumerGroup(), topic()))
                .as("зачтена только первая запись").isEqualTo(1L);
        // В тему мёртвых писем ничего не уходит — её нет.
        assertThat(Wire.topicNames()).doesNotContain(topic() + ".DLT");
    }
}
