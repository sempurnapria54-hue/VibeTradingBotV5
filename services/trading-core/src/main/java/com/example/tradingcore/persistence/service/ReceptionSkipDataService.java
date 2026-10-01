package com.example.tradingcore.persistence.service;

import static org.apache.commons.lang3.StringUtils.remove;
import static org.apache.commons.lang3.StringUtils.truncate;

import com.example.tradingcore.persistence.repository.ReceptionSkipRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для следа пропуска отравленной записи
 * (docs/rules/durable-consumer-reception.md §«След пропуска — таблица
 * `reception_skips`»).
 *
 * <p><b>Своя транзакция ({@code REQUIRES_NEW}), и довод — критерий дома.</b>
 * След есть свидетельство о ходе приёма, а не следствие принятого: транзакция
 * обработки к моменту записи уже откатилась, и подхваченная чужая унесла бы
 * след вместе с собой (docs/rules/durable-consumer-reception.md
 * §«Транзакционные границы»).
 *
 * <p><b>Отказ записи НЕ поглощается</b> — в отличие от строки отказа доступа
 * ({@link AccessDenialDataService}): проглоченный отказ сделал бы пропуск
 * бесшумным. Вызывающий (обработчик отказа приёма) пробрасывает его, и
 * запись возвращается в повтор.
 *
 * <p><b>Значения с провода усекаются по ширине колонки и очищаются от
 * нулевого символа.</b> Идентичность, класс и ключ приходят от
 * производителя, а первопричина несёт куски содержимого; значение, которое
 * база не примет, роняло бы запись следа <b>каждым</b> повтором — то есть
 * отравленная запись остановила бы партию навсегда, ровно тем исходом,
 * который пропуск и снимает. Координаты не усекаются: их несёт брокер, и
 * по ним запись возвращается.
 */
@Service
@RequiredArgsConstructor
public class ReceptionSkipDataService {

    /** Ширина колонки идентичности события. */
    private static final int EVENT_ID_WIDTH = 64;

    /** Ширина колонки класса события. */
    private static final int EVENT_TYPE_WIDTH = 128;

    /** Ширина колонки ключа записи. */
    private static final int TENANT_ID_WIDTH = 64;

    /** Нулевой символ: строковые колонки Postgres его не принимают. */
    private static final char NUL = '\u0000';

    private final ReceptionSkipRepository repository;

    /**
     * Записать след пропуска, если следа этой записи ещё нет; {@code true} —
     * строка легла этим вызовом, {@code false} — след по тем же координатам
     * уже лежал (повторная доставка после несостоявшейся фиксации смещения).
     *
     * @param consumerGroup группа потребителя, пропускающая запись
     * @param topic         тема записи
     * @param partition     партиция записи
     * @param offset        смещение записи
     * @param eventId       идентичность события из конверта; пусто — не было
     * @param eventType     класс события из конверта; пусто — не было
     * @param tenantId      ключ записи; пусто — не было
     * @param cause         первопричина отказа: класс и сообщение
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Boolean recordSkipIfAbsent(String consumerGroup, String topic, Integer partition, Long offset,
                                      String eventId, String eventType, String tenantId, String cause) {
        return repository.insertIfAbsent(consumerGroup, topic, partition, offset,
                wireValue(eventId, EVENT_ID_WIDTH), wireValue(eventType, EVENT_TYPE_WIDTH),
                wireValue(tenantId, TENANT_ID_WIDTH), remove(cause, NUL),
                OffsetDateTime.now(ZoneOffset.UTC)) > 0;
    }

    /** Значение с провода в форме, которую колонка примет; пустое остаётся пустым. */
    private static String wireValue(String value, int width) {
        return truncate(remove(value, NUL), width);
    }
}
