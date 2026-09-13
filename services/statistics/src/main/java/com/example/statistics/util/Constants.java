package com.example.statistics.util;

import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Константы сервиса (.claude/rules/codestyle.md §Константы).
 */
@UtilityClass
public class Constants {

    /**
     * Заголовки контекста вызова, которые ставит периметр.
     *
     * <p><b>Имя объявлено домом контекста</b>
     * (docs/architecture/contracts.md §«Контекст тенанта в вызове»), а не
     * этим классом: его читают обе стороны провода, и второе объявление
     * разошлось бы с первым (.claude/rules/carrier-levels.md). Здесь —
     * копия литерала, как и у построенного соседа с тем же заголовком;
     * расхождение её с домом ловится не чтением, а тем, что радиус отбора
     * стал бы пустым на каждом вызове сразу.
     */
    @UtilityClass
    public static class Header {

        /** Тенант контекста: радиус отбора обеих выборок чтения. */
        public static final String TENANT = "X-Tenant-Id";
    }

    /**
     * Имена полей конверта в заголовках сообщения.
     *
     * <p><b>Это ПЯТАЯ приватная копия имён, и она объявлена, а не
     * заведена по недосмотру.</b> Носителя имён в общей библиотеке не
     * заводится намеренно: он был бы вторым носителем формы, которую
     * задают сами поля конверта
     * (docs/architecture/contracts.md §«Носителя имён в общей библиотеке
     * не заводится, и это решение»). Цена копии названа там же, и охрана
     * у неё не чтение, а сквозной тест формы провода:
     * {@code WireFormContractTest} строит сообщение именами, которые
     * ставит построенный публикатор, и требует, чтобы потребитель нашёл
     * по ним все обязательные значения.
     *
     * <p><b>Тенанта здесь нет, и это несущая клауза.</b> Он едет
     * <b>ключом записи</b>, одноимённого заголовка на проводе нет вовсе —
     * потребитель, искавший бы его заголовком, не находил бы его на
     * КАЖДОМ сообщении, а не на редком.
     */
    @UtilityClass
    public static class EventHeaders {

        /** Идентичность события; она же ключ дедупа доставки. */
        public static final String EVENT_ID = "eventId";

        /** Класс события — дискриминатор содержимого. */
        public static final String EVENT_TYPE = "eventType";

        /** Версия формы содержимого; едет десятичным числом. */
        public static final String VERSION = "version";

        /** Момент происшествия; едет ISO-8601 со смещением. */
        public static final String OCCURRED_AT = "occurredAt";

        /** Контекст трассировки; законно отсутствует. */
        public static final String TRACE_CONTEXT = "traceContext";

        /**
         * Все имена, которые потребитель читает с провода. Перечень
         * существует затем, чтобы сквозной тест формы сравнивал его с
         * именами публикатора целиком, а не по одному.
         */
        public static final List<String> ALL = List.of(EVENT_ID, EVENT_TYPE, VERSION, OCCURRED_AT, TRACE_CONTEXT);
    }

    /**
     * Имена компонентов верхнего уровня содержимого, из которых
     * заполняются колонки радиуса отбора.
     *
     * <p><b>Вложенные структуры не разбираются</b>, и это не экономия:
     * механизм обязан быть исполним тем, кто формы содержимого не знает.
     * Не зная формы, читатель не может сказать, ЧЕЙ из встреченных на
     * глубине {@code internalId} перед ним, и подобрал бы чужое совпадение
     * (docs/models/domain/other/StatisticsFact.md §«Почему четыре колонки
     * идентичности, а не все»).
     */
    @UtilityClass
    public static class RadiusFields {

        /** Биржевой счёт. */
        public static final String EXCHANGE_ACCOUNT_INTERNAL_ID = "exchangeAccountInternalId";

        /** Инструмент. */
        public static final String INSTRUMENT_INTERNAL_ID = "instrumentInternalId";

        /** Сделка. */
        public static final String DEAL_INTERNAL_ID = "dealInternalId";

        /** Определение стратегии. */
        public static final String STRATEGY_INTERNAL_ID = "strategyInternalId";
    }

    /**
     * Имена рядов, которые сервис отдаёт наблюдателю, и метка их пары.
     *
     * <p><b>Имена — КОНТРАКТ С ПРАВИЛОМ АЛЕРТА, а не деталь экспортёра:</b>
     * их читает {@code PrometheusRule} манифеста сервиса
     * (deploy/base/services/audit.yaml), и переименование
     * здесь без правки там гасит алерт молча — правило перестаёт находить
     * ряд, а «нет ряда» на стороне Prometheus неотличимо от «нет
     * срабатывания». Общего носителя у Java и YAML не бывает, поэтому
     * охрана здесь не чтение, а проба: {@code AlertRuleContractTest}
     * читает манифест и требует, чтобы КАЖДОЕ имя, названное правилом,
     * встретилось в выдаче настоящего реестра.
     *
     * <p><b>Единица ряда — ПАРА «группа × тема», а метка одна.</b> Группа
     * у процесса одна, и её половина пары была бы одинаковым значением на
     * каждом ряду; сам процесс называют метки цели, которые ставит
     * наблюдатель (namespace, pod, job).
     *
     * <p><b>Миллисекунды, а не секунды.</b> Обе величины времени выводятся
     * из {@code retention.ms}, который брокер отдаёт в миллисекундах, и
     * правило сравнивает их ДРУГ С ДРУГОМ: перевод в секунды завёл бы два
     * деления и округление там, где предмет — равенство единиц у двух
     * рядов, а не соответствие соглашению об именах.
     */
    @UtilityClass
    public static class ReceptionMetrics {

        /** Возраст последнего принятого события пары. */
        public static final String LAST_EVENT_AGE = "statistics.reception.last.event.age.ms";

        /** Порог алерта на лаг пары — доля срока хранения её темы. */
        public static final String LAG_ALERT_THRESHOLD = "statistics.reception.lag.alert.threshold.ms";

        /** Остаток непринятого по паре в смещениях. */
        public static final String UNCONSUMED_RECORDS = "statistics.reception.unconsumed.records";

        /** Метка второй половины пары — тема производителя. */
        public static final String TOPIC_TAG = "topic";
    }

    /**
     * Имена, которыми клиент брокера называет свой лаг.
     *
     * <p><b>Берётся ровно {@code records-lag}, а не {@code records-lag-max}
     * и не {@code records-lag-avg}.</b> Первый — попартиционный и несёт
     * метку темы, из которой и складывается остаток ПАРЫ; второй на уровне
     * клиента метки темы не несёт вовсе, и суммирование по нему дало бы
     * величину по группе — ровно ту, против которой заведена потемность
     * (docs/spec/durable-reception.json, операнд {@code unconsumedRecords}).
     */
    @UtilityClass
    public static class ConsumerMetrics {

        /** Попартиционный лаг потребителя. */
        public static final String RECORDS_LAG = "records-lag";

        /** Метка темы у попартиционного лага. */
        public static final String TOPIC_TAG = "topic";
    }

    /**
     * Коды, которыми ручная поверхность метит СВОИ операции
     * (docs/rules/manual-halt.md — дом перечня).
     *
     * <p><b>Операнд несёт РАЗЛИЧЕНИЕ ДВУХ КЛАССОВ кода, а не литерал:</b>
     * счётчики ручной тропы у обоих зёрен спрашивают, принадлежит ли код
     * строки этому множеству (docs/spec/statistics-aggregates.json,
     * {@code codeIsManualOperation}).
     *
     * <p><b>Различает тропу именно КОД, а не актор строки.</b> Актор
     * отвечает на вопрос «кем порождён ход», и у джобы, запущенной ручным
     * триггером, он равен принципалу — по актору автоматическая
     * остановка, найденная в ручном прогоне детекции, попала бы в число
     * ручных, то есть разрез давал бы ровно ту ошибку, против которой
     * заведён.
     *
     * <p><b>Это КОПИЯ перечня, живущего у чужого производителя, и цена её
     * названа:</b> код, добавленный к ручной поверхности ядра, сюда сам не
     * приедет, и счётчик занизит число молча. Механической охраны у этого
     * нет — оживитель назван строкой реестра компонентов
     * (.claude/work/code-gate-ledger.json, компонент писателей событий
     * ядра).
     */
    @UtilityClass
    public static class ManualOperation {

        /** Постановка ступени ручной тропой. */
        public static final String HALT_REQUESTED = "MANUAL_HALT_REQUESTED";

        /** Снятие ступени ручной тропой. */
        public static final String HALT_CLEARED = "MANUAL_HALT_CLEARED";

        /**
         * Все коды ручной операции: перечень уезжает в запрос параметром,
         * а не литералом текста — носитель у него один.
         */
        public static final List<String> CODES = List.of(HALT_REQUESTED, HALT_CLEARED);
    }

    /**
     * Классы событий, несомые статистикой, и их раскладка по зёрнам
     * (docs/models/domain/other/StatisticsFact.md §«Признак несомого
     * класса»).
     *
     * <p><b>Класс несом ровно тогда, когда его содержимое несёт операнд
     * объявленного зерна</b>, и перечень поэтому ПРОИЗВОДЕН от зёрен, а не
     * назначен здесь. Этот блок — его отпечаток в коде: единицей подписки
     * в Kafka служит тема, а не класс, и отбор идёт у потребителя.
     *
     * <p><b>Это КОПИЯ перечня, живущего у чужого производителя, и цена её
     * названа</b> — та же, что у {@link ManualOperation}: класс,
     * переименованный у производителя, сюда сам не приедет, и событие
     * молча перестанет становиться фактом. Механической охраны у этого
     * нет; оживитель — строка реестра компонентов
     * (.claude/work/code-gate-ledger.json, компонент писателей событий
     * ядра).
     */
    @UtilityClass
    public static class CarriedEvent {

        /** Терминал сделки: единственный класс сделочного зерна. */
        public static final String DEAL_CLOSED = "DEAL_CLOSED";

        /** Открытие сделки. */
        public static final String DEAL_OPENED = "DEAL_OPENED";

        /** Решение о создании обычной заявки. */
        public static final String ORDER_DECIDED = "ORDER_DECIDED";

        /** Подъём ступени защиты. */
        public static final String HOLD_RAISED = "HOLD_RAISED";

        /** Отчёт о происшествии. */
        public static final String ANOMALY_REPORTED = "ANOMALY_REPORTED";

        /** Классы зерна происшествий: всякий несомый класс, кроме терминала. */
        public static final List<String> INCIDENT_CLASSES =
                List.of(DEAL_OPENED, ORDER_DECIDED, HOLD_RAISED, ANOMALY_REPORTED);
    }

    /**
     * Имена полей содержимого, из которых статистика достаёт операнды
     * зёрен.
     *
     * <p><b>Дом имён — форма содержимого у производителя класса</b>
     * (services/common/model/message); здесь стои́т их отпечаток, потому что
     * содержимое приезжает документом, а не типом. Цена та же, что у
     * перечня классов выше, и оживитель тот же.
     */
    @UtilityClass
    public static class ContentFields {

        /** Расчётная валюта результата сделки. */
        public static final String RESULT_CURRENCY = "resultCurrency";

        /** Сделка приняла риск. */
        public static final String TOOK_RISK = "tookRisk";

        /** Граф сделки полон. */
        public static final String GRAPH_COMPLETE = "graphComplete";

        /** Чистый результат сделки. */
        public static final String RESULT = "result";

        /** Комиссия. */
        public static final String FEE = "fee";

        /** Финансирование. */
        public static final String FUNDING = "funding";

        /** Штраф ликвидации. */
        public static final String LIQUIDATION_PENALTY = "liquidationPenalty";

        /** Плановый риск. */
        public static final String PLANNED_RISK = "plannedRisk";

        /** Исход закрытия. */
        public static final String CLOSE_OUTCOME = "closeOutcome";

        /** Состояние сверки P&amp;L. */
        public static final String RECONCILIATION_STATUS = "reconciliationStatus";

        /** Полнота разбивки движений средств. */
        public static final String BREAKDOWN_INCOMPLETE = "breakdownIncomplete";

        /** Доступность базы риска. */
        public static final String RISK_BENCHMARK_AVAILABILITY = "riskBenchmarkAvailability";

        /** Жёсткость поднятой ступени защиты. */
        public static final String RUNG = "rung";

        /** Критичность отчёта о происшествии. */
        public static final String SEVERITY = "severity";

        /** Код операции: им различается ручная тропа. */
        public static final String CODE = "code";
    }
}
