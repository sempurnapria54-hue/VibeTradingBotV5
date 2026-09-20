package com.example.tradingcore.box;

import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ящик со штатным положением осей конфигурации: все семь тиков включены,
 * такт — до которого прогон не доживает, адреса трёх соседей — стабы,
 * контур доступа настроен на стаб провайдера идентичности.
 *
 * <p><b>Контекст у наследников этого класса ОДИН.</b> Ключ кэша контекста
 * несёт набор методов {@code @DynamicPropertySource}, и у всех
 * наследников он один и тот же — объявленный здесь; свой метод заводит
 * ровно тот класс кейсов, у которого положение осей другое, и платит за
 * это своим подъёмом.
 */
abstract class SharedTradingCoreBox extends TradingCoreBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.register(registry, Map.of());
    }
}
