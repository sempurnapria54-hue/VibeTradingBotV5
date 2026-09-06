package com.example.bff.config;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Оси окружения периметра.
 *
 * <p>Каждое поле — величина, которую задаёт манифест окружения, а не
 * код: адрес владельца, сроки, размеры окон, секрет подписи. Умолчания
 * выбраны так, чтобы незаданное означало отказ, а не разрешение
 * (пустой секрет билета подписку не открывает вовсе).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "perimeter")
public class PerimeterProperties {

    /**
     * Шаблон адреса владельца с плейсхолдером {@code {owner}}. Адресат
     * выводится из первого сегмента пути, и перечня владельцев периметр
     * не держит: пара, которой нет в
     * {@code docs/architecture/contracts.md} §«Синхронные вызовы», в
     * кластере запрещена сетевой политикой — она и есть энфорсер набора.
     */
    private String ownerUrlTemplate;

    /**
     * Бюджет повторов ЧТЕНИЯ при отказе транспорта. Мутирующий запрос не
     * повторяется никогда, и на него это число не действует.
     */
    private Integer readRetries;

    /** Кэш членств. */
    private Membership membership = new Membership();

    /** Поток живых данных в браузер. */
    private Stream stream = new Stream();

    /** Билет подписки. */
    private Ticket ticket = new Ticket();

    /** Кэш членств: эфемерный, со сроком годности. */
    @Getter
    @Setter
    public static class Membership {

        /**
         * Срок годности записи. Устаревшая запись ошибается в
         * разрешающую сторону, поэтому срок короткий.
         */
        private Duration cacheTtl;
    }

    /** Поток живых данных. */
    @Getter
    @Setter
    public static class Stream {

        /** Темы, у которых есть производитель; тем без него здесь нет. */
        private List<String> topics;

        /**
         * Сколько последних событий тенанта держится в памяти реплики
         * для продолжения потока по идентичности последнего события.
         */
        private Integer replayWindow;

        /** Период пульса: молчание без него — наблюдаемый отказ. */
        private Duration pulseInterval;

        /**
         * Выключатель тика пульса. При {@code false} тик ничего не
         * делает — как у всякой джобы (.claude/rules/codestyle.md
         * §Джобы).
         */
        private Boolean pulseEnabled = Boolean.TRUE;

        /** Срок жизни соединения подписки. */
        private Duration connectionTimeout;

        /**
         * Потолок одновременных подписок на тенанта. Держит память
         * реплики конечной: подписки живут в ней, и число их задаёт не
         * наш пользователь, а тот, кто их открывает.
         */
        private Integer maxSubscriptionsPerTenant;
    }

    /** Билет подписки: вторая форма предъявления на тропе потока. */
    @Getter
    @Setter
    public static class Ticket {

        /**
         * Секрет подписи, ОБЩИЙ у реплик: подписка может прийти не на ту
         * реплику, что выдала билет. Пустое — выдача не настроена.
         */
        private String secret;

        /**
         * Срок билета. Проверяется при открытии подписки; установленный
         * поток по его истечении не рвётся.
         */
        private Duration ttl;
    }
}
