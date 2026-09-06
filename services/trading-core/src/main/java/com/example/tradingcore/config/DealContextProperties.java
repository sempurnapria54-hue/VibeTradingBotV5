package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки сборки контекста прохода.
 *
 * <p><b>Дом величины — конфигурация, а не константа кода</b>
 * (docs/components/DealContextService.md §«Объёмы загрузки»): конкретное
 * число — параметр развёртывания, от него не зависит ни одна формула.
 *
 * <p><b>Секция своя, а не у оркестратора.</b> Потолок читает сборка
 * контекста, а не проход: оркестратор о нём не знает и знать не обязан, а
 * общая секция сделала бы его читателем чужой величины.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "deal-context")
public class DealContextProperties {

    /**
     * Потолок выборки строк разбивки движений на одну сделку.
     *
     * <p>Это единственная коллекция контекста, чья мощность не задана
     * конструкцией сделки: у долгой сделки с частым начислением
     * финансирования она растёт со временем жизни. Упёршаяся в потолок
     * выборка — <b>не усечение, а неполнота</b>: расчёт итогового числа
     * запрещается, а не считается по усечённому множеству
     * (docs/spec/deal-context-load.json §cashFlowsComplete).
     */
    private Integer cashFlowWindowLimit;

    /**
     * Толерантность прохода к возрасту снимка средств: старше — обработчик
     * заказывает добычу и на этой итерации ни преконтроля, ни создания
     * заявки не запускает
     * (docs/components/TranchePrecheckHandler.md §«Рабочая логика»).
     *
     * <p><b>Величина операционная, а не риск-аппетитная</b>, поэтому живёт
     * в конфигурации рядом с политикой повтора: она не потолок риска, а
     * срок годности операнда, и правка обязана браться сразу везде.
     * Пустое место читается как «возраст не ограничен» — снимок в добычу
     * не заказывается, потому что срока, по которому он устарел бы, никто
     * не объявил (docs/rules/absent-value-semantics.md).
     */
    private Duration balanceFreshness;
}
