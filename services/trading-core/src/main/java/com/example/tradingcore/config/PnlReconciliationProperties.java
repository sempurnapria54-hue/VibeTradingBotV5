package com.example.tradingcore.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Три числа допуска сверки P&amp;L (docs/rules/pnl-reconciliation.md
 * §Допуск; форма — docs/spec/pnl-reconciliation.json, величина
 * {@code epsilon}).
 *
 * <p><b>Секция своя, а не у контура площадки, и это не вкусовая
 * развилка.</b> Числа общие: на площадку задаётся только РЕЖИМ, в котором
 * они применяются (docs/models/domain/core/Exchange.md §«Настройки контура
 * биржи» — перечень с названными границами). Донорская форма держала их у
 * контура; при второй площадке это дало бы два допуска там, где политика
 * объявляет один.
 *
 * <p><b>Пустого места у этих чисел нет:</b> некалиброванность выражена
 * режимом допуска, а не пустым числом. Умолчания ниже — рабочая форма
 * разведочного режима, и до калибровки держателем расхождение сверх них
 * лестницу не триггерит.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pnl-reconciliation")
public class PnlReconciliationProperties {

    /**
     * Относительный член: доля валового оборота области сверки. Охраняет
     * ошибку композиции разбивки — та растёт вместе с оборотом.
     */
    private BigDecimal relativeShare = BigDecimal.ZERO;

    /**
     * Омиссионный член: множитель ожидаемой round-trip комиссии сделки.
     * Охраняет омиссию фиксированного размера — потерянную строку.
     */
    private BigDecimal omissionMultiplier = BigDecimal.ZERO;

    /**
     * Пол допуска в расчётной валюте. Общий для обоих членов и вынесен
     * наружу: на сделке без входных ног омиссионный член равен нулю,
     * минимум схлопывается, и охрану композиции держит только пол.
     */
    private BigDecimal floor = BigDecimal.ZERO;
}
