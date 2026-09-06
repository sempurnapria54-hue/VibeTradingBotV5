package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.AnomalyReportEntity;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Журнал происшествий (docs/models/domain/other/AnomalyReport.md). */
public interface AnomalyReportRepository extends JpaRepository<AnomalyReportEntity, Long> {

    /**
     * Носитель дедупа факта-СОСТОЯНИЯ: стои́т ли по этому ключу отчёт,
     * заведённый <b>в окне наблюдения</b> — не раньше {@code since} и не
     * позже {@code until}. Отдельного счётчика подряд идущих тиков не
     * заводится: durable-факт «признак наблюдался прошлым тиком» и есть
     * стоящая строка, и она переживает рестарт.
     *
     * <p><b>Границ две.</b> Нижняя — иначе отчёт недельной давности читался
     * бы подтверждением. Верхняя — иначе подтверждением служит строка,
     * заведённая тем же проходом секундами раньше: пара проходов, идущих
     * подряд (ручной триггер поверх планового тика, рестарт, дрейф
     * расписания), гонку чтения длиной в такт не переживает.
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
}
