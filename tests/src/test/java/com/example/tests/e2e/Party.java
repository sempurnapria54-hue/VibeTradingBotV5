package com.example.tests.e2e;

import static java.util.Objects.nonNull;

/**
 * Сторона тропы — единица развёртывания, поднимаемая своим процессом.
 *
 * <p><b>Порядок объявления несущий:</b> адрес стороны назначается при её
 * заведении, и сторона, зовущая соседа, заводится после него — коннектор
 * раньше ядра, ядро раньше владельца определений, все владельцы раньше
 * периметра.
 */
public enum Party {

    /** Коннектор площадки: базы нет, ключи счёта берёт из хранилища. */
    CONNECTOR("connector-okx", null, "/api/v1"),

    /** Торговое ядро. */
    TRADING_CORE("trading-core", "core", "/api/v1/trading-core"),

    /** Владелец определений стратегий. */
    STRATEGIES("strategies", "strategies", "/api/v1/strategies"),

    /** Журнал аудита — durable-потребитель обеих тем. */
    AUDIT("audit", "audit", "/api/v1/audit"),

    /** Статистика — durable-потребитель темы ядра. */
    STATISTICS("statistics", "statistics", "/api/v1/statistics"),

    /** Владелец реестра счетов и членств: сторона тропы периметра, у прочих троп — стаб. */
    AUTH("auth", "auth", "/api/v1/auth"),

    /** Периметр: заводится последним — он адресует всех владельцев. */
    BFF("bff", null, "/api/v1/bff");

    private final String module;
    private final String databaseSuffix;
    private final String root;

    Party(String module, String databaseSuffix, String root) {
        this.module = module;
        this.databaseSuffix = databaseSuffix;
        this.root = root;
    }

    /** Имя модуля стороны — {@code services/<module>}. */
    public String module() {
        return module;
    }

    /** Есть ли у стороны своя база. */
    public Boolean hasDatabase() {
        return nonNull(databaseSuffix);
    }

    /** Суффикс имени базы стороны в общем контейнере. */
    public String databaseSuffix() {
        return databaseSuffix;
    }

    /** Корень поверхности стороны. */
    public String root() {
        return root;
    }
}
