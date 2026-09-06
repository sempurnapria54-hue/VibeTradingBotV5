package com.example.tradingcore.domain.safety;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Ведёт счётчик серии подряд убыточных сделок счёта и отвечает, достигнут
 * ли предел. Исполнимая форма — docs/spec/loss-streak-halt.json; дом
 * правила — docs/rules/loss-streak-halt.md.
 *
 * <p><b>Носитель один на оба терминала.</b> Ход объявлен домом штатного
 * терминала, а аварийный делает его «тем же ходом»
 * (docs/components/MarkDealEmergencyClosedExecutor.md §«Побочные эффекты
 * терминала»); вторая копия формулы разошлась бы с первой.
 *
 * <p><b>Операнд — ЦЕНОВОЙ результат, а не итог целиком:</b> записанное
 * число плюс накопленное финансирование эпизодов. Серия меряет,
 * реализовалась ли допустимая потеря, а финансирование в определение риска
 * не входит (docs/rules/risk-policy.md).
 *
 * <p><b>Популяция — оба терминала сделки;</b> ошибочное состояние
 * терминалом не является и счётчика не двигает — сделка доедет до
 * аварийного терминала и будет учтена там ровно один раз. Вызывающими
 * поэтому служат только терминальные звенья, и признак популяции здесь не
 * пересчитывается.
 */
@Service
@RequiredArgsConstructor
public class LossStreakCounter {

    private final ExchangeAccountDataService exchangeAccountDataService;
    private final TenantRiskAppetiteDataService tenantRiskAppetiteDataService;

    /**
     * Применить исход сделки к счётчику серии и ответить, достигнут ли
     * предел. Ход идёт транзакцией терминала; запрос ступени — после её
     * коммита, и делает его вызывающий.
     *
     * <p>Три исхода: ценовой убыток увеличивает, ценовая прибыль
     * обнуляет, ноль и недоступное число оставляют как было. <b>Неполный
     * граф тоже оставляет:</b> второе слагаемое ценового результата
     * считается по загружаемой коллекции эпизодов, и на усечённой загрузке
     * счётчик замораживается, а не обнуляется убыточной carry-сделкой,
     * прочитанной как прибыльная.
     */
    public Boolean applyTerminal(DealContext dealContext) {
        BigDecimal priceResult = priceResult(dealContext);
        ExchangeAccount account = dealContext.getExchangeAccount();
        Integer countBefore = zeroIfNull(account.getConsecutiveLossCount());
        if (isNull(priceResult) || priceResult.signum() == 0) {
            return haltTriggered(account, countBefore);
        }
        boolean loss = priceResult.signum() < 0;
        exchangeAccountDataService.applyLossStreak(account.getId(), loss);
        Integer countAfter = loss ? countBefore + 1 : 0;
        account.setConsecutiveLossCount(countAfter);
        return haltTriggered(account, countAfter);
    }

    /**
     * Ценовой результат: итог без накопленного финансирования. Знак
     * финансирования в домене нормализован издержкой, поэтому возврат
     * издержки в число есть сложение. Пусто — числа нет либо граф
     * предъявлен не целиком.
     */
    private BigDecimal priceResult(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(dealContext.getGraphComplete()) || isNull(deal.getResultProfit())) {
            return null;
        }
        return deal.getResultProfit().add(fundingCost(deal));
    }

    /**
     * Накопленное финансирование сделки — сумма по эпизодам. Берётся из
     * записи закрытия эпизода, а не из строк разбивки движений: те сверяют
     * то же число и в ценовой результат не входят.
     */
    private BigDecimal fundingCost(Deal deal) {
        return emptyIfNull(deal.getPositions()).stream()
                .map(Position::getExternalFundingCost)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Предел достигнут. Порог не задан — срабатывать нечему: торговля уже
     * отвергнута преконтролем кодом незаданного числа риск-аппетита, и
     * провизорное значение здесь не подставляется.
     */
    private Boolean haltTriggered(ExchangeAccount account, Integer countAfter) {
        Integer limit = tenantRiskAppetiteDataService.findByTenantInternalId(account.getTenantId())
                .map(Tenant::getGlobalConsecutiveLossLimit)
                .orElse(null);
        return nonNull(limit) && countAfter >= limit;
    }

    private Integer zeroIfNull(Integer value) {
        return isNull(value) ? 0 : value;
    }
}
