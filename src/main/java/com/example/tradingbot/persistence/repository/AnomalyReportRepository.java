package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.anomaly.AnomalyReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyReportRepository extends JpaRepository<AnomalyReportEntity, Long> {

    /**
     * Дедуп STATE-отчёта по стоящему состоянию: пока по ключу
     * «биржа + код + severity» отчёт уже заведён, второй не создаётся
     * (docs/rules/error-handling-policy.md; потребитель —
     * UNCLASSIFIED_CASH_FLOW у писателя строки разбивки).
     */
    Boolean existsByExchangeIdAndCodeAndSeverity(Long exchangeId, String code, String severity);
}
