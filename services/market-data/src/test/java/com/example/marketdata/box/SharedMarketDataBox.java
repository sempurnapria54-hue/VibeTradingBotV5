package com.example.marketdata.box;

import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ящик со штатным положением осей конфигурации: один тип инструмента,
 * две котировочные валюты, все пять тиков включены, такт — до которого
 * прогон не доживает, адрес коннектора — стаб.
 *
 * <p><b>Контекст у наследников этого класса ОДИН.</b> Ключ кэша контекста
 * несёт набор методов {@code @DynamicPropertySource}, и у всех
 * наследников он один и тот же — объявленный здесь; свой метод заводит
 * ровно тот класс кейсов, у которого положение осей другое, и платит за
 * это своим подъёмом.
 */
abstract class SharedMarketDataBox extends MarketDataBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        MarketDataSubstrate.register(registry, Map.of());
    }
}
