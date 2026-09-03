package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.anomaly.AnomalyReportEntity;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomalyReportRepository extends JpaRepository<AnomalyReportEntity, Long> {

    /**
     * Дедуп STATE-отчёта по стоящему состоянию: пока по ключу
     * «биржа + код + severity» отчёт уже заведён, второй не создаётся
     * (docs/rules/error-handling-policy.md; потребитель —
     * UNCLASSIFIED_CASH_FLOW у писателя строки разбивки).
     */
    Boolean existsByExchangeIdAndCodeAndSeverity(Long exchangeId, String code, String severity);

    /**
     * Носитель подтверждения гистерезиса и дедупа: стои́т ли по этому
     * ключу отчёт, заведённый <b>в окне</b> — не раньше {@code since} и не
     * позже {@code until}. Отдельного счётчика подряд идущих тиков не
     * заводится: durable-факт «признак наблюдался прошлым тиком» и есть
     * стоящая строка, и она переживает рестарт
     * (docs/components/AnomalyJob.md §«Такт и гистерезис»).
     *
     * <p><b>Границ две.</b> Нижняя — иначе отчёт недельной давности
     * читался бы подтверждением. Верхняя — иначе подтверждением служит
     * строка, заведённая тем же проходом секундами раньше: пара проходов,
     * идущих подряд (ручной триггер поверх планового тика, рестарт,
     * дрейф расписания), гонку чтения длиной в такт не переживает, а
     * формально «подтверждает» её жёсткой ступенью.
     *
     * <p>Пустые части ключа сравниваются как ПУСТЫЕ, а не пропускаются:
     * у биржевого радиуса инструмент пуст и в строке, и в запросе, и
     * условие «или параметр пуст» схлопнуло бы биржевую строку с
     * инструментной.
     *
     * <p>Форма — {@code exists}, а не {@code count(*) > 0}: скан
     * прерывается на первом совпадении, а не считает все строки журнала.
     */
    @Query("select case when exists (select 1 from AnomalyReportEntity r where r.exchangeId = :exchangeId "
            + "and ((:instrumentId is null and r.instrumentId is null) or r.instrumentId = :instrumentId) "
            + "and ((:subject is null and r.subjectExternalId is null) or r.subjectExternalId = :subject) "
            + "and r.code = :code and r.severity = :severity "
            + "and r.createdAt >= :since and r.createdAt <= :until) then true else false end")
    Boolean existsStanding(@Param("exchangeId") Long exchangeId,
                           @Param("instrumentId") Long instrumentId,
                           @Param("subject") String subject,
                           @Param("code") String code,
                           @Param("severity") String severity,
                           @Param("since") OffsetDateTime since,
                           @Param("until") OffsetDateTime until);
}
