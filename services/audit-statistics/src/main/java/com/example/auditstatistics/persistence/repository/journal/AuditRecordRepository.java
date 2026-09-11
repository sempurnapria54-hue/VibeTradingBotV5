package com.example.auditstatistics.persistence.repository.journal;

import com.example.auditstatistics.persistence.model.journal.AuditRecordEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по строкам журнала событий. */
public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, Long> {

    /**
     * Вставка строки журнала, <b>поглощающая конфликт по ключу дедупа</b>
     * (docs/components/AuditEventListener.md §«Форма вставки — поглощающая
     * конфликт по ключу»).
     *
     * <p><b>Почему не «проверить и вставить».</b> Проверка «существует ли
     * уже» перед вставкой запрещена домом идемпотентности
     * (docs/rules/idempotency-via-unique.md): она не атомарна, и отметкой
     * обработанного служит сам ключ строки.
     *
     * <p><b>Почему не ловля отката.</b> Нарушение ключа откатывает
     * транзакцию целиком, а в ней лежит и прочая работа прохода — снятие
     * флага остановки и момент последнего принятого события. Повторная
     * доставка штатна, и она обязана оставаться <b>принятой</b>, а не
     * превращаться в отказ.
     *
     * <p><b>Момент приёма приходит параметром, а не берётся у базы:</b>
     * его писателем объявлен код приёма
     * (docs/models/domain/other/AuditRecord.md §«Единственная ветвь, на
     * которой строки не будет, — неполный вход»).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into audit_records
                (event_id, tenant_id, event_type, occurred_at, recorded_at, version,
                 trace_context, exchange_account_internal_id, instrument_internal_id,
                 deal_internal_id, strategy_internal_id, content)
            values
                (:eventId, :tenantId, :eventType, :occurredAt, :recordedAt, :version,
                 :traceContext, :exchangeAccountInternalId, :instrumentInternalId,
                 :dealInternalId, :strategyInternalId, cast(:content as jsonb))
            on conflict (event_id) do nothing
            """)
    void insertAbsorbingDuplicate(@Param("eventId") String eventId,
                                  @Param("tenantId") String tenantId,
                                  @Param("eventType") String eventType,
                                  @Param("occurredAt") OffsetDateTime occurredAt,
                                  @Param("recordedAt") OffsetDateTime recordedAt,
                                  @Param("version") Integer version,
                                  @Param("traceContext") String traceContext,
                                  @Param("exchangeAccountInternalId") String exchangeAccountInternalId,
                                  @Param("instrumentInternalId") String instrumentInternalId,
                                  @Param("dealInternalId") String dealInternalId,
                                  @Param("strategyInternalId") String strategyInternalId,
                                  @Param("content") String content);

    /**
     * Ход чистки: удалить ПОРЦИЮ строк, принятых раньше глубины хранения
     * (docs/components/JournalCleanupJob.md §«Удаление окном, а не „всё
     * старое разом“»).
     *
     * <p><b>Порция обязательна, а не желательна.</b> Журнал растёт быстрее
     * любой таблицы контура, и безлимитная правка запрещена домом
     * конвенций (.claude/rules/codestyle.md §«Выборка данных…»): одним
     * оператором «удалить всё старое» проход держал бы блокировки на всём,
     * что накопилось, и рос бы вместе с журналом.
     *
     * <p><b>Отбор — по моменту ПРИЁМА.</b> На этом стои́т клейм отбора
     * пересчёта агрегатов: у всякой удалённой строки момент приёма меньше
     * самого раннего уцелевшего, а произойти событие раньше, чем было
     * принято, не могло (docs/rules/statistics-aggregates.md §«Пересчёт —
     * проекция, а не накопитель»). На оси происшествия это перестало бы
     * быть верным — удалённой оказалась бы строка, принятая позже
     * уцелевшей.
     *
     * <p><b>Порядок внутри подзапроса несущий:</b> без него порция берётся
     * произвольной, и самые старые строки могли бы не уходить вовсе, пока
     * подходящих под условие больше порции.
     *
     * @return сколько строк удалено — проход повторяется, пока порция
     *         полна
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            delete from audit_records
             where id in (select id
                            from audit_records
                           where recorded_at < :threshold
                           order by recorded_at
                           limit :batchSize)
            """)
    Integer deleteBatchRecordedBefore(@Param("threshold") OffsetDateTime threshold,
                                      @Param("batchSize") Integer batchSize);

    /**
     * Первый операнд нижней границы полноты: самый ранний момент приёма
     * среди СОХРАНИВШИХСЯ строк (docs/spec/audit-journal.json,
     * {@code journalMinRecordedAt}).
     *
     * <p><b>Запрос ЗДЕСЬ не нативный, и это не стилевая разница с
     * соседями.</b> У нативного скалярного чтения тип результата выводится
     * из метаданных JDBC, а драйвер Postgres объявляет {@code min(timestamptz)}
     * кодом 93 ({@code TIMESTAMP}) и отдаёт {@code java.sql.Timestamp} —
     * замерено прогоном захода K5. Объявленный {@code OffsetDateTime} тогда
     * держался бы на преобразовании, которого никто не назначал, а ошибка
     * пришла бы СМЕЩЕНИЕМ времени, то есть тихо. У запроса по отображению
     * тип берётся из самой сущности, и выводить его неоткуда.
     *
     * <p>Пустой журнал отдаёт пустое значение, и это значение, а не ноль:
     * ветвь разбирает вызывающий (docs/rules/absent-value-semantics.md).
     */
    @Query("select min(record.recordedAt) from AuditRecordEntity record")
    OffsetDateTime earliestRecordedAt();

    /**
     * Страница журнальной выборки: окно тенанта по моменту происшествия,
     * от новых к старым, с курсорной позицией и четырьмя необязательными
     * отборами по радиусам
     * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
     *
     * <p><b>Страница берётся КУРСОРОМ, а не смещением.</b> Сравнение идёт
     * составным значением {@code (occurred_at, event_id)}: пара уникальна
     * на любом подмножестве строк, потому что вторая её половина — ключ
     * дедупа доставки. Смещение на растущей таблице пропускало бы и
     * дублировало строки при вставке между запросами.
     *
     * <p><b>Порядок сортировки и порядок сравнения обязаны совпадать.</b>
     * Читаем от новых к старым, поэтому следующая страница — строки
     * строго <b>меньше</b> курсора; разошедшись, они отдали бы читателю
     * пустоту вместо хвоста окна.
     *
     * <p><b>Отбор выбирает строки СО ЗНАЧЕНИЕМ:</b> заданный радиус
     * сравнивается равенством, а равенство пустоты не берёт. Строки без
     * радиуса этим входом не запрашиваются — вопрос «у кого радиуса нет»
     * есть экранный срез и отложен вместе с прочими
     * (.claude/work/backlog.md §«Состав экранных операций чтения
     * `audit-statistics` — по появлению их потребителя»).
     *
     * <p><b>Необязательные операнды приведены {@code cast}'ом, и это не
     * украшение.</b> Пустое значение уходит в базу параметром без
     * собственного текста, и тип его выводить неоткуда: приведение
     * называет тип на месте, а не оставляет его на догадку драйвера. Тот
     * же класс дефекта, что назвал заход K5 у скалярного чтения, — только
     * приходит он не смещением времени, а отказом разбора.
     *
     * <p><b>План запроса ограничен ОКНОМ, а не отбором:</b> окно
     * обязательно и ограничено сверху ({@code JournalReadProperties}),
     * поэтому скан идёт по ограниченному ряду при любом наборе
     * радиусов — своим индексом покрыты две тропы из четырёх
     * ({@code ix_audit_record_tenant_deal},
     * {@code ix_audit_record_tenant_strategy}).
     *
     * @param limit сколько строк прочитать. Вызывающий просит на одну
     *              больше страницы: лишняя строка отвечает на вопрос «окно
     *              дочитано или нет» и наружу не отдаётся
     */
    @Query(nativeQuery = true, value = """
            select record.*
              from audit_records record
             where record.tenant_id = :tenantId
               and record.occurred_at >= :from
               and record.occurred_at <= :to
               and (cast(:cursorOccurredAt as timestamptz) is null
                    or (record.occurred_at, record.event_id)
                       < (cast(:cursorOccurredAt as timestamptz), cast(:cursorEventId as varchar)))
               and (cast(:exchangeAccountInternalId as varchar) is null
                    or record.exchange_account_internal_id = cast(:exchangeAccountInternalId as varchar))
               and (cast(:instrumentInternalId as varchar) is null
                    or record.instrument_internal_id = cast(:instrumentInternalId as varchar))
               and (cast(:dealInternalId as varchar) is null
                    or record.deal_internal_id = cast(:dealInternalId as varchar))
               and (cast(:strategyInternalId as varchar) is null
                    or record.strategy_internal_id = cast(:strategyInternalId as varchar))
             order by record.occurred_at desc, record.event_id desc
             limit :limit
            """)
    List<AuditRecordEntity> findPage(@Param("tenantId") String tenantId,
                                     @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to,
                                     @Param("cursorOccurredAt") OffsetDateTime cursorOccurredAt,
                                     @Param("cursorEventId") String cursorEventId,
                                     @Param("exchangeAccountInternalId") String exchangeAccountInternalId,
                                     @Param("instrumentInternalId") String instrumentInternalId,
                                     @Param("dealInternalId") String dealInternalId,
                                     @Param("strategyInternalId") String strategyInternalId,
                                     @Param("limit") Integer limit);
}
