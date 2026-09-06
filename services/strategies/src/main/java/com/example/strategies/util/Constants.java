package com.example.strategies.util;

import lombok.experimental.UtilityClass;

/**
 * Константы сервиса: один класс, темы — вложенными классами
 * (.claude/rules/codestyle.md §Константы).
 */
@UtilityClass
public class Constants {

    /** Заголовки исходящих вызовов. */
    @UtilityClass
    public class Header {

        /**
         * Префикс схемы bearer. Константа, а не литерал в каждом клиенте:
         * опечатка в нём даёт отказ идентичности на стороне соседа.
         */
        public static final String BEARER_PREFIX = "Bearer ";

        /**
         * Заголовок контекста тенанта во входящем вызове.
         *
         * <p>Контекст несёт тенанта и роль
         * (docs/architecture/contracts.md §«Контекст тенанта в вызове»);
         * принятый из ТЕЛА тенант был бы объявлением вызывающего о самом
         * себе, а сверка контекста с членствами идёт по заголовку.
         */
        public static final String TENANT = "X-Tenant-Id";
    }

    /** Темы, в которые публикует владелец определений. */
    @UtilityClass
    public class Topic {

        /**
         * Тема фактов производителя: имя выводится правилом
         * «производитель и род» (docs/architecture/contracts.md §«Событие
         * → тема»), а род у всех его классов один — это происшествия.
         */
        public static final String FACTS = "strategies.facts";
    }
}
