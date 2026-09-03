package com.example.tradingbot.config;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.other.DealCashFlow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки контура биржи (секция exchange-contour) — значения на биржу
 * целиком, не меняющиеся в рантайме; ключ секции — имя биржи
 * (Exchange.name). Дом перечня настроек —
 * docs/models/domain/core/Exchange.md §«Настройки контура биржи»;
 * колонок под них не заводится.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "exchange-contour")
public class ExchangeContourProperties {

    /** Настройки контура по биржам; ключ — имя биржи. */
    private Map<String, Contour> exchanges = new LinkedHashMap<>();

    /** Контур биржи по имени; отсутствие секции — пустой контур (разведочное состояние). */
    public Contour forExchange(String exchangeName) {
        return exchanges.getOrDefault(exchangeName, new Contour());
    }

    /** Настройки контура одной биржи. */
    @Getter
    @Setter
    public static class Contour {

        /**
         * Отображение «сырой тип[/подтип] → категория движения». Ключ пары
         * — «type/subType», ключ типа — «type»; строка пары перекрывает
         * строку своего типа (docs/models/mapping/DealCashFlow.md
         * §«Резолв категории»). Стартовый набор наблюдением не
         * подтверждён; пополняет держатель по наблюдённому перечню,
         * автоматического пополнения нет.
         */
        private Map<String, DealCashFlow.CashFlowCategory> cashFlowCategoryMapping = new LinkedHashMap<>();

        /**
         * Список исключений сверки: ключи «type» / «type/subType»,
         * выведенные из области сверки P&L, будучи покрытыми отображением
         * (docs/integrations/okx/rules/cash-flow-categories.md §«Типы вне
         * экономики сделки»). Пишет держатель.
         */
        private List<String> reconciliationExclusions = new ArrayList<>();

        /**
         * Тип/пара выведены из области сверки списком исключений:
         * строка типа покрывает все его подтипы, строка пары — точечно
         * (docs/models/mapping/DealCashFlow.md §«Область сверки задаётся
         * списком исключений по бирже»).
         */
        public Boolean excludesFromReconciliation(String externalType, String externalSubType) {
            if (reconciliationExclusions.contains(externalType)) {
                return true;
            }
            return isNotBlank(externalSubType)
                    && reconciliationExclusions.contains(externalType + "/" + externalSubType);
        }

        /**
         * Резолв категории от частного к общему: точная пара, затем тип
         * без подтипа. Пусто — тип отображением не покрыт: вызывающий
         * садит строку в принимающую корзину OTHER и поднимает отчёт
         * нераспознанного движения.
         */
        public Optional<DealCashFlow.CashFlowCategory> resolveCategory(String externalType,
                                                                       String externalSubType) {
            if (isNotBlank(externalSubType)) {
                DealCashFlow.CashFlowCategory byPair =
                        cashFlowCategoryMapping.get(externalType + "/" + externalSubType);
                if (nonNull(byPair)) {
                    return Optional.of(byPair);
                }
            }
            return Optional.ofNullable(cashFlowCategoryMapping.get(externalType));
        }
    }
}
