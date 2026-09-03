package com.example.tradingbot.domain.command.risk;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Записывает четвёрку чисел риска сделки. Общий носитель обязанности, а
 * не копия в каждом исполнителе: перечень писателей закрыт
 * (docs/models/domain/aggregate/Deal.md §«Писатели четвёрки и их
 * триггеры»), и каждый из них пересчитывает ВСЕ ЧЕТЫРЕ целиком —
 * частичный пересчёт оставлял бы соседние числа посчитанными по прежнему
 * графу.
 *
 * <p><b>Пересчёт запрещён на неполном графе, и это обязанность каждого
 * писателя.</b> На неполном графе {@code plannedRiskAmount} вышел бы
 * заниженным, то есть ослабил бы кумулятивный потолок — ошибка в
 * разрешающую сторону. Признак читается ГОТОВЫМ с контекста прохода и не
 * пересобирается: иначе каждый писатель обязан был бы знать объём
 * загрузки.
 *
 * <p><b>Отказ записи звено НЕ завершает</b> — исход возвращается
 * вызывающему, прежние значения остаются нетронутыми, а повтор идёт по
 * бюджету попыток строки исполнения. Исчерпание бюджета — штатная
 * ошибочная тропа действия, а не тихое продолжение с прежними числами.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealRiskNumbersWriter {

    private final DealDataService dealDataService;

    /**
     * Пересчитать и записать четвёрку. {@code false} — граф предъявлен не
     * целиком, числа не тронуты и звено писателя не завершено.
     */
    public Boolean recompute(DealContext dealContext) {
        if (isFalse(DealRiskNumbers.recomputeAllowed(dealContext.getGraphComplete()))) {
            log.debug("Risk numbers not recomputed: deal graph incomplete dealId={}",
                    dealContext.getDeal().getId());
            return false;
        }
        Deal deal = dealContext.getDeal();
        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);
        deal.setPlannedRiskAmount(numbers.getPlannedRiskAmount());
        deal.setIncurredRiskAmount(numbers.getIncurredRiskAmount());
        deal.setCurrentRiskAmount(numbers.getCurrentRiskAmount());
        deal.setProtectionRelievedRiskAmount(numbers.getProtectionRelievedRiskAmount());
        dealDataService.save(deal);
        return true;
    }
}
