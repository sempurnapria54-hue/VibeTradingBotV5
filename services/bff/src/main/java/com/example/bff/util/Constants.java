package com.example.bff.util;

import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Константы периметра: имена заголовков и путей, которые читают обе
 * стороны провода (.claude/rules/codestyle.md §Константы).
 */
@UtilityClass
public class Constants {

    /** Заголовки контекста тенанта: имена объявлены контрактом, не сервисом. */
    @UtilityClass
    public static class ContextHeaders {

        /** Тенант вызова; выводится из членств предъявителя. */
        public static final String TENANT_ID = "X-Tenant-Id";

        /**
         * Роль в тенанте. Едет с первого дня и сегодня не читается никем
         * — заголовок заводится вместе с тропой, чтобы не переписывать её
         * при появлении второго субъекта.
         */
        public static final String TENANT_ROLE = "X-Tenant-Role";
    }

    /** Схема предъявления на исходящих вызовах периметра. */
    @UtilityClass
    public static class Authorization {

        /**
         * Префикс заголовка предъявления. Периметр пересобирает его из
         * ПРИНЯТОГО токена, а не переписывает исходный заголовок: так
         * дальше уезжает ровно то, что контур принял, и второго источника
         * истины о предъявителе не появляется.
         */
        public static final String BEARER_PREFIX = "Bearer ";
    }

    /** Поверхность периметра. */
    @UtilityClass
    public static class Paths {

        /** Общий префикс версии внешней поверхности. */
        public static final String API_V1 = "/api/v1";

        /**
         * Имя единицы периметра. Первым сегментом после версии оно
         * отличает СВОЮ точку от проксируемой — второго признака
         * различения не заводится.
         */
        public static final String PERIMETER_OWNER = "bff";

        /** Собственная поверхность периметра. */
        public static final String PERIMETER_ROOT = API_V1 + "/" + PERIMETER_OWNER;

        /** Открытая точка контура — проба живости. */
        public static final String HEALTH = "/actuator/health/**";

        /**
         * Вторая открытая точка — съём метрик наблюдателем окружения
         * (docs/rules/api-access-policy.md §«Съём метрик — второе
         * исключение»). Путь поимённый, а не префикс актуатора.
         */
        public static final String PROMETHEUS = "/actuator/prometheus";

        /**
         * Форма имени владельца: строчные, цифры и дефис. Охрана
         * СОБСТВЕННАЯ, а не только сетевой политикой кластера — иначе
         * свойство периметра держалось бы конфигурацией среды, в которой
         * он запущен.
         */
        public static final String OWNER_NAME_FORM = "[a-z0-9-]+";
    }

    /** Заголовки соединения, которые пересылка владельцу браузеру не отдаёт. */
    @UtilityClass
    public static class ConnectionHeaders {

        /**
         * Заголовки соединения (hop-by-hop, RFC 9110 §7.6.1): описывают
         * соединение владельца с периметром, а не ответ, и дальше него не
         * уезжают. Ответ браузеру собирает контейнер периметра своим
         * соединением.
         */
        public static final Set<String> HOP_BY_HOP = Set.of(
                "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
                "te", "trailer", "transfer-encoding", "upgrade");
    }

    /** Заголовки протокола потока в браузер. */
    @UtilityClass
    public static class StreamHeaders {

        /**
         * Идентичность последнего полученного события. Браузер шлёт её
         * сам, пока переподключается своя подписка; непустая — старше
         * параметра адреса {@link StreamParameters#LAST_EVENT_ID}.
         */
        public static final String LAST_EVENT_ID = "Last-Event-ID";
    }

    /** Параметры адреса подписки на поток в браузер. */
    @UtilityClass
    public static class StreamParameters {

        /**
         * Идентичность последнего полученного события при ПЕРЕСОЗДАНИИ
         * подписки: браузерный {@code EventSource} заголовков не ставит, и
         * клиент передаёт её адресом рядом с билетом. Непустой заголовок
         * {@link StreamHeaders#LAST_EVENT_ID} её старше.
         */
        public static final String LAST_EVENT_ID = "lastEventId";
    }

    /** Имена полей конверта события в заголовках сообщения. */
    @UtilityClass
    public static class EventHeaders {

        /** Идентичность события; она же идентичность записи в потоке. */
        public static final String EVENT_ID = "eventId";

        /** Класс события. */
        public static final String EVENT_TYPE = "eventType";

        /** Момент происшествия. */
        public static final String OCCURRED_AT = "occurredAt";
    }

    /** Классы записей, которые периметр порождает сам. */
    @UtilityClass
    public static class StreamRecords {

        /** Пульс: молчание без него — наблюдаемый отказ. */
        public static final String PULSE = "PERIMETER_PULSE";

        /** Явный разрыв: поток продолжен не с той позиции, что просил клиент. */
        public static final String GAP = "PERIMETER_GAP";

        /**
         * Текст комментария протокола, которым подписка подтверждает
         * открытие. Записью он не является: класса не несёт, в окно не
         * кладётся, и клиент протокола его не показывает.
         */
        public static final String OPENED_COMMENT = "PERIMETER_OPENED";
    }

    /** Ряд частоты отказов доступа — след отказа у периметра без базы. */
    @UtilityClass
    public static class AccessDenialMetrics {

        /** Счётчик отказов доступа на поверхности периметра. */
        public static final String DENIALS = "perimeter.access.denials";

        /** Метка класса отказа: ряд на класс, чтобы серию было видно по классу. */
        public static final String OUTCOME_TAG = "outcome";

        /** Класс отказа: принципал не предъявлен либо предъявленный не принят. */
        public static final String PRINCIPAL_ABSENT = "PRINCIPAL_ABSENT";

        /** Класс отказа: принципал принят, операция ему не разрешена. */
        public static final String OPERATION_FORBIDDEN = "OPERATION_FORBIDDEN";
    }

    /** Имена, которыми клиент брокера называет связь с координатором группы. */
    @UtilityClass
    public static class ConsumerMetrics {

        /**
         * Возраст последнего сердцебиения координатору группы, в секундах;
         * {@code -1}, пока клиент не послал ни одного.
         */
        public static final String LAST_HEARTBEAT_SECONDS_AGO = "last-heartbeat-seconds-ago";

        /**
         * Возраст последней успешной ребалансировки группы, в секундах;
         * {@code -1}, пока участник не вступил в группу ни разу.
         */
        public static final String LAST_REBALANCE_SECONDS_AGO = "last-rebalance-seconds-ago";
    }
}
