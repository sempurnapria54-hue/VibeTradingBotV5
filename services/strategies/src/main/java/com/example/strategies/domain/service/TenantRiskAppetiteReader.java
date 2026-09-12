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
 * Добывает числа риск-аппетита тенанта у ядра и отвергает вызов, если их
 * нет (docs/rules/risk-policy.md §«Числа назначает держатель; пустое
 * место — отказ»).
 *
 * <p><b>Один компонент на обе тропы — создание и активацию.</b> Копии
 * одного разбора разошлись бы первой же правкой: у создания и активации
 * операнд один и тот же, и вердикт «числа не назначены» обязан быть
 * одинаковым.
 *
 * <p><b>Пустое число отвергает вызов ЗДЕСЬ, а не в обходе деталей.</b>
 * Проверка неравенств пропускает неторгуемую деталь по построению, и
 * определение, у которого торгуемых деталей нет вовсе, прошло бы мимо
 * охраны: разрешение выдавалось бы там, где ни одно неравенство не
 * считалось. Охрана внутри обхода при этом остаётся — она адресует
 * отказ конкретной детали.
 *
 * <p><b>Строки нет — тоже отказ.</b> Ядро о тенанте ещё не знает, и это
 * отличается от «числа назначены пустыми» лишь причиной, но не исходом.
 */
@Service
@RequiredArgsConstructor
public class TenantRiskAppetiteReader {

    private final TradingCoreReadClient tradingCoreReadClient;

    /** Числа тенанта; не назначены — отказ вызова. */
    public TenantRiskAppetite read(String tenantInternalId) {
        RiskAppetiteCoreResponse response = tradingCoreReadClient.getRiskAppetite(tenantInternalId);
        if (isNull(response)
                || isNull(response.globalSimultaneousRiskPerDealPercent())
                || isNull(response.globalCatastrophicRiskPerDealMultiplier())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "STRATEGY_RISK_APPETITE_NOT_CONFIGURED: числа риск-аппетита тенанта не назначены");
        }
        return new TenantRiskAppetite(response.globalSimultaneousRiskPerDealPercent(),
                response.globalCatastrophicRiskPerDealMultiplier());
    }
}
