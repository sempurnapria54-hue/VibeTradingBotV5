package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.AnomalyReportEntity;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Журнал происшествий (docs/models/domain/other/AnomalyReport.md). */
public interface AnomalyReportRepository extends JpaRepository<AnomalyReportEntity, Long> {

    /**
     * Носитель дедупа факта-СОСТОЯНИЯ: стои́т ли по этому ключу отчёт,
     * заведённый <b>в окне наблюдения</b> — не раньше {@code since} и не
     * позже {@code until}.
     *
     * <p><b>Пустые части ключа сравниваются как ПУСТЫЕ, а не
     * пропускаются:</b> у счётного радиуса инструмент пуст и в строке, и в
     * запросе, и условие «или параметр пуст» схлопнуло бы счётную строку с
     * инструментной.
     *
     * <p>Форма — {@code exists}, а не счёт строк: скан прерывается на
     * первом совпадении.
     */
    @Query("select case when exists (select 1 from AnomalyReportEntity r "
            + "where r.exchangeAccountId = :exchangeAccountId "
            + "and ((:instrumentId is null and r.instrumentId is null) or r.instrumentId = :instrumentId) "
            + "and ((:subject is null and r.subjectExternalId is null) or r.subjectExternalId = :subject) "
            + "and r.code = :code and r.severity = :severity "
            + "and r.createdAt >= :since and r.createdAt <= :until) then true else false end")
    Boolean existsStanding(@Param("exchangeAccountId") Long exchangeAccountId,
                           @Param("instrumentId") Long instrumentId,
                           @Param("subject") String subject,
                           @Param("code") String code,
                           @Param("severity") String severity,
                           @Param("since") OffsetDateTime since,
                           @Param("until") OffsetDateTime until);

    /**
     * Носитель ПОДТВЕРЖДЕНИЯ гистерезиса: у стоящей по ключу строки серия
     * жива, и последнее её наблюдение не моложе {@code observedUntil}.
     * Серию гасит полный проход, признака не наблюдавший, — поэтому живая
     * серия и есть «признак держался на каждом проходе с прошлого
     * наблюдения», а не «признак замечался в окне».
     *
     * <p><b>Верхняя граница обязательна:</b> без неё подтверждением служит
     * наблюдение того же или смежного прохода секундами раньше, а гонка
     * чтения, против которой гистерезис заведён, живёт такт.
     */
    @Query("select case when exists (select 1 from AnomalyReportEntity r "
            + "where r.exchangeAccountId = :exchangeAccountId "
            + "and ((:instrumentId is null and r.instrumentId is null) or r.instrumentId = :instrumentId) "
            + "and ((:subject is null and r.subjectExternalId is null) or r.subjectExternalId = :subject) "
            + "and r.code = :code and r.severity = :severity "
            + "and r.createdAt >= :since and r.lastObservedAt <= :observedUntil) then true else false end")
    Boolean existsSeries(@Param("exchangeAccountId") Long exchangeAccountId,
                         @Param("instrumentId") Long instrumentId,
                         @Param("subject") String subject,
                         @Param("code") String code,
                         @Param("severity") String severity,
                         @Param("since") OffsetDateTime since,
                         @Param("observedUntil") OffsetDateTime observedUntil);

    /** Продлить серию стоящей по ключу строки моментом наблюдения. */
    @Modifying
    @Query("update AnomalyReportEntity r set r.lastObservedAt = :observedAt "
            + "where r.exchangeAccountId = :exchangeAccountId "
            + "and ((:instrumentId is null and r.instrumentId is null) or r.instrumentId = :instrumentId) "
            + "and ((:subject is null and r.subjectExternalId is null) or r.subjectExternalId = :subject) "
            + "and r.code = :code and r.severity = :severity and r.createdAt >= :since")
    void markObserved(@Param("exchangeAccountId") Long exchangeAccountId,
                      @Param("instrumentId") Long instrumentId,
                      @Param("subject") String subject,
                      @Param("code") String code,
                      @Param("severity") String severity,
                      @Param("since") OffsetDateTime since,
                      @Param("observedAt") OffsetDateTime observedAt);

    /**
     * Прервать серии счёта, которых проход не продлил: последнее
     * наблюдение раньше начала прохода.
     */
    @Modifying
    @Query("update AnomalyReportEntity r set r.lastObservedAt = null "
            + "where r.exchangeAccountId = :exchangeAccountId and r.lastObservedAt < :passStartedAt")
    void breakSeries(@Param("exchangeAccountId") Long exchangeAccountId,
                     @Param("passStartedAt") OffsetDateTime passStartedAt);

    /**
     * Носитель ключа ПРОИСШЕСТВИЯ, привязанного к сущности-предмету: отчёт
     * с этим кодом по этому предмету уже заведён — окна нет, предмет сам
     * ограничивает происшествие одним моментом.
     */
    Boolean existsByCodeAndSubjectExternalId(String code, String subjectExternalId);
}
