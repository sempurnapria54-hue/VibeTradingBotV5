package com.example.auth.domain.service;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import lombok.Getter;

/**
 * Окружение не допускает счёт этого контура.
 *
 * <p>Отдельный тип, а не общий отказ валидации: причина отказа несущая —
 * непроизводственное окружение не двигает капитал, — и она обязана быть
 * различима на внешней поверхности, а не тонуть в «неверный запрос».
 */
@Getter
public class ContourNotAdmittedException extends RuntimeException {

    private final ExchangeAccount.Contour contour;

    public ContourNotAdmittedException(ExchangeAccount.Contour contour) {
        super("Контур " + contour + " не допускается этим окружением");
        this.contour = contour;
    }
}
