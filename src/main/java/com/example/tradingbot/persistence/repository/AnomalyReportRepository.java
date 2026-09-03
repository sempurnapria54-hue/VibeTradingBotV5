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
     * Носитель подтверждения гистерезиса: стои́т ли по этому ключу отчёт,
     * заведённый НЕ РАНЬШЕ порога. Отдельного счётчика подряд идущих
     * тиков не заводится — durable-факт «признак наблюдался прошлым
     * тиком» и есть стоящая строка, и она переживает рестарт
     * (docs/components/AnomalyJob.md §«Такт и гистерезис»). Без порога
     * отчёт недельной давности читался бы подтверждением.
     *
     * <p>Пустые части ключа сравниваются как ПУСТЫЕ, а не пропускаются:
     * у биржевого радиуса инструмент пуст и в строке, и в запросе, и
     * условие «или параметр пуст» схлопнуло бы биржевую строку с
     * инструментной.
     */
    @Query("select count(r) > 0 from AnomalyReportEntity r where r.exchangeId = :exchangeId "
            + "and ((:instrumentId is null and r.instrumentId is null) or r.instrumentId = :instrumentId) "
            + "and ((:subject is null and r.subjectExternalId is null) or r.subjectExternalId = :subject) "
            + "and r.code = :code and r.severity = :severity and r.createdAt >= :since")
    Boolean existsStanding(@Param("exchangeId") Long exchangeId,
                           @Param("instrumentId") Long instrumentId,
                           @Param("subject") String subject,
                           @Param("code") String code,
                           @Param("severity") String severity,
                           @Param("since") OffsetDateTime since);
}
