package com.example.tradingbot.message;

/**
 * Содержимое события «происшествие зафиксировано»: идентичность отчёта, его
 * радиус, класс, код и актор. Снимки состояния сюда не едут — они лежат в
 * своей строке и потребителю не адресованы.
 *
 * <p><b>Актор едет содержимым, потому что у класса есть ручная тропа:</b>
 * отчёт заводит не только детекция — ручная остановка заводит его и на
 * постановке ступени, и на снятии, и обе тропы доходят до писателя события
 * (docs/rules/manual-halt.md, docs/spec/event-actor-presence.json). Область
 * значений — домовая: <b>класс</b>, а не идентификатор пользователя
 * (docs/models/domain/other/Auditable.md §«Область значений актора»).
 *
 * @param anomalyReportInternalId   идентичность отчёта о происшествии
 * @param exchangeAccountInternalId биржевой счёт радиуса
 * @param instrumentInternalId      инструмент радиуса; пусто у счётного
 *                                  происшествия
 * @param scope                     радиус происшествия — имя значения
 *                                  {@code HoldScope}; область значений
 *                                  домовая
 *                                  (docs/components/models/HoldSignal.md),
 *                                  и здесь она не переписывается
 * @param severity                  класс происшествия — имя значения
 *                                  {@code AnomalyReport.Severity}; область
 *                                  значений домовая
 *                                  (docs/models/domain/other/AnomalyReport.md),
 *                                  и здесь она не переписывается
 * @param code                      машинный код класса происшествия
 * @param actor                     кто инициировал ход: имя предъявленного
 *                                  принципала либо класс собственного
 *                                  прохода
 */
public record AnomalyReportedMessage(String anomalyReportInternalId,
                                     String exchangeAccountInternalId,
                                     String instrumentInternalId,
                                     String scope,
                                     String severity,
                                     String code,
                                     String actor) {
}
