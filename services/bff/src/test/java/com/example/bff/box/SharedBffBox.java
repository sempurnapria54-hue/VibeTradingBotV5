package com.example.bff.box;

import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ящик со штатным положением осей конфигурации: контур доступа настроен
 * на стаб провайдера идентичности, шесть владельцев адресуются шаблоном
 * стаба, темы подписки — обе, секрет билета задан, такт пульса длиннее
 * прогона.
 *
 * <p><b>Контекст у наследников этого класса ОДИН.</b> Ключ кэша контекста
 * несёт набор методов {@code @DynamicPropertySource}, и у всех
 * наследников он один и тот же — объявленный здесь; свой метод заводит
 * ровно тот класс кейсов, у которого положение осей другое, и платит за
 * это своим подъёмом.
 */
abstract class SharedBffBox extends BffBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of());
    }
}
