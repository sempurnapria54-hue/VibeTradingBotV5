package com.example.auditstatistics.persistence.repository.journal;

import com.example.auditstatistics.persistence.model.journal.JournalReceptionStateEntity;
import com.example.auditstatistics.persistence.repository.ReceptionStateCompletenessQueries;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Запросы по строке состояния приёма.
 *
 * <p><b>Запросы разведены по писателю, и это разведение несущее</b>
 * (docs/models/domain/other/AuditRecord.md, таблица писателей):
 * <ul>
 *   <li><b>ходы приёма</b> — слушателя и его обработчика ошибок — суть
 *       ОБНОВЛЕНИЯ существующей строки, и вставки нет ни у одного:
 *       вставка отсюда завела бы у состава пар второго писателя. Пока
 *       строки нет, обновление не находит цели и не делает ничего — это
 *       верный исход, а не пропущенная ветвь: окно ограничено одним
 *       тактом тика, а отравленное сообщение доставляется повторно;</li>
 *   <li><b>ходы тика</b> — заведение строки появившейся темы и приведение
 *       признака подписки к составу подписки. Момент наблюдения тик
 *       ставит ТОЛЬКО заведением; дальше его переписывает слушатель, и
 *       обновления тика его не трогают.</li>
 * </ul>
 *
 * <p><b>Читающий запрос лежит в общем родителе, когда читателей ДВА, а не
 * потому, что он читающий.</b> Три операнда полноты
 * ({@link ReceptionStateCompletenessQueries}) читают два подключения к базе
 * журнала — своё и кросс-подключение модуля статистики, — и текст запроса у
 * них обязан быть один (docs/architecture/data-ownership.md §Раскладка).
 * Выборка моментов пары ({@link #subscribedPairMoments}) читателя имеет
 * ровно одного — тик состояния приёма, ходящий подключением владельца, — и
 * поднятая в общего родителя, она досталась бы и читателю чужой базы,
 * которому не нужна.
 */
public interface JournalReceptionStateRepository
        extends JpaRepository<JournalReceptionStateEntity, Long>, ReceptionStateCompletenessQueries {

    /**
     * Транзакция приёма: снять флаг остановки и подвинуть момент
     * последнего принятого события.
     *
     * <p><b>Момент двигается только вперёд.</b> Порядок между партициями
     * темы не обещан ничем, и запись «как пришло» опустила бы величину на
     * переупорядоченной паре. {@code greatest} в Postgres пустые значения
     * игнорирует, поэтому первая же принятая строка ставит момент, а более
     * ранний следом пришедший его не опускает.
     *
     * <p><b>Снятие флага не обусловлено движением момента:</b> повторно
     * доставленное событие <b>принято</b> — оно уже в журнале, — и снятие
     * флага на нём законно.
     *
     * <p>Момент последнего такта тика здесь не пишется: его писатель — тик
     * состояния приёма, и второго у величины нет
     * (docs/rules/writer-named-for-every-value.md).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update journal_reception_states
               set reception_halted = false,
                   last_accepted_occurred_at = greatest(last_accepted_occurred_at, :occurredAt)
             where consumer_group = :consumerGroup
               and topic = :topic
            """)
    void markAccepted(@Param("consumerGroup") String consumerGroup,
                      @Param("topic") String topic,
                      @Param("occurredAt") OffsetDateTime occurredAt);

    /**
     * Постановка флага остановки — <b>отдельной транзакцией</b>, до того
     * как отказ уйдёт наружу.
     *
     * <p>Той же транзакцией, что и обработка, флаг откатился бы вместе с
     * ней — то есть не появился бы ровно в том случае, ради которого
     * заведён (docs/models/domain/other/AuditRecord.md §«Отдельная
     * транзакция у флага остановки — не деталь реализации, а условие
     * существования флага»).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update journal_reception_states
               set reception_halted = true
             where consumer_group = :consumerGroup
               and topic = :topic
            """)
    void markHalted(@Param("consumerGroup") String consumerGroup,
                    @Param("topic") String topic);

    /**
     * Постановка момента разрыва — отдельной транзакцией: момент
     * назначения партиций лежит вне обработки сообщения вовсе.
     *
     * <p>Записывается <b>момент</b>, а не признак: он отвечает и на «был
     * ли разрыв», и на «лежит ли он внутри обещаемого ряда». Гасит его
     * чистка журнала, когда разрыв уходит за глубину хранения.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update journal_reception_states
               set lag_gap_at = :moment
             where consumer_group = :consumerGroup
               and topic = :topic
            """)
    void markGap(@Param("consumerGroup") String consumerGroup,
                 @Param("topic") String topic,
                 @Param("moment") OffsetDateTime moment);

    /**
     * Наблюдение по паре началось заново: зафиксированного смещения группы
     * не осталось, позиция назначена умолчанием, и доказать непрерывность
     * с прежнего момента нечем
     * (docs/models/domain/other/AuditRecord.md §«Третий исход сравнения
     * смещений двигает границу, а не предикат»).
     *
     * <p><b>Момент только вперёд:</b> нижняя граница полноты монотонна, и
     * опустить её переписыванием назад значило бы обещать больше, чем
     * журнал несёт.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update journal_reception_states
               set observed_since = :moment
             where consumer_group = :consumerGroup
               and topic = :topic
               and observed_since < :moment
            """)
    void restartObservation(@Param("consumerGroup") String consumerGroup,
                            @Param("topic") String topic,
                            @Param("moment") OffsetDateTime moment);

    /**
     * Ход тика: завести строку появившейся в подписке темы.
     *
     * <p><b>Начальные значения названы, а не подразумеваются:</b> момент
     * наблюдения — момент такта (он позже фактического начала чтения, то
     * есть консервативнее); признак подписки — истина; <b>флаг остановки
     * — ложь</b>, потому что остановка есть исход отказа, а не начальное
     * состояние; момент разрыва и момент последнего принятого события
     * остаются пустыми — «разрыва не было» и «не принято ещё ничего»
     * (docs/rules/absent-value-semantics.md).
     *
     * <p><b>Конфликт по ключу пары поглощается.</b> Строка уже есть —
     * заводить нечего, и её величины приёма чужие: перезапись затёрла бы
     * момент наблюдения и флаг остановки, которые пишет слушатель.
     * Проверка «существует ли уже» перед вставкой запрещена домом
     * идемпотентности (docs/rules/idempotency-via-unique.md).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into journal_reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (:consumerGroup, :topic, :moment, true, false, :moment)
            on conflict (consumer_group, topic) do nothing
            """)
    void openPair(@Param("consumerGroup") String consumerGroup,
                  @Param("topic") String topic,
                  @Param("moment") OffsetDateTime moment);

    /**
     * Ход тика: признак подписки — истина темам подписки, момент
     * обновления — каждым тактом.
     *
     * <p>Момент пишется независимо от того, было ли что принимать:
     * величина, обновляемая только при работе, не отличала бы «всё
     * спокойно» от «потребителя нет».
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update journal_reception_states
               set subscribed = true,
                   updated_at = :moment
             where consumer_group = :consumerGroup
               and topic in (:topics)
            """)
    void markSubscribed(@Param("consumerGroup") String consumerGroup,
                        @Param("topics") Collection<String> topics,
                        @Param("moment") OffsetDateTime moment);

    /**
     * Ход тика: тема ушла из подписки — признак снимается, <b>строка не
     * удаляется</b>.
     *
     * <p>Удаление опустило бы нижнюю границу полноты: её второй операнд
     * есть максимум по моментам наблюдения, а строки журнала снятой темы
     * из журнала не исчезают. Без снятия признака строка осталась бы той,
     * которую никто не обновляет, и предикат непрерывности стал бы ложен
     * навсегда (docs/models/domain/other/AuditRecord.md).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update journal_reception_states
               set subscribed = false,
                   updated_at = :moment
             where consumer_group = :consumerGroup
               and topic not in (:topics)
            """)
    void markUnsubscribed(@Param("consumerGroup") String consumerGroup,
                          @Param("topics") Collection<String> topics,
                          @Param("moment") OffsetDateTime moment);

    /**
     * Ход чистки: погасить момент разрыва у пар, чей разрыв чистка вынесла
     * ЗА нижнюю границу полноты
     * (docs/models/domain/other/AuditRecord.md §«Момент разрыва вместо
     * признака разрыва — решение, а не форма записи»).
     *
     * <p><b>Это единственный писатель снятия у величины.</b> Пара, чей
     * разрыв лежит раньше границы, дыры внутри обещаемого ряда больше не
     * имеет, и держать её предикат непрерывности ложным значило бы обещать
     * меньше, чем журнал несёт. В {@code prod} чистки нет, и момент не
     * гаснет там никогда — верно по существу.
     *
     * <p><b>Пустой момент условием не затрагивается</b>: сравнение с
     * пустым значением истины не даёт, и отдельного «момент записан»
     * условие не требует — оно было бы вторым носителем того же отбора.
     *
     * <p><b>Момент обновления строки ход НЕ трогает</b>: его писатель —
     * тик состояния приёма, и второго у величины нет
     * (docs/rules/writer-named-for-every-value.md). Сдвинутый чисткой, он
     * утверждал бы живость приёма в момент, когда приёма могло не быть
     * вовсе.
     *
     * <p><b>Фильтр по группе стои́т</b> по тому же доводу, что у ходов
     * тика: строка чужой группы — чужая запись, и правка её была бы
     * записью без писателя.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update journal_reception_states
               set lag_gap_at = null
             where consumer_group = :consumerGroup
               and lag_gap_at < :lowerBound
            """)
    void clearGapsBefore(@Param("consumerGroup") String consumerGroup,
                         @Param("lowerBound") OffsetDateTime lowerBound);

    /**
     * Моменты подписанных пар группы — вход возраста последнего принятого
     * события (docs/spec/audit-journal.json, {@code lastEventAgeMs}).
     *
     * <p><b>Область — только ПОДПИСАННЫЕ пары</b>, и она та же, что у
     * свёртки алерта: у снятой темы нет ни производителя лага, ни предмета
     * алерта (там же, {@code lagAlertFires}). Строка снятой темы при этом
     * живёт дальше — её момент наблюдения держит нижнюю границу полноты, и
     * область ЭТОЙ выборки на ту не влияет.
     *
     * <p><b>Запрос по отображению, а не нативный</b> — по тому же доводу,
     * что у операндов полноты: у нативного чтения тип результата выводится
     * из метаданных JDBC, и драйвер отдаёт по {@code timestamptz}
     * {@code java.sql.Timestamp}, а расхождение пришло бы СМЕЩЕНИЕМ
     * времени, то есть тихо (замер захода K5).
     */
    @Query("""
            select state.topic as topic,
                   state.lastAcceptedOccurredAt as lastAcceptedOccurredAt,
                   state.observedSince as observedSince
              from JournalReceptionStateEntity state
             where state.consumerGroup = :consumerGroup
               and state.subscribed = true
            """)
    List<ReceptionPairMomentRow> subscribedPairMoments(@Param("consumerGroup") String consumerGroup);
}
