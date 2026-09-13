package com.example.audit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Учётные данные владельца данных сервиса
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Владелец один, поэтому и комплект один.</b> Прежде их было два —
 * журнал и агрегаты, — и третьим подключением журнал читался под ролью
 * агрегатов. Раздел сервиса снял обе конструкции вместе с их предметом:
 * статистика уехала своим процессом со своей базой и своей ролью
 * (.claude/decisions/audit-statistics-split.md).
 *
 * <p>Инвариант «один пишущий» этим не теряет обеспечения: писателя отделяет
 * от чужих таблиц то, что чужих учётных данных у процесса нет вовсе.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "audit.persistence")
public class PersistenceProperties {

    /** Подключение под ролью-владельцем журнала: `audit`. */
    private DatabaseConnectionProperties journal = new DatabaseConnectionProperties();
}
