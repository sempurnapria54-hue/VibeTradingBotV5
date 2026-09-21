package com.example.statistics.box;

import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.9} — пустые значения обязательных полей входа отвергаются
 * наравне с отсутствующими (.claude/tests/cases/statistics.md).
 *
 * <p><b>Клетка КРАСНАЯ по построению, и красной её делает дерево кода, а не
 * ожидание.</b> Ожидание взято из дома: умолчание не бывает благоприятным, и
 * ошибка направляется в запретительную сторону (docs/concept.md, П1).
 * Исполнитель мерит обязательность входа <b>ненулевой ссылкой</b>, а не
 * непустым значением ({@code DealFact.hasCompleteInput}), и все три записи
 * сегодня принимаются: первая даёт факт с пустой идентичностью, о который
 * отметка обработанного схлопнет всякое следующее событие того же момента;
 * вторая — строку с пустым тенантом, невидимую любой выборке чтения; третья —
 * строку с пустым биржевым счётом, то есть собственное зерно агрегата,
 * которого не прочитает никто. Все три суть молчаливая потеря факта ровно там,
 * где конструкция обещает громкую остановку (находка {@code F-4},
 * .claude/work/backlog.md §«Обязательность входа факта статистики мерится
 * ссылкой, а не значением»).
 *
 * <p><b>Свой контекст у клетки НЕ потому, что она травит приём сегодня</b> —
 * сегодня все три записи проходят, — <b>а потому, что она обязана травить его
 * завтра.</b> Положенная в общий ящик, она заняла бы его единственный поток
 * слушателя тем же ходом, которым долг будет закрыт, и уронила бы соседей
 * вместо себя.
 *
 * <p><b>Исход ждётся ДВУСТОРОННИМ условием</b> — флаг остановки либо
 * появившиеся строки, — и это не смягчение ассерта: ожидание одного лишь флага
 * истекало бы по таймауту целую минуту, сообщая ровно то же самое шестьюдесятью
 * секундами позже.
 */
@Tag("debt")
class EmptyMandatoryValuesBoxTest extends PoisonedReceptionBox {

    /** Сколько записей кладёт клетка: пустые идентичность, тенант и счёт. */
    private static final Long PUBLISHED = 3L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-9");
    }

    @Test
    @DisplayName("B2.9 — Пустые значения обязательных полей входа отвергаются наравне с отсутствующими")
    void emptyMandatoryValuesAreRejectedLikeAbsentOnes() {
        givenReceptionStateRows();

        Wire.publish(topic(), TENANT, envelopeWith(EVENT_ID, ""),
                Bodies.dealClosed(ACCOUNT, "S-1"));
        Wire.publish(topic(), "", envelope("E-9b", DEAL_CLOSED, occurredAt),
                Bodies.dealClosed(ACCOUNT, "S-1"));
        Wire.publish(topic(), TENANT, envelope("E-9c", DEAL_CLOSED, occurredAt),
                Bodies.dealClosed("", "S-1"));
        awaitSettled();

        assertReceptionHalted(PUBLISHED);
    }

    /**
     * Ждёт, пока все три записи получат исход: приём встал либо строки легли.
     *
     * <p>Второе условие и есть сегодняшнее поведение; на нём клетка падает,
     * предъявляя долг.
     */
    private void awaitSettled() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Boolean.TRUE, pair(topic()).get(HALTED_COLUMN))
                        || Objects.equals(rows.count(DEAL_FACTS), PUBLISHED));
    }
}
