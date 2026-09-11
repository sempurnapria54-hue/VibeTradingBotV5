package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.auditstatistics.mapping.AggregateMapper;
import com.example.auditstatistics.persistence.repository.aggregates.DealAggregateRepository;
import com.example.auditstatistics.persistence.repository.aggregates.IncidentAggregateRepository;
import com.example.auditstatistics.persistence.repository.journalread.DealGrainRow;
import com.example.auditstatistics.persistence.repository.journalread.IncidentGrainRow;
import com.example.auditstatistics.persistence.service.DealAggregateDataService;
import com.example.auditstatistics.persistence.service.IncidentAggregateDataService;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Значение каждой величины зерна доезжает до СВОЕЙ колонки.
 *
 * <p><b>Почему проба нужна и почему именно такая.</b> Строка зерна несёт
 * три десятка однотипных чисел, и запись их идёт позиционным списком: два
 * соседних счётчика, поменянные местами, компилируются, проходят любой
 * структурный контроль и дают человеку <b>правдоподобное</b> число — «из
 * пяти сделок выиграли две» вместо «проиграли две». Ошибка эта не
 * наблюдаема ничем, кроме сверки значений поимённо.
 *
 * <p><b>Различитель — значение, выведенное из имени свойства.</b> Строка
 * выдачи подменяется заглушкой, отдающей на каждый геттер значение,
 * однозначно опознающее его свойство; ожидание в проверке выводится из
 * ИМЕНИ той величины, которой параметр объявлен. Совпадение значений у
 * двух разных свойств сделало бы пробу слепой, поэтому их различность
 * проверяется отдельно.
 */
class AggregateUpsertArgumentsTest {

    private static final LocalDate BUCKET = LocalDate.of(2026, 9, 9);
    private static final OffsetDateTime ASSEMBLED =
            OffsetDateTime.of(2026, 9, 10, 4, 15, 0, 0, ZoneOffset.UTC);

    private final DealAggregateRepository dealRepository = mock(DealAggregateRepository.class);
    private final IncidentAggregateRepository incidentRepository = mock(IncidentAggregateRepository.class);
    private final AggregateMapper mapper = mock(AggregateMapper.class);
    private final DealAggregateDataService dealDataService = new DealAggregateDataService(dealRepository, mapper);
    private final IncidentAggregateDataService incidentDataService =
            new IncidentAggregateDataService(incidentRepository, mapper);

    @Test
    @DisplayName("Различители величин попарно различны — иначе проба ниже слепа")
    void theDiscriminatorsAreDistinct() {
        List<String> properties = Arrays.stream(DealGrainRow.class.getMethods())
                .map(method -> property(method.getName()))
                .toList();

        assertThat(properties.stream().map(AggregateUpsertArgumentsTest::number).distinct().toList())
                .as("одинаковый различитель у двух свойств пропустил бы их перестановку")
                .hasSize(properties.size());
    }

    @Test
    @DisplayName("Сделочное зерно: каждая величина строки уезжает в свою колонку")
    void everyDealValueReachesItsOwnColumn() {
        dealDataService.upsert(stub(DealGrainRow.class), BUCKET, ASSEMBLED);

        verify(dealRepository).upsert(text("tenantId"),
                text("exchangeAccountInternalId"),
                text("strategyInternalId"),
                BUCKET,
                text("resultCurrency"),
                number("closedDeals"),
                number("riskBearingDeals"),
                number("winningDeals"),
                number("losingDeals"),
                number("neutralDeals"),
                number("resultUnavailableDeals"),
                number("currencyUnresolvedDeals"),
                number("riskUnsizedDeals"),
                number("liquidatedDeals"),
                number("forcedReductionDeals"),
                number("outcomeUndeterminedDeals"),
                number("reconciliationMismatchedDeals"),
                number("reconciliationNotRunDeals"),
                number("breakdownIncompleteDeals"),
                number("breakdownNotAssessedDeals"),
                number("riskBenchmarkMissingDeals"),
                number("RDenominatorDeals"),
                amount("resultBeforeFundingSum"),
                amount("netResultSum"),
                amount("feeSum"),
                amount("fundingSum"),
                amount("liquidationPenaltySum"),
                amount("winResultSum"),
                amount("lossResultSum"),
                amount("plannedRiskSum"),
                amount("plannedRiskExcludedSum"),
                amount("RSum"),
                ASSEMBLED);
    }

    @Test
    @DisplayName("Зерно происшествий: каждый счётчик уезжает в свою колонку")
    void everyIncidentCounterReachesItsOwnColumn() {
        incidentDataService.upsert(stub(IncidentGrainRow.class), BUCKET, ASSEMBLED);

        verify(incidentRepository).upsert(text("tenantId"),
                text("exchangeAccountInternalId"),
                BUCKET,
                number("openedDeals"),
                number("orderDecisions"),
                number("raisedHolds"),
                number("hardRaisedHolds"),
                number("manuallyRaisedHolds"),
                number("anomalyReports"),
                number("criticalAnomalyReports"),
                number("manualOperationReports"),
                ASSEMBLED);
    }

    /**
     * Заглушка строки выдачи: отдаёт на каждый геттер значение, выведенное
     * из имени его свойства.
     */
    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> row) {
        return (T) Proxy.newProxyInstance(row.getClassLoader(),
                new Class<?>[]{row},
                (proxy, method, args) -> value(method));
    }

    private Object value(Method method) {
        if (Object.class.equals(method.getDeclaringClass())) {
            return "grain-row";
        }
        String property = property(method.getName());
        if (String.class.equals(method.getReturnType())) {
            return text(property);
        }
        if (Integer.class.equals(method.getReturnType())) {
            return number(property);
        }
        return amount(property);
    }

    private static String property(String getter) {
        return Introspector.decapitalize(getter.substring("get".length()));
    }

    private static String text(String property) {
        return "value-" + property;
    }

    private static Integer number(String property) {
        return Math.abs(property.hashCode() % 1_000_000) + 1;
    }

    private static BigDecimal amount(String property) {
        return BigDecimal.valueOf(number(property)).movePointLeft(3);
    }
}
