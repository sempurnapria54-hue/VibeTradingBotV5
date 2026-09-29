package com.example.bff.config;

import static java.util.Objects.isNull;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Оси окружения периметра.
 *
 * <p>Каждое поле — величина, которую задаёт манифест окружения, а не
 * код: адрес владельца, сроки, размеры окон, секрет подписи.
 *
 * <p><b>Незаданное означает отказ, а не разрешение, и держит это код, а
 * не умолчание своей конфигурации.</b> Величина, без которой поверхность
 * деградирует молча — пустой либо нулевой срок, окно, потолок, — отвечает
 * отказом ПОДЪЁМА с именем оси: ноль окна обратил бы всякое
 * переподключение в разрыв, ноль потолка отверг бы всякое открытие, и
 * промах конфигурации браузер увидел бы деградацией, а не отказом.
 * Две оси выражают отказ иначе, и оба исхода названы: пустой секрет
 * билета — отказ выдачи билетов при поднятой поверхности, пустой перечень
 * тем — отсутствие слушателя ({@link StreamConsumptionCondition}).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = PerimeterProperties.PREFIX)
public class PerimeterProperties {

    /** Корень ключей осей периметра. */
    public static final String PREFIX = "perimeter";

    /**
     * Шаблон адреса владельца с плейсхолдером {@code {owner}}. Адресат
     * выводится из первого сегмента пути, и перечня владельцев периметр
     * не держит: пара, которой нет в
     * {@code docs/architecture/contracts.md} §«Синхронные вызовы», в
     * кластере запрещена сетевой политикой — она и есть энфорсер набора.
     */
    @NotBlank
    private String ownerUrlTemplate;

    /**
     * Бюджет повторов ЧТЕНИЯ при отказе транспорта. Мутирующий запрос не
     * повторяется никогда, и на него это число не действует.
     */
    @NotNull
    @PositiveOrZero
    private Integer readRetries;

    /** Кэш членств. */
    @Valid
    private Membership membership = new Membership();

    /** Поток живых данных в браузер. */
    @Valid
    private Stream stream = new Stream();

    /** Билет подписки. */
    @Valid
    private Ticket ticket = new Ticket();

    /** Кэш членств: эфемерный, со сроком годности. */
    @Getter
    @Setter
    public static class Membership {

        /**
         * Срок годности записи. Устаревшая запись ошибается в
         * разрешающую сторону, поэтому срок короткий.
         */
        @NotNull
        @DurationMin(nanos = 1)
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
        @NotNull
        @Positive
        private Integer replayWindow;

        /** Период пульса: молчание без него — наблюдаемый отказ. */
        @NotNull
        @DurationMin(nanos = 1)
        private Duration pulseInterval;

        /**
         * Выключатель тика пульса. При {@code false} тик ничего не
         * делает — как у всякой джобы (.claude/rules/codestyle.md
         * §Джобы).
         */
        private Boolean pulseEnabled = Boolean.TRUE;

        /** Срок жизни соединения подписки. */
        @NotNull
        @DurationMin(nanos = 1)
        private Duration connectionTimeout;

        /**
         * Потолок одновременных подписок на тенанта. Держит память
         * реплики конечной: подписки живут в ней, и число их задаёт не
         * наш пользователь, а тот, кто их открывает.
         */
        @NotNull
        @Positive
        private Integer maxSubscriptionsPerTenant;

        /**
         * Имена тем подписки. Пустое имя темой не является — его даёт
         * незаданная ось либо разделитель на краю, — и в перечень оно не
         * попадает: пустой перечень есть отсутствие подписки, а не
         * подписка на тему без имени.
         */
        public List<String> topicNames() {
            if (isNull(topics)) {
                return new ArrayList<>();
            }
            return topics.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
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
         * поток по его истечении не рвётся. Он же — срок, который окно
         * переигрывания тенанта переживает его последнюю подписку.
         */
        @NotNull
        @DurationMin(nanos = 1)
        private Duration ttl;
    }
}
