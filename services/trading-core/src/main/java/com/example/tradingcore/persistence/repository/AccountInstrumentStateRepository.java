package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.AccountInstrumentStateEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Запросы по строке состояния счёта на инструменте.
 *
 * <p><b>Каждый точечный запрос сам ставит {@code modifiedAt} и
 * {@code modifiedBy}:</b> мимо сущности слушатели аудита не проходят
 * (docs/models/domain/other/Auditable.md §«Системные поля и точечная
 * запись»); значения приходят от границы, часы и автор — те же, что у
 * слушателя.
 */
public interface AccountInstrumentStateRepository extends JpaRepository<AccountInstrumentStateEntity, Long> {

    Optional<AccountInstrumentStateEntity> findByExchangeAccountIdAndInstrumentId(Long exchangeAccountId,
                                                                                  Long instrumentId);

    /**
     * Безопасная вставка по ключу: строка пары заводится, если её ещё нет,
     * и конкурент, пришедший вторым, не получает ни дубля, ни отказа
     * (docs/rules/idempotency-via-unique.md).
     *
     * <p><b>Почему нативным запросом, а не «прочитать и сохранить».</b>
     * Проверка «существует ли уже» не атомарна: два ленивых читателя пары
     * — проход сделки и сканер входа — попадают в неё одновременно, и
     * проигравший падал бы нарушением ключа, то есть ронял бы проход,
     * который ничего плохого не сделал.
     *
     * <p><b>Аудит проставляется здесь же:</b> нативная вставка слушателей
     * JPA не проходит, поэтому автор и момент записываются явно — иначе у
     * строки не было бы названного писателя
     * (docs/rules/writer-named-for-every-value.md). Момент приходит от
     * границы, а не часами базы: шкала у него та же, что у слушателя.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into account_instrument_states
                (exchange_account_id, instrument_id, safety_rung, margin_mode,
                 created_at, created_by, modified_at, modified_by)
            values
                (:exchangeAccountId, :instrumentId, :safetyRung, :marginMode,
                 :writtenAt, :writer, :writtenAt, :writer)
            on conflict (exchange_account_id, instrument_id) do nothing
            """)
    void insertIfAbsent(@Param("exchangeAccountId") Long exchangeAccountId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("safetyRung") String safetyRung,
                        @Param("marginMode") String marginMode,
                        @Param("writtenAt") OffsetDateTime writtenAt,
                        @Param("writer") String writer);

    /**
     * Инструменты счёта, стоящие в НАЗВАННЫХ ступенях. Проекция колонки,
     * а не строки: читателю нужен ответ «стои́т ли ступень», а не
     * состояние пары (.claude/rules/codestyle.md §«Выборка данных: не тянем
     * сущность ради одного поля»).
     *
     * <p>Перечень ступеней — параметр, потому что читателей два и границы
     * у них разные: отбору входа гасит вход ЛЮБАЯ ступень, а детектору
     * непроэнфорсенной блокировки нужна только жёсткая.
     *
     * <p>Читается ОДНИМ запросом на счёт: чтение по паре дало бы
     * обращение на каждый инструмент каталога, то есть выборку, растущую
     * вместе с ним. Пар без строки в ответе нет по построению — строка
     * материализуется лениво, и её отсутствие означает рабочее состояние.
     */
    @Query("""
            select s.instrumentId from AccountInstrumentStateEntity s
            where s.exchangeAccountId = :exchangeAccountId and s.safetyRung in :rungs""")
    List<Long> findInstrumentIdsInRungs(@Param("exchangeAccountId") Long exchangeAccountId,
                                        @Param("rungs") Collection<String> rungs);

    /**
     * Строки пар счёта, стоящие в НАЗВАННЫХ ступенях, — для читателя,
     * которому нужна сама ступень, а не факт её стояния. Одним запросом на
     * счёт по тому же доводу, что у проекции выше.
     */
    List<AccountInstrumentStateEntity> findByExchangeAccountIdAndSafetyRungIn(Long exchangeAccountId,
                                                                              Collection<String> safetyRungs);

    /**
     * Подъём ступени пары, <b>гардированный стоящей ступенью</b>: строка
     * переставляется, только если она стои́т в одной из перечисленных
     * (более низких) ступеней. Возвращает число применённых строк — оно и
     * есть анкер идемпотентности реакции
     * (docs/components/SafetyHoldCoordinator.md §Последовательность).
     *
     * <p><b>Гард стои́т в запросе, а не в вызывающем коде.</b> Проверка
     * «какая ступень стои́т сейчас» и последующая запись не атомарны: два
     * прохода, поднимающие ступень одной пары, оба прочитали бы рабочее
     * состояние и оба сочли бы себя первыми — то есть снятие риска
     * запустилось бы дважды.
     */
    @Modifying
    @Query("""
            update AccountInstrumentStateEntity s set s.safetyRung = :requested,
                                                      s.modifiedAt = :modifiedAt, s.modifiedBy = :modifiedBy
            where s.exchangeAccountId = :exchangeAccountId and s.instrumentId = :instrumentId
              and s.safetyRung in :lowerRungs""")
    int raiseRung(@Param("exchangeAccountId") Long exchangeAccountId,
                  @Param("instrumentId") Long instrumentId,
                  @Param("requested") String requested,
                  @Param("lowerRungs") Collection<String> lowerRungs,
                  @Param("modifiedAt") OffsetDateTime modifiedAt,
                  @Param("modifiedBy") String modifiedBy);

    /**
     * <b>Снятие ступени: переход ВНИЗ, гардированный НАЗВАННОЙ ступенью.</b>
     * Строка переставляется, только если стои́т ровно та ступень, которую
     * вызов снимает; возвращает число применённых строк.
     *
     * <p>Гард именем, а не рангом, и это несущее: он делает повтор
     * ХОЛОСТЫМ вместо шага по лестнице дальше. Иначе первый вызов снял бы
     * сворачивание, второй — мягкую ступень, и двойное нажатие вернуло бы
     * торговлю (docs/rules/manual-halt.md §«Ступень называется явно и у
     * снятия тоже»).
     */
    @Modifying
    @Query("""
            update AccountInstrumentStateEntity s set s.safetyRung = :target,
                                                      s.modifiedAt = :modifiedAt, s.modifiedBy = :modifiedBy
            where s.exchangeAccountId = :exchangeAccountId and s.instrumentId = :instrumentId
              and s.safetyRung = :standing""")
    int clearRung(@Param("exchangeAccountId") Long exchangeAccountId,
                  @Param("instrumentId") Long instrumentId,
                  @Param("standing") String standing,
                  @Param("target") String target,
                  @Param("modifiedAt") OffsetDateTime modifiedAt,
                  @Param("modifiedBy") String modifiedBy);
}
