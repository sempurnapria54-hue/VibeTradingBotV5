package com.example.statistics.box;

/**
 * Содержимое событий, которые кейсы кладут в тему.
 *
 * <p><b>Содержимое подаётся ДОСЛОВНО, а не собирается формой
 * производителя.</b> На проводе едет документ, и разбирает его чтец конверта
 * по ИМЕНАМ полей, лежащим у потребителя приватной копией
 * ({@code Constants.ContentFields}) — кейс проверяет именно их. Собранное
 * типизованной формой соседа, содержимое приезжало бы уже причёсанным
 * сериализатором, то есть клетка проверяла бы наш сборщик.
 *
 * <p><b>Значения перечней — СЛОВА чужого производителя, и это несущее.</b>
 * Исход закрытия, состояние сверки, полнота разбивки, доступность базы риска,
 * жёсткость ступени и критичность отчёта сравниваются у потребителя с
 * литералами; общей библиотеки этих перечней в дереве статистики нет вовсе
 * (docs/models/domain/other/StatisticsFact.md §«Признак несомого класса»).
 * Слова здесь взяты у домов перечней — {@code Deal.CloseOutcome},
 * {@code Deal.ReconciliationStatus}, {@code Deal.BreakdownCompleteness},
 * {@code Deal.RiskBenchmarkAvailability}, {@code HoldRung},
 * {@code AnomalyReport.Severity}, код ручной операции — у правила ручной
 * остановки (docs/rules/manual-halt.md), — а не выдуманы: выдуманное слово дало
 * бы зелёную клетку на пустом счётчике.
 *
 * <p><b>Денежные операнды едут ТЕКСТОМ десятичной записи.</b> Так они и
 * приезжают с провода, и читает их чтец конверта строкой, а не двоичным типом
 * узла (docs/rules/decimal-arithmetic.md).
 */
final class Bodies {

    /** Расчётная валюта результата у штатных содержимых. */
    static final String CURRENCY = "USDT";

    /** Чистый результат штатного содержимого. */
    static final String NET_RESULT = "12.500000000000000000";

    /** Накопленное финансирование штатного содержимого: издержкой отрицательное. */
    static final String FUNDING = "-0.250000000000000000";

    /** Комиссия штатного содержимого. */
    static final String FEE = "0.400000000000000000";

    /** Штраф принудительного закрытия штатного содержимого. */
    static final String LIQUIDATION_PENALTY = "0.000000000000000000";

    /** Плановый риск штатного содержимого: знаменатель отношения к риску. */
    static final String PLANNED_RISK = "5.000000000000000000";

    /** Исход закрытия, ни одним разрезом не считаемый. */
    static final String NORMAL_EXIT = "NORMAL_EXIT";

    /** Жёсткость ступени, которую считает разрез жёстких. */
    static final String HARD = "HARD";

    /** Критичность отчёта, которую считает разрез критичных. */
    static final String CRITICAL = "CRITICAL";

    /**
     * Код операции ручной постановки ступени.
     *
     * <p>Взят у дома перечня (docs/rules/manual-halt.md), а не выдуман: ручную
     * тропу различает именно КОД, и выдуманное слово дало бы зелёную клетку на
     * пустом счётчике.
     */
    static final String MANUAL_HALT_REQUESTED = "MANUAL_HALT_REQUESTED";

    private Bodies() {
    }

    /**
     * Штатное содержимое терминала сделки: все ключи зерна и все операнды.
     *
     * @param exchangeAccount биржевой счёт — обязательный ключ зерна
     * @param strategy        определение стратегии; пусто законно, но здесь есть
     */
    static String dealClosed(String exchangeAccount, String strategy) {
        return """
                {"exchangeAccountInternalId": "%s",
                 "strategyInternalId": "%s",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": %s,
                 "fee": %s,
                 "funding": %s,
                 "liquidationPenalty": %s,
                 "plannedRisk": %s,
                 "closeOutcome": "%s",
                 "reconciliationStatus": "MATCHED",
                 "breakdownIncomplete": "COMPLETE",
                 "riskBenchmarkAvailability": "AVAILABLE"}"""
                .formatted(exchangeAccount, strategy, CURRENCY, NET_RESULT, FEE, FUNDING,
                        LIQUIDATION_PENALTY, PLANNED_RISK, NORMAL_EXIT);
    }

    /**
     * Штатное содержимое терминала плюс поле {@code tenantId} в ТЕЛЕ.
     *
     * <p>Им ставится третий претендент на тенанта: ключ записи, заголовок и
     * содержимое называют разные значения, а победить обязан ключ записи.
     *
     * @param exchangeAccount биржевой счёт
     * @param tenantId        значение, которое лежит в содержимом
     */
    static String dealClosedWithTenantField(String exchangeAccount, String tenantId) {
        return """
                {"tenantId": "%s",
                 "exchangeAccountInternalId": "%s",
                 "strategyInternalId": "S-1",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": %s,
                 "closeOutcome": "%s"}"""
                .formatted(tenantId, exchangeAccount, CURRENCY, NET_RESULT, NORMAL_EXIT);
    }

    /**
     * Содержимое терминала БЕЗ определения стратегии: ключ законно пуст.
     *
     * @param exchangeAccount биржевой счёт
     */
    static String dealClosedWithoutStrategy(String exchangeAccount) {
        return """
                {"exchangeAccountInternalId": "%s",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": %s,
                 "fee": %s,
                 "funding": %s,
                 "liquidationPenalty": %s,
                 "plannedRisk": %s,
                 "closeOutcome": "%s",
                 "reconciliationStatus": "MATCHED",
                 "breakdownIncomplete": "COMPLETE",
                 "riskBenchmarkAvailability": "AVAILABLE"}"""
                .formatted(exchangeAccount, CURRENCY, NET_RESULT, FEE, FUNDING,
                        LIQUIDATION_PENALTY, PLANNED_RISK, NORMAL_EXIT);
    }

    /**
     * Содержимое терминала, у которого ключи зерна лежат на ГЛУБИНЕ, а на
     * верхнем уровне одноимённых компонентов нет.
     *
     * @param exchangeAccount биржевой счёт внутри вложенного объекта
     * @param strategy        определение стратегии внутри того же объекта
     */
    static String dealClosedNestedKeys(String exchangeAccount, String strategy) {
        return """
                {"payload": {"exchangeAccountInternalId": "%s", "strategyInternalId": "%s"},
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": %s,
                 "closeOutcome": "%s"}"""
                .formatted(exchangeAccount, strategy, CURRENCY, NET_RESULT, NORMAL_EXIT);
    }

    /**
     * Содержимое терминала БЕЗ денежных операндов: ключи зерна и признаки на
     * месте, мер нет ни одной.
     *
     * @param exchangeAccount биржевой счёт
     */
    static String dealClosedWithoutMeasures(String exchangeAccount) {
        return """
                {"exchangeAccountInternalId": "%s",
                 "strategyInternalId": "S-1",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "closeOutcome": "%s",
                 "reconciliationStatus": "MATCHED",
                 "breakdownIncomplete": "COMPLETE",
                 "riskBenchmarkAvailability": "AVAILABLE"}"""
                .formatted(exchangeAccount, CURRENCY, NORMAL_EXIT);
    }

    /**
     * Содержимое терминала с НАЗВАННЫМИ денежными операндами.
     *
     * <p>Ими ставится вход клетки о десятичной записи: значения выбирает
     * клетка, потому что предмет её — что они доезжают и складываются
     * дословно.
     *
     * @param exchangeAccount биржевой счёт
     * @param netResult       чистый результат десятичной записью
     * @param funding         накопленное финансирование десятичной записью
     * @param fee             комиссия десятичной записью
     */
    static String dealClosedWithMeasures(String exchangeAccount, String netResult,
                                         String funding, String fee) {
        return """
                {"exchangeAccountInternalId": "%s",
                 "strategyInternalId": "S-1",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": %s,
                 "fee": %s,
                 "funding": %s,
                 "liquidationPenalty": %s,
                 "plannedRisk": %s,
                 "closeOutcome": "%s",
                 "reconciliationStatus": "MATCHED",
                 "breakdownIncomplete": "COMPLETE",
                 "riskBenchmarkAvailability": "AVAILABLE"}"""
                .formatted(exchangeAccount, CURRENCY, netResult, fee, funding,
                        LIQUIDATION_PENALTY, PLANNED_RISK, NORMAL_EXIT);
    }

    /**
     * Содержимое терминала с НАЗВАННОЙ валютой и НАЗВАННЫМ результатом.
     *
     * <p>Им ставится вход клетки о повторе с иным содержимым: отличаться
     * обязаны ровно те операнды, о которых кейс говорит, — результат и
     * расчётная валюта.
     *
     * @param exchangeAccount биржевой счёт
     * @param currency        расчётная валюта результата
     * @param netResult       чистый результат десятичной записью
     */
    static String dealClosedWith(String exchangeAccount, String currency, String netResult) {
        return """
                {"exchangeAccountInternalId": "%s",
                 "strategyInternalId": "S-1",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": %s,
                 "fee": %s,
                 "funding": %s,
                 "liquidationPenalty": %s,
                 "plannedRisk": %s,
                 "closeOutcome": "%s",
                 "reconciliationStatus": "MATCHED",
                 "breakdownIncomplete": "COMPLETE",
                 "riskBenchmarkAvailability": "AVAILABLE"}"""
                .formatted(exchangeAccount, currency, netResult, FEE, FUNDING,
                        LIQUIDATION_PENALTY, PLANNED_RISK, NORMAL_EXIT);
    }

    /**
     * Содержимое терминала, несущее вложенные объекты, массивы и поля, которых
     * нет ни среди ключей зерна, ни среди операндов.
     *
     * @param exchangeAccount биржевой счёт
     */
    static String dealClosedRichDocument(String exchangeAccount) {
        return """
                {"exchangeAccountInternalId": "%s",
                 "strategyInternalId": "S-1",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": %s,
                 "closeOutcome": "%s",
                 "nested": {"level": {"kept": true}},
                 "series": [1, 2, 3],
                 "objects": [{"a": 1}, {"b": 2}],
                 "note": "стратегия «альфа» — вход"}"""
                .formatted(exchangeAccount, CURRENCY, NET_RESULT, NORMAL_EXIT);
    }

    /**
     * Содержимое терминала БЕЗ биржевого счёта: обязательного ключа зерна нет
     * ни на одном уровне.
     *
     * <p>Отсутствие здесь буквальное, а не пустое значение: пустота от
     * отсутствия отличается (docs/rules/absent-value-semantics.md), и вторую
     * сторону берёт своя клетка.
     */
    static String dealClosedWithoutAccount() {
        return """
                {"strategyInternalId": "S-1",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": %s,
                 "closeOutcome": "%s"}"""
                .formatted(CURRENCY, NET_RESULT, NORMAL_EXIT);
    }

    /** Содержимое подъёма ступени БЕЗ биржевого счёта — того же ключа зерна. */
    static String holdRaisedWithoutAccount() {
        return """
                {"rung": "%s", "code": "%s"}""".formatted(HARD, MANUAL_HALT_REQUESTED);
    }

    /**
     * Обрывок текста, документом не являющийся вовсе.
     *
     * <p><b>Оборван он посреди объекта намеренно.</b> Скаляр — число, слово,
     * {@code true} — разбирается как документ успешно, и клетка о
     * неразбираемом теле мерила бы тогда другую ветвь (пробел покрытия
     * {@code G5} документа кейсов).
     */
    static String notADocument() {
        return "{\"exchangeAccountInternalId\":";
    }

    /**
     * Содержимое терминала, чей денежный операнд записан НЕЧИСЛОВОЙ строкой.
     *
     * <p>Ключи зерна при этом на месте: предмет клетки — что испорченный
     * операнд роняет обработку, а не подменяется пустотой.
     *
     * @param exchangeAccount биржевой счёт
     * @param result          чистый результат — записью, числом не являющейся
     */
    static String dealClosedWithNonNumericResult(String exchangeAccount, String result) {
        return """
                {"exchangeAccountInternalId": "%s",
                 "strategyInternalId": "S-1",
                 "resultCurrency": "%s",
                 "tookRisk": true,
                 "graphComplete": true,
                 "result": "%s",
                 "closeOutcome": "%s"}"""
                .formatted(exchangeAccount, CURRENCY, result, NORMAL_EXIT);
    }

    /**
     * Содержимое происшествия, у которого из разрезов нет ни одного: только
     * обязательный ключ зерна.
     *
     * @param exchangeAccount биржевой счёт
     */
    static String incident(String exchangeAccount) {
        return """
                {"exchangeAccountInternalId": "%s", "dealInternalId": "D-1"}"""
                .formatted(exchangeAccount);
    }

    /**
     * Содержимое подъёма ступени: ступень и код есть, критичности нет.
     *
     * @param exchangeAccount биржевой счёт
     * @param rung            жёсткость поднятой ступени
     * @param code            код операции; пусто означает «тропа не ручная»
     */
    static String holdRaised(String exchangeAccount, String rung, String code) {
        return """
                {"exchangeAccountInternalId": "%s", "rung": "%s", "code": "%s"}"""
                .formatted(exchangeAccount, rung, code);
    }

    /**
     * Содержимое отчёта о происшествии: критичность и код есть, ступени нет.
     *
     * @param exchangeAccount биржевой счёт
     * @param severity        критичность отчёта
     * @param code            код операции
     */
    static String anomalyReported(String exchangeAccount, String severity, String code) {
        return """
                {"exchangeAccountInternalId": "%s", "severity": "%s", "code": "%s"}"""
                .formatted(exchangeAccount, severity, code);
    }
}
