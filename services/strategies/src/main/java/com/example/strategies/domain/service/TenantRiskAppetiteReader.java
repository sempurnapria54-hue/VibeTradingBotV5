package com.example.strategies.domain.service;

import static java.util.Objects.isNull;

import com.example.strategies.domain.model.TenantRiskAppetite;
import com.example.strategies.integration.internal.api.TradingCoreReadClient;
import com.example.strategies.integration.internal.api.model.RiskAppetiteCoreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Добывает у ядра принятые им числа риск-аппетита и отвергает вызов, если
 * хоть одного из трёх нет (docs/rules/risk-policy.md, правило о числах
 * риск-аппетита: пустое место — отказ).
 *
 * <p><b>Один компонент на обе тропы — создание и активацию.</b> Копии
 * одного разбора разошлись бы первой же правкой: у создания и активации
 * операнд один и тот же, и вердикт «числа не приняты» обязан быть
 * одинаковым.
 *
 * <p><b>Пустое число отвергает вызов ЗДЕСЬ, а не в обходе деталей.</b>
 * Проверка неравенств пропускает неторгуемую деталь по построению, и
 * определение, у которого торгуемых деталей нет вовсе, прошло бы мимо
 * охраны: разрешение выдавалось бы там, где ни одно неравенство не
 * считалось. Охрана внутри обхода при этом остаётся — она адресует
 * отказ конкретной детали.
 *
 * <p><b>Пустой ответ — тоже отказ.</b> Ядро чисел не отдало, и это
 * отличается от «число пусто» лишь причиной, но не исходом.
 */
@Service
@RequiredArgsConstructor
public class TenantRiskAppetiteReader {

    private final TradingCoreReadClient tradingCoreReadClient;

    /** Принятые ядром числа; хоть одного нет — отказ вызова. */
    public TenantRiskAppetite read() {
        RiskAppetiteCoreResponse response = tradingCoreReadClient.getRiskAppetite();
        if (isNull(response)
                || isNull(response.globalSimultaneousRiskPerDealPercent())
                || isNull(response.globalCumulativeRiskPerDealMultiplier())
                || isNull(response.globalMaxLeverage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "STRATEGY_RISK_APPETITE_NOT_CONFIGURED: числа риск-аппетита ядром не приняты");
        }
        return new TenantRiskAppetite(response.globalSimultaneousRiskPerDealPercent(),
                response.globalCumulativeRiskPerDealMultiplier(),
                response.globalMaxLeverage());
    }
}
