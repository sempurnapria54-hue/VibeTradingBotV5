package com.example.auth.domain.service;

import static java.util.Objects.isNull;

import com.example.auth.config.EnvironmentProperties;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

/**
 * Допуск контура при регистрации биржевого счёта.
 *
 * <p>Дом правила — docs/architecture/platform.md §«Чем различаются
 * окружения»; исполнимая форма — docs/spec/environment-contour.json,
 * величина {@code contourAdmitted}. Здесь — точка, где правило
 * СРАБАТЫВАЕТ: момент регистрации, а не момент сделки. Отказ на
 * регистрации дёшев; отказ на сделке приходит отказом доступа площадки и
 * поздно.
 */
@Service
public class ExchangeAccountRegistrationService {

    private final EnvironmentProperties environment;

    public ExchangeAccountRegistrationService(EnvironmentProperties environment) {
        this.environment = environment;
    }

    /**
     * Допускает ли окружение счёт этого контура.
     *
     * <p>Пустой перечень допустимых контуров — не «допустимы все», а
     * отказ: незаданное место означает отказ, а не разрешение
     * (docs/concept.md). Иначе окружение без конфигурации торговало бы
     * боевыми деньгами.
     */
    public Boolean contourAdmitted(ExchangeAccount.Contour contour) {
        if (isNull(contour) || CollectionUtils.isEmpty(environment.getAdmittedContours())) {
            return Boolean.FALSE;
        }
        return environment.getAdmittedContours().contains(contour);
    }
}
