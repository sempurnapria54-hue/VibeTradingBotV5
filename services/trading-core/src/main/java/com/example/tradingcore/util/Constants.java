package com.example.tradingcore.util;

import lombok.experimental.UtilityClass;

/**
 * Константы торгового ядра: величины, осмысленные больше чем в одном
 * классе. Один класс-дом с вложенными по теме
 * (.claude/rules/codestyle.md §Константы).
 */
@UtilityClass
public class Constants {

    /**
     * Аудит записи торговых строк.
     */
    @UtilityClass
    public class Audit {

        /**
         * Автор записи торговых строк. Строки пишут проходы оркестрации и
         * исполнители, у которых пользователя нет по построению.
         *
         * <p>Константа вынесена в дом, потому что читателей у неё два:
         * поставщик JPA-аудита и БЕЗОПАСНАЯ ВСТАВКА ПО КЛЮЧУ, которая
         * идёт нативным запросом и слушателей аудита не проходит вовсе
         * (docs/rules/idempotency-via-unique.md).
         */
        public static final String WRITER = "trading-core";
    }

    /**
     * Машинные коды причин, с которыми поднимается сигнал ступени либо
     * заводится журнальная строка.
     *
     * <p><b>Закрытого реестра кодов нет</b>, и здесь он не заводится: код
     * приходит от правила, которое ступень запросило, и дом каждого
     * значения — дом его тропы
     * (docs/components/models/HoldSignal.md §«Значения `code` заводят
     * тропы-производители»). Класс держит только те, что уже названы
     * своими домами.
     */
    @UtilityClass
    public class Hold {

        /**
         * База риска счёта не наблюдена: снимок средств приземлился, а
         * остаток расчётной валюты неположителен либо не резолвился. Дом
         * кода — docs/components/RefreshBalanceExecutor.md §«Первое
         * наблюдение базы риска».
         */
        public static final String RISK_BASE_NOT_OBSERVED = "RISK_BASE_NOT_OBSERVED";

        /**
         * Данные шага стратегии недоступны, и объявленная им реакция —
         * аварийная: живой риск снимается килл-свичем, а не выводится
         * закрывающими действиями. Ступень жёсткая, радиус — инструмент.
         * Дом кода — docs/rules/market-data-freshness.md §«Что
         * ограничивает», форма ступени — docs/rules/instrument-hold.md.
         */
        public static final String INSTRUMENT_MARKET_DATA_EXPIRED = "INSTRUMENT_MARKET_DATA_EXPIRED";

        /**
         * Живой риск транша не покрыт и обязательства покрытия у него нет:
         * сломался НАШ собственный учёт покрытия риска, и радиус доверия к
         * нему неизвестен. Ступень счётная и жёсткая. Дом кода и реакции —
         * docs/rules/live-risk-protection.md §«Реакция на непокрытый риск».
         */
        public static final String EXCHANGE_LIVE_RISK_UNCOVERED = "EXCHANGE_LIVE_RISK_UNCOVERED";

        /**
         * Площадка ответила контролируемым отказом: внешний факт получен
         * (или должен был быть найден), а продолжать нормальный проход
         * небезопасно. Ступень счётная и жёсткая, и она БЕЗУСЛОВНА:
         * истинный радиус поражения неизвестен, поэтому тормозим
         * консервативно. Дом кода и реакции —
         * docs/rules/controlled-exchange-exceptions.md §«Реакция —
         * безусловная биржевая ступень 2».
         */
        public static final String EXCHANGE_CONTROLLED_FAILURE = "EXCHANGE_CONTROLLED_FAILURE";

        /**
         * Источник отверг наши ключи: подпись не принята, ключ отозван,
         * passphrase не та, доступ приостановлен. Ступень счётная и
         * жёсткая: сопровождать принятый риск нечем — подтвердить нечем и
         * починить нечем, — значит он подлежит снятию. Дом кода —
         * docs/rules/exchange-hold.md §«Ступень 2 — сворачивание», п. 5.
         */
        public static final String EXCHANGE_CREDENTIALS_REJECTED = "EXCHANGE_CREDENTIALS_REJECTED";

        /**
         * Строка исполнения израсходовала бюджет попыток при
         * ПОДТВЕРЖДЁННОМ покрытии принятого риска: «мы не смогли
         * дозвониться», а не «площадка отвергла». Ступень инструментная и
         * мягкая — рвать покрытый риск нечем. Дом кода и формы —
         * docs/rules/instrument-hold.md §«Форма реакции на исчерпание
         * бюджета попыток».
         */
        public static final String INSTRUMENT_RETRY_BUDGET_EXHAUSTED =
                "INSTRUMENT_RETRY_BUDGET_EXHAUSTED";

        /**
         * Ручная постановка ступени держателем. Ни одна автоматическая
         * тропа этого кода не поднимает; направление операции ничем, кроме
         * кода, не наблюдаемо — у постановки и снятия одна и та же пара
         * «радиус × ступень». Дом кода — docs/rules/manual-halt.md
         * §«Наблюдаемость: ручное отличимо и от автоматики, и друг от
         * друга».
         */
        public static final String MANUAL_HALT_REQUESTED = "MANUAL_HALT_REQUESTED";

        /**
         * Ручное снятие ступени держателем — единственный выход из холда:
         * автоматического снятия нет ни у одного основания. Природа факта
         * — ПРОИСШЕСТВИЕ: каждое снятие обязано дать свою строку, иначе
         * холд, поднятый и снятый трижды, оставил бы один след. Дом кода —
         * там же.
         */
        public static final String MANUAL_HALT_CLEARED = "MANUAL_HALT_CLEARED";

        /**
         * Живая сущность по инструменту, которого в контуре нет вовсе.
         * Восстановление здесь недостижимо — строки инструмента нет, —
         * поэтому риск не может быть приписан ничему. Ступень счётная и
         * жёсткая. Дом кода — docs/rules/exchange-hold.md §«Ступень 2 —
         * сворачивание», п. 3.
         */
        public static final String EXCHANGE_FOREIGN_INSTRUMENT_RISK = "EXCHANGE_FOREIGN_INSTRUMENT_RISK";

        /**
         * Позиций по одному инструменту больше одной: режим позиций счёта
         * не тот, который объявлен adapter-константой. Ступень счётная и
         * жёсткая. Дом кода — docs/rules/exchange-hold.md §«Ступень 2 —
         * сворачивание», п. 1.
         */
        public static final String EXCHANGE_POSITION_MODE_VIOLATION = "EXCHANGE_POSITION_MODE_VIOLATION";

        /**
         * Живая заявка либо algo без нашего маркера: счётом распоряжается
         * кто-то ещё, и доверять собственному покрытию оснований нет.
         * Ступень счётная и жёсткая. Дом кода — docs/rules/exchange-hold.md
         * §«Ступень 2 — сворачивание», п. 3.
         */
        public static final String EXCHANGE_FOREIGN_ORDER = "EXCHANGE_FOREIGN_ORDER";

        /**
         * Сумма gross-экспозиций траншей разошлась с нетто-размером живого
         * эпизода: наш счёт экспозиции разошёлся с биржей. Ступень счётная
         * и жёсткая. Дом кода — docs/models/domain/aggregate/Deal.md
         * §«Экспозиция сделки и сверка с биржей».
         */
        public static final String EXCHANGE_EXPOSURE_MISMATCH = "EXCHANGE_EXPOSURE_MISMATCH";

        /**
         * Жёсткая ступень радиуса стои́т, а сущности радиуса на бирже живы.
         * Ступени детектор не запрашивает вовсе — она уже стои́т, —
         * реакция журнальная. Дом кода —
         * docs/components/SafetyHoldCoordinator.md §«Поглощённый сигнал
         * наблюдаем».
         */
        public static final String SAFETY_RUNG_NOT_ENFORCED = "SAFETY_RUNG_NOT_ENFORCED";

        /**
         * Наша строка терминальна, а сущность на бирже жива. Реакция
         * журнальная, блокировки в составе нет. Дом кода —
         * docs/rules/error-handling-policy.md §«Перечень реакций».
         */
        public static final String LOCAL_TERMINAL_ALIVE_ON_EXCHANGE = "LOCAL_TERMINAL_ALIVE_ON_EXCHANGE";

        /**
         * Позиции по инструменту нет, а заявки живут, и живая сделка их не
         * объясняет. Живого направленного риска у хвоста нет, снимать
         * нечего — ступень мягкая, инструментная. Дом кода —
         * docs/rules/instrument-hold.md §Триггеры.
         */
        public static final String INSTRUMENT_ORPHAN_ORDERS = "INSTRUMENT_ORPHAN_ORDERS";

        /**
         * Живая сделка перестала укладываться в потолки, хотя её защита
         * стои́т и подтверждается. Ступень мягкая: принятый риск покрыт, и
         * рвать его нечем. Дом кода — docs/rules/instrument-hold.md
         * §«Форма реакции на нарушение риск-политики при живой защите».
         */
        public static final String RISK_POLICY_BREACH_UNDER_PROTECTION =
                "RISK_POLICY_BREACH_UNDER_PROTECTION";

        /**
         * Проход проактивной детекции добыт не целиком либо детекция по
         * нему не отработала. Первый же такой проход заводит отчёт;
         * серия подряд идущих поднимает МЯГКУЮ счётную ступень: биржевая
         * защита продолжает стоять, пока мы её не видим. Дом кода —
         * docs/rules/exchange-hold.md §«Ступень 1 — мягкий холд».
         */
        public static final String ANOMALY_PASS_INCOMPLETE = "ANOMALY_PASS_INCOMPLETE";

        /**
         * Судьба встроенной защиты не определена: цикл добычи прошёл, а
         * разбор истории не дал записи ни одной ногой. Терминал на этом
         * не ставится — разбор ведёт человек. Дом кода —
         * docs/components/RefreshOrderExecutor.md, дом тропы —
         * docs/lifecycles/Order.md §«Пустой разбор истории».
         */
        public static final String INSTRUMENT_PROTECTION_FATE_UNKNOWN = "INSTRUMENT_PROTECTION_FATE_UNKNOWN";

        /**
         * Принимающая корзина движений средств непуста: тип операции не
         * покрыт отображением контура площадки. Дом кода —
         * docs/components/RefreshBillsExecutor.md §Границы, дом резолва —
         * docs/models/mapping/DealCashFlow.md §«Резолв категории».
         */
        public static final String UNCLASSIFIED_CASH_FLOW = "UNCLASSIFIED_CASH_FLOW";

        /**
         * Снятие риска на инструментном радиусе не подтвердилось: остаток
         * неустраним, значит интеграции нельзя доверять, а радиус ущерба
         * неизвестен — реакция эскалируется на счётный радиус. Дом кода —
         * docs/components/SafetyHoldCoordinator.md §«Гейт терминала
         * отчёта», дом соразмерности —
         * docs/rules/error-handling-policy.md §«Нарушение контракта
         * интеграции: радиус неизвестен по построению».
         */
        public static final String EXCHANGE_KILL_SWITCH_RESIDUAL = "EXCHANGE_KILL_SWITCH_RESIDUAL";

        /**
         * Сверка P&amp;L разошлась сверх допуска. Ступень мягкая:
         * подозрение на НАШ счёт, не на поведение площадки, а сворачивание
         * снесло бы материал разбора. Дом кода и реакции —
         * docs/rules/pnl-reconciliation.md §«Реакция на расхождение».
         */
        public static final String PNL_RECONCILIATION_MISMATCH = "PNL_RECONCILIATION_MISMATCH";

        /**
         * Серия подряд убыточных сделок достигла порога риск-аппетита.
         * Ступень мягкая: исчерпан наш риск-бюджет, а не доверие к
         * площадке. Дом кода — docs/rules/loss-streak-halt.md.
         */
        public static final String LOSS_STREAK_LIMIT_REACHED = "LOSS_STREAK_LIMIT_REACHED";

        /**
         * У вошедшей сделки нет знаменателя R: операции были, а плановый
         * риск пуст либо вырожден. Журнальный отчёт-происшествие; дом кода
         * — docs/models/domain/aggregate/Deal.md.
         */
        public static final String RISK_BENCHMARK_MISSING = "RISK_BENCHMARK_MISSING";

        /**
         * Запись закрытия эпизода добыта, а торговый исход из её сырого
         * типа не выводится. Дом кода —
         * docs/models/mapping/PositionCloseResult.md.
         */
        public static final String UNRECOGNIZED_CLOSE_TYPE = "UNRECOGNIZED_CLOSE_TYPE";

        /**
         * Итог сделки неисчислим на аварийном терминале: число остаётся
         * пустым, и пустота обязана быть счётной. Дом кода —
         * docs/components/MarkDealEmergencyClosedExecutor.md.
         */
        public static final String RESULT_NOT_COMPUTABLE = "RESULT_NOT_COMPUTABLE";

        /**
         * Ноль результата записан, а расчётная валюта не резолвилась — в
         * чём выражен ноль, неизвестно. Дом кода —
         * docs/components/MarkDealClosedExecutor.md §«Терминальное ребро».
         */
        public static final String RESULT_CURRENCY_UNRESOLVED = "RESULT_CURRENCY_UNRESOLVED";

        /**
         * Валюта, в которой площадка посчитала итог эпизода, разошлась с
         * расчётной валютой инструмента: итог складывается из разноимённых
         * слагаемых и смещён. Отчёт делает смещение наблюдаемым; терминал
         * им не блокируется. Дом контроля —
         * docs/rules/pnl-reconciliation.md §«Проверка валюты чисел записей
         * закрытия».
         */
        public static final String RESULT_CURRENCY_MISMATCH = "RESULT_CURRENCY_MISMATCH";

        /**
         * Контроль валюты не проведён: расчётная валюта инструмента не
         * резолвится, и сравнивать не с чем. Свой код, а не молчание:
         * «не проверяли» обязано отличаться от «проверили, всё в порядке».
         * Дом — там же.
         */
        public static final String RESULT_CURRENCY_UNVERIFIABLE = "RESULT_CURRENCY_UNVERIFIABLE";
    }

    /**
     * Заголовки исходящих вызовов к соседям по ярусу.
     */
    @UtilityClass
    public class Header {

        /**
         * Схема предъявления сервисного токена. Читателей четыре —
         * по клиенту на каждого соседа, — и форма у всех обязана быть
         * одна: расхождение в одном клиенте выглядит отказом
         * идентичности на стороне соседа
         * (.claude/rules/codestyle.md §Константы).
         */
        public static final String BEARER_PREFIX = "Bearer ";
    }
}
