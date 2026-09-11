package com.example.auditstatistics.persistence.repository.journalread;

import com.example.auditstatistics.persistence.model.journal.JournalReceptionStateEntity;
import com.example.auditstatistics.persistence.repository.ReceptionStateCompletenessQueries;
import org.springframework.data.repository.Repository;

/**
 * Операнды полноты по строкам состояния приёма, читаемые ИЗ БАЗЫ ЖУРНАЛА
 * под ролью агрегатов (docs/architecture/data-ownership.md §Раскладка:
 * «Читателей у третьего подключения два»).
 *
 * <p><b>Второй читатель третьего подключения — агрегатная выборка</b>, и
 * обе её величины полноты лежат в чужой для модуля статистики базе.
 * Берёт она их <b>не тем же запросом, что строки</b>: кросс-базовых
 * запросов не бывает, строки идут из своей базы.
 *
 * <p><b>Своих объявлений запросов у интерфейса нет ни одного</b> — все три
 * приходят общим родителем ({@link ReceptionStateCompletenessQueries}):
 * схема одна, и копия текста разошлась бы с первой молча. Расходится
 * подключение, а вместе с ним права: запись через него отвергает
 * <b>база</b>, а не дисциплина автора
 * (docs/spec/owner-role-grants.json, {@code writeConfinedToOwner}).
 *
 * <p><b>Наследует {@code Repository}, а не {@code JpaRepository}, и это
 * несущий выбор:</b> унаследованный {@code save} завёл бы в коде тропу
 * записи в чужую базу — то есть отказ приходил бы в рантайме вместо того,
 * чтобы отсутствовать вовсе.
 */
public interface ReceptionStateSourceRepository
        extends Repository<JournalReceptionStateEntity, Long>, ReceptionStateCompletenessQueries {
}
