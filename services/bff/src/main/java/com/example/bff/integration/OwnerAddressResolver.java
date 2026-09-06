package com.example.bff.integration;

import com.example.bff.config.PerimeterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Адрес владельца по его имени.
 *
 * <p><b>Перечня владельцев здесь нет, и это несущее свойство.</b>
 * Адресат выводится из первого сегмента пути, а таблица маршрутов стала
 * бы вторым носителем состава поверхности и старела бы при каждом новом
 * пути владельца (docs/architecture/contracts.md §«Владельца называет
 * путь, а не таблица маршрутов»). Новый владелец за периметром правки
 * периметра не требует — это и есть проверяемое свойство конвенции.
 *
 * <p><b>Энфорсер допустимого набора — не этот класс, а сетевая
 * политика.</b> Пара, которой нет в
 * {@code docs/architecture/contracts.md} §«Синхронные вызовы», в
 * кластере запрещена (docs/architecture/platform.md §Безопасность), и
 * запрос к невыдуманному имени попросту не соединится.
 */
@Component
@RequiredArgsConstructor
public class OwnerAddressResolver {

    /** Плейсхолдер имени владельца в шаблоне адреса. */
    private static final String OWNER_PLACEHOLDER = "{owner}";

    private final PerimeterProperties properties;

    /**
     * Базовый адрес владельца.
     *
     * @param owner имя единицы из инвентаря {@code services.md}
     * @return адрес, по которому периметр обращается к владельцу
     */
    public String baseUrlOf(String owner) {
        return properties.getOwnerUrlTemplate().replace(OWNER_PLACEHOLDER, owner);
    }
}
