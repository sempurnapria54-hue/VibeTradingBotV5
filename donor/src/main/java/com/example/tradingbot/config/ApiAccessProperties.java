package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Принципал, которому открыта поверхность контура
 * (docs/rules/api-access-policy.md). Имя и секрет читаются из Vault
 * per-profile (secret/tradingbot/api-access[-test]), как datasource и
 * OKX-креды; в коммит не попадают.
 *
 * <p><b>Умолчаний нет ни у одного поля, и это намеренно.</b> Пустой секрет
 * поднял бы контур с поверхностью, закрытой ничем, — то есть ошибка была бы в
 * разрешающую сторону, а такие запрещены (docs/concept.md, П1 следствие 3).
 * Пустое место означает отказ подъёма, а не открытую поверхность.
 *
 * <p><b>Принципал один — посылка фазы 1</b>, и её параметр снятия — появление
 * второго субъекта поверхности (дом посылки и порядок пересмотра —
 * docs/rules/api-access-policy.md). Арность выражена <b>здесь и только
 * здесь</b>: остальной код работает с «текущим принципалом» и его имени не
 * знает, поэтому снятие посылки не растекается по коду.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "api-access")
public class ApiAccessProperties {

    /** Имя принципала: то, что журнал отвечает на «кто это был». */
    private String principal;

    /**
     * Секрет принципала в открытом виде — из Vault. В памяти хранится только
     * его хэш: {@code ApiAccessSecurityConfig} кодирует значение при сборке
     * бина и сырую строку не удерживает.
     */
    private String secret;
}
