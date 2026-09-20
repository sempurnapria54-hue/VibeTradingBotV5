package com.example.strategies.box;

import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ящик со штатным положением осей конфигурации: реле включено, такт — до
 * которого прогон не доживает, адрес соседа — стаб, контур доступа
 * настроен на стаб провайдера идентичности, окно перечня умолчательное.
 *
 * <p><b>Контекст у наследников этого класса ОДИН.</b> Ключ кэша контекста
 * несёт набор методов {@code @DynamicPropertySource}, и у всех
 * наследников он один и тот же — объявленный здесь; свой метод заводит
 * ровно тот класс кейсов, у которого положение осей другое, и платит за
 * это своим подъёмом.
 */
abstract class SharedStrategiesBox extends StrategiesBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StrategiesSubstrate.register(registry, Map.of());
    }
}
