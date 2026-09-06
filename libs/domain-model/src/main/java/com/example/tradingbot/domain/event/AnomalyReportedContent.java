package com.example.tradingbot.domain.event;

/**
 * Содержимое события «происшествие зафиксировано»: идентичность отчёта,
 * его радиус, класс и код. Снимки состояния сюда не едут — они лежат в
 * своей строке и потребителю не адресованы.
 */
public record AnomalyReportedContent(String anomalyReportInternalId,
                                     String exchangeAccountInternalId,
                                     String instrumentInternalId,
                                     String scope,
                                     String severity,
                                     String code) {
}
