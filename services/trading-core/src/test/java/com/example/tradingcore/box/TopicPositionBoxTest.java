package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Позиция чтения темы — клетка {@code B8.7}.
 *
 * <p><b>Предусловие ставится ДО подъёма контекста, и хук у этого один.</b>
 * Сообщение обязано лежать в теме раньше, чем появится подписка, а
 * единственная точка, исполняемая до создания контекста по построению
 * каркаса, — метод {@code @DynamicPropertySource}: его зовёт настройщик
 * контекста, то есть заведомо раньше его обновления. Побочное действие в
 * нём названо и ограничено ровно этим.
 *
 * <p><b>Группа потребителя своя, и она СВЕЖА по построению</b>
 * ({@link TradingCoreSubstrate} §шапка): зафиксированных смещений у нового
 * имени нет, и умолчание позиции чтения тем самым становится наблюдаемым.
 * С общей группой клетка мерила бы чужие смещения, а не умолчание.
 *
 * <p><b>Тема тоже своя, и клетке она прибавляет точности.</b> В общей
 * теме лежат определения всех соседних классов прогона, и «прочитано с
 * начала» доказывалось бы отметкой среди десятков чужих; в своей лежит
 * ровно одно сообщение — то, которое клетка и положила до подписки.
 *
 * <p><b>Базу этот класс НЕ опустошает.</b> Следствие принятого сообщения
 * возникает на подъёме контекста — то есть ДО первой клетки, — и штатное
 * опустошение перед клеткой стёрло бы ровно то, о чём клетка утверждает.
 *
 * <p><b>Наблюдается отметка обработанного, а не копия определения, и это
 * следствие построения.</b> Копию заводит активация, а её применение
 * резолвит идентичности счёта и инструмента в числовые ключи проекций;
 * проекции же ставятся ТИКОМ, то есть живой поверхностью, которой до
 * подъёма контекста не существует. Событие о определении без копии таких
 * входов не имеет, и его штатный исход — отметка в inbox — наблюдаем тем
 * же ходом: обработано значит прочитано, а прочитано с начала темы значит
 * позиция чтения не в конце.
 */
class TopicPositionBoxTest extends TradingCoreBox {

    /** Идентичность события, положенного до подъёма контекста. */
    private static final String EVENT_ID = "ev-before-startup";

    /** Идентичность определения, которого в копиях нет вовсе. */
    private static final String ABSENT_DEFINITION = "S-before-startup";

    /** Потолок ожидания следа приёма. */
    private static final Duration INTAKE_TIMEOUT = Duration.ofSeconds(60);

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-topic-position";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of());
        Wire.publishStrategyFact(TradingCoreSubstrate.ownStrategyTopic(NAME), EVENT_ID,
                "STRATEGY_DELETED",
                Definitions.lifecycle(Definitions.withDetail(ABSENT_DEFINITION, ACCOUNT, INSTRUMENT)),
                TENANT);
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    /** Штатное опустошение отменено — довод у шапки класса. */
    @Override
    @BeforeEach
    void resetSubstrate() {
        PeerStub.all().forEach(PeerStub::reset);
    }

    @Test
    @DisplayName("B8.7 — чтение темы начинается с начала, а не с конца")
    void theSubscriptionStartsReadingFromTheBeginningOfTheTopic() {
        // Сообщение опубликовано ДО подписки, и оно обработано: позиция
        // чтения в конце темы оставила бы ядро без фактов владельца
        // определений — в том числе без активации, по которой оно торгует.
        Awaitility.await().atMost(INTAKE_TIMEOUT).pollInterval(Duration.ofMillis(50))
                .until(() -> rows.countWhere("inbox_events", "event_id", EVENT_ID) == 1L);

        assertThat(rows.row("inbox_events", "event_id", EVENT_ID).get("event_type"))
                .isEqualTo("STRATEGY_DELETED");
        assertThat(rows.countWhere("strategies", "internal_id", ABSENT_DEFINITION)).isZero();
    }
}
