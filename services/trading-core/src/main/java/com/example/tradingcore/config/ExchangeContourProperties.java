package com.example.tradingcore.config;

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
import org.springframework.stereotype.Component;

/**
 * Настройки контура площадки: значения на площадку целиком, в рантайме не
 * меняющиеся. Ключ секции — код площадки ({@code ExchangeAccount.exchangeCode}),
 * а не идентификатор счёта: контур описывает номенклатуру источника, и у
 * двух счетов одной площадки он один.
 *
 * <p><b>Дом перечня настроек — docs/models/domain/core/Exchange.md
 * §«Настройки контура биржи»</b>; колонок под них не заводится.
 *
 * <p><b>Здесь только то, что читает живой потребитель.</b> Три числа
 * допуска сверки контуром не задаются вовсе: они общие, не на площадку, и
 * живут своей секцией (docs/rules/pnl-reconciliation.md §Допуск); на
 * площадку задаётся только РЕЖИМ, в котором они применяются.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "exchange-contour")
public class ExchangeContourProperties {

    /** Настройки контура по площадкам; ключ — код площадки. */
    private Map<String, Contour> exchanges = new LinkedHashMap<>();

    /**
     * Контур площадки по её коду; секции нет — пустой контур.
     *
     * <p>Пустой контур не молчит: при нём отображение не покрывает ни
     * одного типа, всякая строка садится в принимающую корзину и заводит
     * журнальный отчёт. Разведочное состояние тем и объявлено, что видно в
     * данных.
     */
    public Contour forExchange(String exchangeCode) {
        return exchanges.getOrDefault(exchangeCode, new Contour());
    }

    /** Настройки контура одной площадки. */
    @Getter
    @Setter
    public static class Contour {

        /**
         * Отображение «сырой тип[/подтип] → категория движения». Ключ пары
         * — «type/subType», ключ типа — «type»; строка пары перекрывает
         * строку своего типа (docs/models/mapping/DealCashFlow.md
         * §«Резолв категории»). Пополняет держатель по наблюдённому
         * перечню; автоматического пополнения нет.
         */
        private Map<String, DealCashFlow.CashFlowCategory> cashFlowCategoryMapping = new LinkedHashMap<>();

        /**
         * Исключения сверки: ключи «type» / «type/subType», выведенные из
         * области сверки P&L, будучи покрытыми отображением
         * (docs/integrations/okx/rules/cash-flow-categories.md §«Типы вне
         * экономики сделки»). Пишет держатель.
         */
        private List<String> reconciliationExclusions = new ArrayList<>();

        /**
         * Глубина свежего эндпоинта движений средств, дней. Операнд
         * решения «звать ли архив»: контрактная величина источника
         * (docs/integrations/okx/contracts/account-bills.md).
         */
        private Integer billsFreshDepthDays = 7;

        /**
         * Глубина архива движений средств, дней. Операнд признака полноты
         * разбивки: окно добычи, уходящее глубже архива, накрыть жизнь
         * сделки не могло (docs/components/RefreshBillsExecutor.md).
         */
        private Integer billsArchiveDepthDays = 90;

        /**
         * Допуск сверки НЕ калиброван — разведочный режим. Умолчание
         * истинно намеренно: допуск отгружается некалиброванным, и до
         * калибровки расхождение неотличимо от «допуск не тот», поэтому
         * лестница им не триггерится (docs/rules/pnl-reconciliation.md
         * §«Разведочный режим допуска»). Снимает флаг держатель.
         */
        private Boolean reconciliationExploratory = true;

        /**
         * Тип либо пара выведены из области сверки: строка типа покрывает
         * все его подтипы, строка пары — точечно
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
         * Резолв категории от частного к общему: точная пара, затем тип без
         * подтипа. Пусто — тип отображением не покрыт: вызывающий садит
         * строку в принимающую корзину и поднимает отчёт нераспознанного
         * движения.
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
