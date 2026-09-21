package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Общее у клеток группы {@code B2}: неполный вход и единственная ветвь отказа
 * приёма (.claude/tests/cases/statistics.md §«B2 — Неполный вход: единственная
 * ветвь отказа приёма»).
 *
 * <p><b>У КАЖДОЙ клетки этой группы свой контекст, и это не роскошь.</b>
 * Отравленное сообщение повторяется без ограничения числа попыток
 * ({@code ReceptionErrorHandler}) и занимает единственный поток слушателя:
 * контекст, в котором оно легло, не принимает больше ничего. Вторая клетка в
 * том же контексте наблюдала бы исход первой, и её отрицания — «строки факта
 * нет», «смещение стои́т» — сошлись бы по ЛОЖНОЙ причине: сообщение до
 * обработки просто не дошло бы. Поэтому класс здесь равен клетке, а пара
 * «группа + тема» у него своя ({@link StatisticsSubstrate#registerOwn}).
 *
 * <p><b>Контекст закрывается вместе с классом</b> ({@link DirtiesContext}), и
 * довод тоже механический: контексты каркас теста кэширует и не закрывает, а
 * застрявший потребитель продолжает биться в брокер и в базу каждую паузу
 * повтора до конца прогона. Полтора десятка таких зомби — не экономия памяти,
 * а чужая нагрузка на субстрат соседних клеток. Закрытие ничего не теряет:
 * контекст у класса свой, и переиспользовать его некому.
 *
 * <p><b>Кейс с ДВУМЯ записями живёт двумя классами, и это следствие того
 * же.</b> Вторая отравленная запись в том же контексте не доезжает до
 * обработки вовсе — её ожидание сошлось бы по ложной причине; поэтому
 * {@code B2.3} и {@code B2.8} разведены по классам, а исход у них общий и
 * собран здесь.
 *
 * <p><b>РАДИУСА остановки эта группа не наблюдает ни одной клеткой, и это
 * свойство предмета, а не пропуск.</b> Подписка статистики объявлена одной
 * темой — операндов зерна не несёт ни один класс второго производителя
 * (docs/models/domain/other/StatisticsFact.md §«Признак несомого класса»), — и
 * утверждение «флаг лёг у своей пары, а не у группы» на одной паре не выразимо
 * вовсе. Тем же обстоятельством кейс {@code B2.15} назван непрогоняемым.
 */
@DirtiesContext
abstract class PoisonedReceptionBox extends StatisticsBox {

    /** Идентичность события, которым клетки этой группы травят приём. */
    protected static final String POISON_EVENT = "E-P";

    /** Биржевой счёт отравленных сообщений: обязательный ключ обоих зёрен. */
    protected static final String ACCOUNT = "ACCOUNT-1";

    /** Момент происшествия отравленного сообщения. */
    protected final OffsetDateTime occurredAt = momentsAgo(Duration.ofMinutes(5));

    /**
     * Полный конверт отравленного сообщения без названного заголовка.
     *
     * <p><b>Вход клетки есть ОТСУТСТВИЕ заголовка</b>, а не его пустое
     * значение: публикатор пустого заголовка не ставит вовсе, и пустота от
     * отсутствия отличается (docs/rules/absent-value-semantics.md).
     *
     * @param header имя заголовка, которого на записи не будет
     */
    protected Map<String, String> envelopeWithout(String header) {
        Map<String, String> headers =
                new LinkedHashMap<>(envelope(POISON_EVENT, DEAL_CLOSED, occurredAt));
        headers.remove(header);
        return headers;
    }

    /**
     * Полный конверт, у которого названный заголовок несёт названное значение.
     *
     * @param header имя заголовка
     * @param value  значение, которое на нём поедет
     */
    protected Map<String, String> envelopeWith(String header, String value) {
        Map<String, String> headers =
                new LinkedHashMap<>(envelope(POISON_EVENT, DEAL_CLOSED, occurredAt));
        headers.put(header, value);
        return headers;
    }

    /**
     * Кладёт отравленное сообщение и ждёт, пока приём по паре встанет.
     *
     * <p><b>Ожидается ФЛАГ, а не смещение</b>, и довод живёт у самого ожидания
     * ({@link StatisticsBox#awaitHalted}).
     *
     * @param headers заголовки конверта, которые есть; прочих нет
     * @param key     ключ записи — тенант; пусто означает «ключа нет»
     * @param payload содержимое дословно; пусто означает «тела нет»
     */
    protected void poison(Map<String, String> headers, String key, String payload) {
        Wire.publish(topic(), key, headers, payload);
        awaitHalted();
    }

    /** Отравленное сообщение штатного вида: конверт без названного заголовка. */
    protected void poisonWithout(String header) {
        poison(envelopeWithout(header), TENANT, Bodies.dealClosed(ACCOUNT, "S-1"));
    }

    /**
     * Исход, общий всей группе: строк фактов нет ни в одной таблице, смещение
     * стои́т, флаг лежит, непрерывность не утверждаема, наружу не ушло ничего.
     *
     * <p><b>«Смещение не продвинулось» выражено ПУСТОТОЙ, и это точно:</b>
     * отравленное сообщение у этих клеток — первая запись своей темы, группа не
     * фиксировала по ней ничего, и всякое ненулевое значение означало бы, что
     * запись зачтена принятой.
     *
     * <p><b>«В тему мёртвых писем ничего не уходит — её нет»</b> проверяется
     * именем, которое завела бы переадресация отказавшего сообщения: тема с
     * суффиксом {@code .DLT} у брокера субстрата не появляется
     * (docs/rules/durable-consumer-reception.md §«Обработчик отказа — часть
     * конструкции, а не настройка»).
     *
     * @param published сколько записей клетка положила в свою тему
     */
    protected void assertReceptionHalted(Long published) {
        assertThat(dealFacts()).as("сделочного факта нет").isEmpty();
        assertThat(incidentFacts()).as("факта происшествия нет тоже").isEmpty();
        assertThat(Wire.committedOffset(consumerGroup(), topic()))
                .as("смещение группы не продвинулось").isNull();
        assertThat(Wire.endOffset(topic())).as("записи лежат непринятыми").isEqualTo(published);
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.TRUE);
        assertThat(continuityClaimable()).as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
        assertThat(Wire.topicNames()).doesNotContain(topic() + ".DLT");
    }

    /** Исход группы у клетки, положившей в тему ровно одну запись. */
    protected void assertReceptionHalted() {
        assertReceptionHalted(1L);
    }
}
