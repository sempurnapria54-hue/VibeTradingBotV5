package com.example.connector.okx.box;

import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ящик со штатным положением осей окружения: окружение `dev`, адрес
 * площадки — стаб, контур доступа настроен, хранилище живо, кэш ключей
 * включён с заведомо длинным сроком.
 *
 * <p><b>Контекст у наследников этого класса ОДИН.</b> Ключ кэша контекста
 * несёт набор методов {@code @DynamicPropertySource}, и у всех
 * наследников он один и тот же — объявленный здесь; свой метод заводит
 * ровно тот класс кейсов, у которого положение осей другое, и платит за
 * это своим подъёмом.
 */
abstract class SharedConnectorBox extends ConnectorBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        ConnectorSubstrate.register(registry, Map.of());
    }
}
