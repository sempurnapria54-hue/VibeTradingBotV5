package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Общее у клеток группы {@code B2}: неполный вход и единственная ветвь
 * отказа (.claude/tests/cases/audit.md §«B2 — Неполный вход: единственная
 * ветвь отказа»).
 *
 * <p><b>У КАЖДОЙ клетки этой группы свой контекст, и это не роскошь.</b>
 * Отравленное сообщение повторяется без ограничения числа попыток
 * ({@code JournalReceptionErrorHandler}) и занимает единственный поток
 * слушателя: контекст, в котором оно легло, не принимает больше ничего —
 * ни по этой теме, ни по соседней. Вторая клетка в том же контексте
 * наблюдала бы исход первой, и её отрицания — «строки журнала нет»,
 * «смещение стои́т» — сошлись бы по ЛОЖНОЙ причине: сообщение до обработки
 * просто не дошло бы. Поэтому класс здесь равен клетке, а пара «группа +
 * темы» у него своя ({@link AuditSubstrate#registerOwn}).
 *
 * <p><b>Контекст закрывается вместе с классом</b>
 * ({@link DirtiesContext}), и довод тоже механический: контексты каркас
 * теста кэширует и не закрывает, а застрявший потребитель продолжает
 * биться в брокер и в базу каждую паузу повтора до конца прогона. Полтора
 * десятка таких зомби — не экономия памяти, а чужая нагрузка на субстрат
 * соседних клеток. Закрытие ничего не теряет: контекст у класса свой, и
 * переиспользовать его некому.
 *
 * <p><b>Вторая половина двух проверок — РАДИУС остановки.</b> Флаг лежит
 * у своей пары, соседняя его не получает — и это ровно та ошибка в
 * разрешающую сторону, которая названа находкой {@code F-2}: приём по
 * соседней теме не идёт тоже, потому что поток один, а её попарный
 * предикат об этом молчит. Клетки утверждают о наблюдаемом сегодня
 * поведении, находку не подменяя
 * (.claude/tests/cases/audit.md §«Находки владельцам»).
 */
@DirtiesContext
abstract class PoisonedReceptionBox extends AuditBox {

    /** Идентичность события, которым клетки этой группы травят приём. */
    protected static final String POISON_EVENT = "E-P";

    /** Момент происшествия отравленного сообщения. */
    protected final OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));

    /** Тема, в которую клетка кладёт отравленное сообщение. */
    protected String poisonedTopic() {
        return subscription().getFirst();
    }

    /** Соседняя пара той же группы: ею наблюдается радиус остановки. */
    protected String neighbourTopic() {
        return subscription().getLast();
    }

    /**
     * Полный конверт отравленного сообщения без названного заголовка.
     *
     * <p><b>Вход клетки есть ОТСУТСТВИЕ заголовка</b>, а не его пустое
     * значение: публикатор пустого заголовка не ставит вовсе, и пустота
     * от отсутствия отличается (docs/rules/absent-value-semantics.md).
     *
     * @param header имя заголовка, которого на записи не будет
     */
    protected Map<String, String> envelopeWithout(String header) {
        Map<String, String> headers = new LinkedHashMap<>(envelope(POISON_EVENT, occurredAt));
        headers.remove(header);
        return headers;
    }

    /**
     * Полный конверт, у которого названный заголовок несёт названное
     * значение.
     *
     * @param header имя заголовка
     * @param value  значение, которое на нём поедет
     */
    protected Map<String, String> envelopeWith(String header, String value) {
        Map<String, String> headers = new LinkedHashMap<>(envelope(POISON_EVENT, occurredAt));
        headers.put(header, value);
        return headers;
    }

    /**
     * Кладёт отравленное сообщение и ждёт, пока приём по паре встанет.
     *
     * <p><b>Ожидается ФЛАГ, а не смещение</b>, и довод живёт у самого
     * ожидания ({@link AuditBox#awaitHalted}).
     *
     * @param headers заголовки конверта, которые есть; прочих нет
     * @param key     ключ записи — тенант; пусто означает «ключа нет»
     * @param payload содержимое дословно; пусто означает «тела нет»
     */
    protected void poison(Map<String, String> headers, String key, String payload) {
        Wire.publish(poisonedTopic(), key, headers, payload);
        awaitHalted(poisonedTopic());
    }

    /** Отравленное сообщение штатного вида: конверт без названного заголовка. */
    protected void poisonWithout(String header) {
        poison(envelopeWithout(header), TENANT, Bodies.reference());
    }

    /**
     * Исход, общий всей группе: строки нет, смещение стои́т, флаг лежит у
     * своей пары, непрерывность не утверждаема, наружу не ушло ничего.
     *
     * <p><b>«Смещение не продвинулось» выражено ПУСТОТОЙ, и это точно:</b>
     * отравленное сообщение у этих клеток — первая запись темы, группа не
     * фиксировала по ней ничего, и всякое ненулевое значение означало бы,
     * что запись зачтена принятой.
     *
     * <p><b>«В тему мёртвых писем ничего не уходит — её нет»</b> проверяется
     * именем, которое завела бы переадресация отказавшего сообщения: тема
     * с суффиксом {@code .DLT} у брокера субстрата не появляется.
     */
    protected void assertReceptionHalted() {
        assertThat(records()).as("строки журнала нет").isEmpty();
        assertThat(Wire.committedOffset(consumerGroup(), poisonedTopic()))
                .as("смещение группы не продвинулось").isNull();
        assertThat(Wire.endOffset(poisonedTopic())).as("запись лежит непринятой").isEqualTo(1L);
        assertThat(pair(poisonedTopic()).get(HALTED_COLUMN)).isEqualTo(Boolean.TRUE);
        assertThat(pair(neighbourTopic()).get(HALTED_COLUMN))
                .as("флаг лёг у своей пары, а не у группы (F-2)").isEqualTo(Boolean.FALSE);
        assertThat(continuityClaimable()).as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
        assertThat(Wire.topicNames()).doesNotContain(poisonedTopic() + ".DLT");
        assertThat(Wire.endOffset(neighbourTopic())).as("наружу не ушло ничего").isZero();
    }
}
