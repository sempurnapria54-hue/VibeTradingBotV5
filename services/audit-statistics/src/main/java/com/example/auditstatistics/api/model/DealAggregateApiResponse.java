package com.example.auditstatistics.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Строка сделочного зерна агрегатов в форме ответа поверхности
 * (docs/rules/statistics-aggregates.md §«Что хранится»).
 *
 * <p><b>Отдаются только слагаемые.</b> Доля выигрышных, профит-фактор,
 * ожидаемость, средний R и предикаты их показуемости здесь полями не
 * заводятся: их считает сторона, которая числа показывает, и это названное
 * ограничение с домом и оживителем
 * (docs/rules/statistics-aggregates.md §«Что хранится»).
 *
 * <p><b>Тенанта форма не несёт:</b> радиус приезжает операндом вызова, и
 * всякая строка ответа несёт его по построению отбора — поле повторяло бы
 * вопрос в каждом элементе ответа (.claude/rules/design-simplicity.md).
 *
 * <p><b>Ключа базы форма не несёт:</b> наружу идёт ключ зерна, он же
 * идентичность строки (.claude/rules/codestyle.md §«Идентичность наружу»).
 */
@Getter
@Builder
public class DealAggregateApiResponse {

    @Schema(description = "Биржевой счёт: компонент ключа зерна — радиус, по которому работают остановки")
    private final String exchangeAccountInternalId;

    @Schema(description = "Определение стратегии: компонент ключа зерна; "
            + "пусто означает «сделка стратегии не имеет»")
    private final String strategyInternalId;

    @Schema(description = "Сутки UTC: компонент ключа зерна, по нему идут окно и порядок")
    private final LocalDate bucketDate;

    @Schema(description = "Расчётная валюта результата: компонент ключа зерна; "
            + "пусто означает «валюта результата не резолвилась», и денежные суммы такой строки нулевые")
    private final String resultCurrency;

    @Schema(description = "Закрытых сделок за сутки — оба терминала")
    private final Integer closedDeals;

    @Schema(description = "Из них принявших риск: популяция всех долей. "
            + "Сделка, закрытая без входа, сюда не входит")
    private final Integer riskBearingDeals;

    @Schema(description = "Из принявших риск — с положительным результатом до финансирования")
    private final Integer winningDeals;

    @Schema(description = "Из принявших риск — с отрицательным результатом до финансирования")
    private final Integer losingDeals;

    @Schema(description = "Из принявших риск — с нулевым результатом до финансирования")
    private final Integer neutralDeals;

    @Schema(description = "Из принявших риск — те, у кого результат недоступен: нулём он не подменяется")
    private final Integer resultUnavailableDeals;

    @Schema(description = "Из принявших риск — те, у кого расчётная валюта результата не резолвилась")
    private final Integer currencyUnresolvedDeals;

    @Schema(description = "Из принявших риск — те, у кого плановый риск нулевой: "
            + "знаменатель отношения к риску их не покрывает")
    private final Integer riskUnsizedDeals;

    @Schema(description = "Из принявших риск — закрытые биржей по марже")
    private final Integer liquidatedDeals;

    @Schema(description = "Из принявших риск — принудительно сокращённые биржей")
    private final Integer forcedReductionDeals;

    @Schema(description = "Из принявших риск — с неустановленным торговым исходом")
    private final Integer outcomeUndeterminedDeals;

    @Schema(description = "Из принявших риск — с несошедшейся сверкой")
    private final Integer reconciliationMismatchedDeals;

    @Schema(description = "Из принявших риск — с непроверенной сверкой: "
            + "«не проверяли» отличается от «проверили, всё в порядке»")
    private final Integer reconciliationNotRunDeals;

    @Schema(description = "Из принявших риск — с неполной разбивкой движений")
    private final Integer breakdownIncompleteDeals;

    @Schema(description = "Из принявших риск — с неоценённой полнотой разбивки")
    private final Integer breakdownNotAssessedDeals;

    @Schema(description = "Из принявших риск — с потерянной базой риска")
    private final Integer riskBenchmarkMissingDeals;

    /**
     * Сколько сделок вошло в сумму R-мультипликаторов — вторая половина
     * среднего R.
     *
     * <p><b>Имя на проводе объявлено ЯВНО, и без этого величина не уезжала
     * бы вовсе.</b> Капитализация аксессоров в репозитории —
     * {@code beanspec} (lombok.config в корне), и у поля, чей второй знак
     * заглавный, геттер выходит {@code getrDenominatorDeals}; сериализатор
     * же считает геттером только {@code get} + ЗАГЛАВНАЯ, поэтому свойство
     * не опознаётся — <b>ни в теле ответа, ни в схеме</b>. Отказа при этом
     * нет: поле просто отсутствует. Поймано пробой поверхности, а не
     * чтением.
     */
    @JsonProperty("rDenominatorDeals")
    @Schema(description = "Сколько сделок вошло в сумму R-мультипликаторов — вторая половина среднего R")
    private final Integer rDenominatorDeals;

    @Schema(description = "Сумма результатов до накопленного финансирования — "
            + "то самое число, которым двигается счётчик серии убытков")
    private final BigDecimal resultBeforeFundingSum;

    @Schema(description = "Сумма итогов — net по всем издержкам, включая финансирование")
    private final BigDecimal netResultSum;

    @Schema(description = "Комиссии обеих ног, издержкой положительные")
    private final BigDecimal feeSum;

    @Schema(description = "Накопленное финансирование, издержкой положительное")
    private final BigDecimal fundingSum;

    @Schema(description = "Штрафы принудительного закрытия, издержкой положительные")
    private final BigDecimal liquidationPenaltySum;

    @Schema(description = "Сумма выигрышей отдельно: без неё профит-фактор не вычислим никак")
    private final BigDecimal winResultSum;

    @Schema(description = "Сумма убытков отдельно, знак сохранён")
    private final BigDecimal lossResultSum;

    @Schema(description = "Плановый риск сделок, вошедших в денежные суммы, — "
            + "знаменатель отношения к риску")
    private final BigDecimal plannedRiskSum;

    @Schema(description = "Плановый риск сделок, выведенных из этих сумм")
    private final BigDecimal plannedRiskExcludedSum;

    /**
     * Сумма R-мультипликаторов сделок: частное сумм средним R не является.
     *
     * <p>Имя на проводе объявлено явно по тому же доводу, что и у
     * знаменателя выше: иначе величина молча не уезжала бы.
     */
    @JsonProperty("rSum")
    @Schema(description = "Сумма R-мультипликаторов сделок: частное сумм средним R не является")
    private final BigDecimal rSum;

    @Schema(description = "Момент сборки строки: им читатель видит лаг проекции, "
            + "потому что строка пересчитывается джобой, а не двигается событием")
    private final OffsetDateTime assembledAt;
}
