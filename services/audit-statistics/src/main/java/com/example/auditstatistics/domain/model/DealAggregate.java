package com.example.auditstatistics.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Строка сделочного зерна агрегатов в доменной форме
 * (docs/rules/statistics-aggregates.md §«Что хранится»).
 *
 * <p><b>Хранятся только слагаемые; производные считает потребитель
 * выдачи.</b> Доля выигрышных, профит-фактор, ожидаемость, средний R и
 * предикаты их показуемости здесь не живут и полями не заводятся: это
 * форма показа, и она приезжает со стороной, которая числа показывает
 * (docs/rules/statistics-aggregates.md §«Что хранится», абзац о
 * потребителе выдачи).
 *
 * <p><b>Строка нерезолвленной валюты денежных сумм не несёт:</b> у неё все
 * суммы нулевые по построению, а видна такая популяция счётчиком
 * {@code currencyUnresolvedDeals}.
 *
 * <p><b>Ключа базы форма не несёт:</b> он деталь хранения, а идентичность
 * строки — её ключ зерна
 * (.claude/rules/codestyle.md §«Идентичность наружу»).
 */
@Value
@Builder
public class DealAggregate {

    /** Тенант-владелец строки: ключ зерна и радиус чтения. */
    String tenantId;

    /** Биржевой счёт: ключ зерна — радиус, по которому работают остановки. */
    String exchangeAccountInternalId;

    /** Определение стратегии: ключ зерна; пусто — «сделка стратегии не имеет». */
    String strategyInternalId;

    /** Сутки UTC: ключ зерна и порция прохода пересчёта. */
    LocalDate bucketDate;

    /** Расчётная валюта результата: ключ зерна; пусто — валюта не резолвилась. */
    String resultCurrency;

    /** Закрытых сделок за сутки — оба терминала. */
    Integer closedDeals;

    /** Из них принявших риск: популяция всех долей. */
    Integer riskBearingDeals;

    /** Из принявших риск — с положительным результатом до финансирования. */
    Integer winningDeals;

    /** Из принявших риск — с отрицательным результатом до финансирования. */
    Integer losingDeals;

    /** Из принявших риск — с нулевым результатом до финансирования. */
    Integer neutralDeals;

    /** Из принявших риск — те, у кого результат недоступен. */
    Integer resultUnavailableDeals;

    /** Из принявших риск — те, у кого расчётная валюта не резолвилась. */
    Integer currencyUnresolvedDeals;

    /** Из принявших риск — те, у кого плановый риск нулевой. */
    Integer riskUnsizedDeals;

    /** Из принявших риск — закрытые биржей по марже. */
    Integer liquidatedDeals;

    /** Из принявших риск — принудительно сокращённые биржей. */
    Integer forcedReductionDeals;

    /** Из принявших риск — с неустановленным торговым исходом. */
    Integer outcomeUndeterminedDeals;

    /** Из принявших риск — с несошедшейся сверкой. */
    Integer reconciliationMismatchedDeals;

    /** Из принявших риск — с непроверенной сверкой. */
    Integer reconciliationNotRunDeals;

    /** Из принявших риск — с неполной разбивкой движений. */
    Integer breakdownIncompleteDeals;

    /** Из принявших риск — с неоценённой полнотой разбивки. */
    Integer breakdownNotAssessedDeals;

    /** Из принявших риск — с потерянной базой риска. */
    Integer riskBenchmarkMissingDeals;

    /** Сколько сделок вошло в сумму R-мультипликаторов — вторая половина среднего R. */
    Integer rDenominatorDeals;

    /** Сумма результатов до накопленного финансирования. */
    BigDecimal resultBeforeFundingSum;

    /** Сумма итогов — net по всем издержкам, включая финансирование. */
    BigDecimal netResultSum;

    /** Комиссии обеих ног, издержкой положительные. */
    BigDecimal feeSum;

    /** Накопленное финансирование, издержкой положительное. */
    BigDecimal fundingSum;

    /** Штрафы принудительного закрытия, издержкой положительные. */
    BigDecimal liquidationPenaltySum;

    /** Сумма выигрышей: без неё профит-фактор не вычислим никак. */
    BigDecimal winResultSum;

    /** Сумма убытков, знак сохранён. */
    BigDecimal lossResultSum;

    /** Плановый риск сделок, вошедших в денежные суммы. */
    BigDecimal plannedRiskSum;

    /** Плановый риск сделок, выведенных из этих сумм. */
    BigDecimal plannedRiskExcludedSum;

    /** Сумма R-мультипликаторов сделок: частное сумм средним R не является. */
    BigDecimal rSum;

    /**
     * Момент сборки строки — показание читателю, а не операнд выбора
     * пересчитываемого.
     *
     * <p>Им читатель видит лаг проекции: строки пересчитываются джобой, а
     * не двигаются событием.
     */
    OffsetDateTime assembledAt;
}
