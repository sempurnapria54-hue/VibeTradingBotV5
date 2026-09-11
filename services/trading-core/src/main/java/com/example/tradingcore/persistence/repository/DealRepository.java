package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.DealEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Запросы по строке сделки.
 *
 * <p><b>Охраны write-once и монотонности стоят В САМИХ запросах, а не в
 * вызывающем коде</b> (docs/models/domain/aggregate/Deal.md
 * §Персистентность): строка создаётся раньше, чем наблюдается факт,
 * поэтому запретом на запись их выразить нельзя, а охрана, оставленная
 * вызывающему, держится ровно до второго вызывающего.
 */
public interface DealRepository extends JpaRepository<DealEntity, Long> {

    Optional<DealEntity> findByInternalId(String internalId);

    /**
     * Нетерминальные сделки ограниченным окном — вход прохода
     * оркестратора. Окно обязательно: безлимитное чтение растёт вместе с
     * числом торговых строк (.claude/rules/codestyle.md §Выборка данных).
     */
    List<DealEntity> findByStatusNotInOrderByIdAsc(Collection<String> statuses, Pageable pageable);

    /**
     * Слот пары «счёт, инструмент» занят — гейт входа. Радиус пары, а не
     * инструмента: инструмент принадлежит площадке, и у двух счетов одной
     * площадки он один.
     */
    boolean existsByExchangeAccountIdAndInstrumentIdAndStatusNotIn(Long exchangeAccountId, Long instrumentId,
                                                                  Collection<String> statuses);

    /**
     * У счёта есть незакрытая сделка хоть по одной паре — <b>контурная</b>
     * проверка гейта входа (docs/components/EntryScannerJob.md §«Гейт
     * входа»).
     *
     * <p>Радиус — счёт, а не установка целиком: ограничение «торгуется
     * один инструмент на счёт» защищает капитал одного счёта, и сделка на
     * чужом счёте о нём ничего не говорит. Инварианта базы у этой
     * проверки нет; гонку тиков закрывает защита от конкурентного
     * выполнения.
     */
    boolean existsByExchangeAccountIdAndStatusNotIn(Long exchangeAccountId, Collection<String> statuses);

    /**
     * Инструменты счёта, у которых есть незакрытая сделка, — ПРОЕКЦИЕЙ
     * ключа и ОДНИМ запросом на счёт.
     *
     * <p>Читатель — обход проактивной детекции: ему тот же вопрос нужен по
     * КАЖДОМУ инструменту контура, и вопрос по одной паре на итерацию дал
     * бы запрос в цикле длиной в окно контура
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Query("select distinct d.instrumentId from DealEntity d "
            + "where d.exchangeAccountId = :exchangeAccountId and d.status not in :statuses")
    List<Long> findInstrumentIdsWithActiveDeal(@Param("exchangeAccountId") Long exchangeAccountId,
                                               @Param("statuses") Collection<String> statuses);

    /**
     * Нетерминальные сделки счёта — популяция каскадного снятия риска
     * биржевого радиуса (docs/components/KillSwitchService.md §«Два
     * радиуса»).
     *
     * <p><b>Окна у выборки нет намеренно.</b> Мощность ограничена
     * построением: слот пары «счёт, инструмент» держит не больше одной
     * незакрытой сделки, то есть перечень не длиннее каталога контура.
     * Окно здесь резало бы каскад молча — то есть оставляло бы живой риск
     * ровно на тех сделках, до которых не дошло, — а перечень обязан быть
     * полным: неподтверждённая сделка делает неподтверждённым весь каскад.
     */
    List<DealEntity> findByExchangeAccountIdAndStatusNotIn(Long exchangeAccountId, Collection<String> statuses);

    /** Нетерминальные сделки пары «счёт, инструмент» — популяция радиуса пары. */
    List<DealEntity> findByExchangeAccountIdAndInstrumentIdAndStatusNotIn(Long exchangeAccountId,
                                                                          Long instrumentId,
                                                                          Collection<String> statuses);

    /**
     * Сделки счёта недавним окном — чтение поверхности. Порядок от новых:
     * читатель приходит за текущим состоянием торговой строки, а история
     * счёта растёт без предела (.claude/rules/codestyle.md §«Выборка
     * данных»).
     */
    @Query("""
            select d from DealEntity d
            where d.exchangeAccountId = :exchangeAccountId
            order by d.id desc""")
    List<DealEntity> findRecentOnAccount(@Param("exchangeAccountId") Long exchangeAccountId,
                                         Pageable pageable);

    /**
     * ТЕРМИНАЛЬНЫЕ сделки счёта недавним окном — вторая половина популяции
     * предусловия снятия холда (docs/rules/manual-halt.md §«Выборка и
     * производитель предусловия названы»).
     *
     * <p><b>Выборка только по нетерминальным была бы пуста ровно на
     * мотивирующей тропе:</b> остаточный риск живёт и после терминала —
     * снятие риска у сворачивания best-effort, и подтверждения могло не
     * прийти.
     *
     * <p>Окно обязательно и упорядочено от новых: риск, вернувшийся после
     * терминала, живёт на недавно закрытой сделке, а история счёта растёт
     * без предела.
     */
    @Query("""
            select d from DealEntity d
            where d.exchangeAccountId = :exchangeAccountId and d.status in :statuses
            order by d.id desc""")
    List<DealEntity> findRecentTerminalOnAccount(@Param("exchangeAccountId") Long exchangeAccountId,
                                                 @Param("statuses") Collection<String> statuses,
                                                 Pageable pageable);

    /** Та же половина радиусом пары «счёт, инструмент». */
    @Query("""
            select d from DealEntity d
            where d.exchangeAccountId = :exchangeAccountId and d.instrumentId = :instrumentId
              and d.status in :statuses
            order by d.id desc""")
    List<DealEntity> findRecentTerminalOnPair(@Param("exchangeAccountId") Long exchangeAccountId,
                                              @Param("instrumentId") Long instrumentId,
                                              @Param("statuses") Collection<String> statuses,
                                              Pageable pageable);

    /**
     * Сделки счёта, которые уводит жёсткая ступень биржевого радиуса, —
     * популяция <b>первого хода энфорсмента</b>
     * (docs/rules/error-handling-policy.md §«Жёсткая ступень энфорсится
     * непрерывно, а не одним ходом»).
     *
     * <p><b>Отбор по исходным статусам, а не по нетерминальности:</b>
     * сделке, уже стоящей в {@code ERROR}, ребра больше нет, и читать её
     * ради ноля применённых строк незачем.
     *
     * <p><b>Окна у выборки нет намеренно</b>, и довод тот же, что у
     * популяции снятия риска: мощность ограничена построением — слот пары
     * «счёт, инструмент» держит не больше одной незакрытой сделки. Окно
     * резало бы каскад молча.
     */
    List<DealEntity> findByExchangeAccountIdAndStatusIn(Long exchangeAccountId,
                                                        Collection<String> statuses);

    /** Та же популяция радиусом пары «счёт, инструмент». */
    List<DealEntity> findByExchangeAccountIdAndInstrumentIdAndStatusIn(Long exchangeAccountId,
                                                                       Long instrumentId,
                                                                       Collection<String> statuses);

    /**
     * Какие из названных сделок стоя́т на счёте с запрошенной ступенью —
     * операнд энфорсмента жёсткой ступени
     * (docs/components/DealOrchestratorJob.md §«Цикл прохода»).
     *
     * <p><b>Одним запросом на проход, а не чтением ступени на сделку.</b>
     * Радиусов у сделки два, и чтение каждого по строке дало бы два
     * обращения на сделку — то есть выборку, растущую вместе с числом
     * торговых строк (.claude/rules/codestyle.md §«Выборка данных»).
     *
     * <p>Возвращаются идентичности, а не строки: шагу нужен ответ
     * «стои́т ли ступень», а сами счета у него уже есть.
     */
    @Query("""
            select d.id from DealEntity d
            join ExchangeAccountEntity a on a.id = d.exchangeAccountId
            where d.id in :dealIds and a.safetyRung = :rung""")
    List<Long> findIdsUnderAccountRung(@Param("dealIds") Collection<Long> dealIds,
                                       @Param("rung") String rung);

    /** Тот же операнд радиусом пары «счёт, инструмент». */
    @Query("""
            select d.id from DealEntity d
            join AccountInstrumentStateEntity s
              on s.exchangeAccountId = d.exchangeAccountId and s.instrumentId = d.instrumentId
            where d.id in :dealIds and s.safetyRung = :rung""")
    List<Long> findIdsUnderInstrumentRung(@Param("dealIds") Collection<Long> dealIds,
                                          @Param("rung") String rung);

    /**
     * Ребро в {@code ERROR} энфорсментом жёсткой ступени: статус и причина
     * выхода из штатного ведения — <b>одной транзакцией</b>
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
     *
     * <p><b>Гард — исходный статус, и он же держит однократность.</b> У
     * сделки, уже стоящей в {@code ERROR}, ребра больше нет: каждым
     * проходом энфорсмент уводит очередные активные сделки радиуса, а не
     * переписывает поле уже уведённых. Отсюда же берётся разрешённая
     * перезапись на ребре {@code EXIT_PENDING → ERROR}: guard'а на
     * непустоту причины нет — почему сделка перестала вестись штатно
     * СЕЙЧАС, это холд.
     *
     * <p>Запросом, а не чтением с записью: разложение на два хода
     * добавило бы окно, в котором сделка успевает уйти терминалом.
     */
    @Modifying
    @Query("""
            update DealEntity d set d.status = :errorStatus, d.shutdownReason = :shutdownReason
            where d.id = :dealId and d.status in :activeStatuses""")
    int enforceHardRung(@Param("dealId") Long dealId,
                        @Param("shutdownReason") String shutdownReason,
                        @Param("errorStatus") String errorStatus,
                        @Param("activeStatuses") Collection<String> activeStatuses);

    /**
     * Ребро в {@code ERROR} <b>без причины</b> — общий запрос обеих троп,
     * у которых писателя причины нет: перехвата петли (прямая запись, без
     * действия и без анкера — docs/processes/fsm-execution-layering.md) и
     * звена аварийного действия
     * (docs/components/MarkDealErrorExecutor.md).
     *
     * <p><b>Механизмы у этих троп разные, а запись одна и та же.</b>
     * Различает их эмиссия — есть ли действие и анкер, — а не SQL: обе
     * ставят статус и обе причины не пишут, писателя у неё нет по
     * построению (docs/lifecycles/Deal.md §«Причина выхода из штатного
     * ведения», третья клетка перебора). Отдельный запрос от энфорсмента
     * ступени — там причина обязательна, здесь запрещена.
     *
     * <p><b>Гард исходного статуса держит сделку, ушедшую из-под
     * писателя:</b> строка, уже уведённая каскадом ступени либо
     * терминализованная, ребра не получает, и ноль применённых строк
     * означает «писать нечего», а не ошибку.
     */
    @Modifying
    @Query("""
            update DealEntity d set d.status = :errorStatus
            where d.id = :dealId and d.status in :activeStatuses""")
    int applyErrorEdge(@Param("dealId") Long dealId,
                       @Param("errorStatus") String errorStatus,
                       @Param("activeStatuses") Collection<String> activeStatuses);

    /**
     * Применение статусного ребра прохода: статус и обе причины.
     *
     * <p><b>Точечным запросом, а не записью строки целиком.</b> Колонки
     * сделки правятся и охраняемыми запросами звеньев того же прохода
     * (граница окна движений, метка добытости, база риска), а модель в
     * памяти собрана ДО них — сохранение строки целиком откатило бы
     * записанное диспетчеризацией.
     *
     * <p>Гард исходного статуса держит то же, что и у энфорсмента: пока
     * шли команды, терминальное звено могло применить своё ребро, и
     * запись поверх него вернула бы сделку из терминала.
     */
    @Modifying
    @Query("""
            update DealEntity d
               set d.status = :status, d.shutdownReason = :shutdownReason, d.closeReason = :closeReason
             where d.id = :dealId and d.status = :fromStatus""")
    int applyStatusEdge(@Param("dealId") Long dealId,
                        @Param("status") String status,
                        @Param("shutdownReason") String shutdownReason,
                        @Param("closeReason") String closeReason,
                        @Param("fromStatus") String fromStatus);

    /**
     * <b>Терминальное ребро сделки: статус и причина закрытия одним
     * точечным запросом.</b> Оба терминала пишут им — штатный
     * ({@code ACTIVE}/{@code EXIT_PENDING} → {@code CLOSED}) и аварийный
     * ({@code ERROR} → {@code EMERGENCY_CLOSED}); различает их набор
     * разрешённых исходных статусов, а не запрос.
     *
     * <p><b>Записью строки целиком терминал не ставится, и довод тот же,
     * что у {@link #applyStatusEdge}:</b> колонки сделки правятся
     * охраняемыми запросами звеньев того же прохода, а модель в памяти
     * собрана ДО них.
     *
     * <p><b>Гард исходного статуса разводит терминал с каскадом жёсткой
     * ступени.</b> Проактивная детекция уводит активные сделки радиуса в
     * {@code ERROR} своим тиком и своим потоком
     * (docs/rules/error-handling-policy.md §«Жёсткая ступень энфорсится
     * непрерывно, а не одним ходом»); без гарда терминал, выведенный из
     * снимка начала прохода, перезаписал бы этот каскад — то есть довёл бы
     * сделку до терминала на счёте, стоящем под биржевой ступенью, и не
     * оставил бы следа. Ноль применённых строк здесь означает «сделка
     * ушла из-под прохода», и звено обязано на нём остановиться.
     *
     * <p>Причина закрытия сводится вызывающим (write-once по старшинству
     * либо значением затребователя аварийного ребра) и пишется как
     * сведена — та же раскладка, что у {@link #applyStatusEdge}
     * (docs/lifecycles/Deal.md).
     */
    @Modifying
    @Query("""
            update DealEntity d set d.status = :status, d.closeReason = :closeReason
            where d.id = :dealId and d.status in :fromStatuses""")
    int applyTerminalEdge(@Param("dealId") Long dealId,
                          @Param("status") String status,
                          @Param("closeReason") String closeReason,
                          @Param("fromStatuses") Collection<String> fromStatuses);

    /**
     * <b>Итоговое число сделки вместе с четвёркой признаков отбора — одним
     * точечным запросом.</b> Атомарность пары несущая: durable-факт «число
     * финализировано» служит охраной от перезаписи признаков аварийным
     * терминалом, приходящим на усечённом графе
     * (docs/spec/deal-lifecycle.json §benchmarkAvailabilityOnTerminal), и
     * признак, отставший от числа, эту охрану снял бы.
     *
     * <p><b>Гард — незаполненное число, и он же делает write-once
     * структурным.</b> Прежде однократность держал вызывающий: и
     * финализация выхода, и оба терминала проверяли непустоту числа на
     * модели, собранной в начале прохода. Охрана, оставленная
     * вызывающему, держится ровно до второго вызывающего
     * (docs/models/domain/aggregate/Deal.md §Персистентность), а
     * вызывающих здесь три.
     *
     * <p><b>Пустое число законно и оставляет строку под тем же гардом:</b>
     * аварийный терминал на недоступном итоге пишет одни признаки, число
     * остаётся пустым со смыслом «неисчислимо»
     * (docs/components/MarkDealEmergencyClosedExecutor.md).
     */
    @Modifying
    @Query("""
            update DealEntity d
               set d.resultProfit = :resultProfit, d.resultProfitCurrency = :resultProfitCurrency,
                   d.closeOutcome = :closeOutcome, d.reconciliationStatus = :reconciliationStatus,
                   d.breakdownIncomplete = :breakdownIncomplete,
                   d.riskBenchmarkAvailability = :riskBenchmarkAvailability
             where d.id = :dealId and d.resultProfit is null""")
    int applyResultAndFeatures(@Param("dealId") Long dealId,
                               @Param("resultProfit") BigDecimal resultProfit,
                               @Param("resultProfitCurrency") String resultProfitCurrency,
                               @Param("closeOutcome") String closeOutcome,
                               @Param("reconciliationStatus") String reconciliationStatus,
                               @Param("breakdownIncomplete") String breakdownIncomplete,
                               @Param("riskBenchmarkAvailability") String riskBenchmarkAvailability);

    /**
     * <b>Четвёрка чисел риска — точечным запросом.</b> Пересчёт идёт
     * каждой правкой операнда и по построению переписывает ровно эти
     * четыре колонки; записью строки целиком он вернул бы к снимку начала
     * прохода и всё остальное — статус, границы окон, метки добытости.
     *
     * <p><b>Гарда исходного статуса у него нет намеренно:</b> числа
     * описательны, и их свежесть на сделке, уведённой каскадом ступени в
     * {@code ERROR}, не вредна — вредна была бы запись статуса, которой
     * здесь нет.
     */
    @Modifying
    @Query("""
            update DealEntity d
               set d.plannedRiskAmount = :plannedRiskAmount,
                   d.incurredRiskAmount = :incurredRiskAmount,
                   d.currentRiskAmount = :currentRiskAmount,
                   d.protectionRelievedRiskAmount = :protectionRelievedRiskAmount
             where d.id = :dealId""")
    int applyRiskNumbers(@Param("dealId") Long dealId,
                         @Param("plannedRiskAmount") BigDecimal plannedRiskAmount,
                         @Param("incurredRiskAmount") BigDecimal incurredRiskAmount,
                         @Param("currentRiskAmount") BigDecimal currentRiskAmount,
                         @Param("protectionRelievedRiskAmount") BigDecimal protectionRelievedRiskAmount);

    /**
     * Порог доказанного покрытия двигается только вперёд: число
     * наблюдений равно числу закрывшихся эпизодов, и порог обязан
     * накрывать движения всех — откат назад стёр бы покрытие раннего
     * эпизода.
     */
    @Modifying
    @Query("""
            update DealEntity d set d.coverageProvenThrough = :observedAt
            where d.id = :dealId
              and (d.coverageProvenThrough is null or d.coverageProvenThrough < :observedAt)""")
    int advanceCoverageProvenThrough(@Param("dealId") Long dealId,
                                     @Param("observedAt") OffsetDateTime observedAt);

    /**
     * Write-once нижней границы окна линковки движений: заполненная
     * граница повторной записью не перетирается — её пишет первая
     * отправленная входная заявка сделки, каким бы траншем она ни
     * ставилась.
     */
    @Modifying
    @Query("""
            update DealEntity d set d.billsWindowBegin = :observedAt
            where d.id = :dealId and d.billsWindowBegin is null""")
    int applyBillsWindowBegin(@Param("dealId") Long dealId,
                              @Param("observedAt") OffsetDateTime observedAt);

    /**
     * Метка «движения добыты по …» двигается только вперёд: откат назад
     * стёр бы факт более глубокой добычи.
     */
    @Modifying
    @Query("""
            update DealEntity d set d.billsFetchedThrough = :fetchedThrough
            where d.id = :dealId
              and (d.billsFetchedThrough is null or d.billsFetchedThrough < :fetchedThrough)""")
    int advanceBillsFetchedThrough(@Param("dealId") Long dealId,
                                   @Param("fetchedThrough") OffsetDateTime fetchedThrough);

    /**
     * Write-once базы риска: охрана адресует не конкуренцию писателей, а
     * вторую и последующие входные заявки — знаменатель всех четырёх
     * потолков фиксируется ПЕРВЫМ сайзингом сделки.
     */
    @Modifying
    @Query("""
            update DealEntity d set d.plannedRiskEquityBase = :equityBase
            where d.id = :dealId and d.plannedRiskEquityBase is null""")
    int applyPlannedRiskEquityBase(@Param("dealId") Long dealId,
                                   @Param("equityBase") BigDecimal equityBase);

    /**
     * Write-once валюты риска — той же природы, что и база: снимок
     * момента первого сайзинга. Отдельным запросом, а не вместе с базой:
     * база приходит с торгового состояния счёта, валюта — с расчёта
     * действия, и одна из них может быть непустой без другой.
     */
    @Modifying
    @Query("""
            update DealEntity d set d.plannedRiskCurrency = :currency
            where d.id = :dealId and d.plannedRiskCurrency is null""")
    int applyPlannedRiskCurrency(@Param("dealId") Long dealId,
                                 @Param("currency") String currency);
}
