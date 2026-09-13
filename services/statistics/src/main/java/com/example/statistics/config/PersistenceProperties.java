package com.example.statistics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Учётные данные владельца данных сервиса
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Комплект один: владелец у процесса один.</b> Факты и агрегаты —
 * два предмета одного владельца, и лежат они в одной базе; чужого
 * подключения конструкция не оставляет ни одного
 * (.claude/decisions/audit-statistics-split.md).
 *
 * <p>Инвариант «один пишущий» этим обеспечен, а не только объявлен:
 * писателя отделяет от чужих таблиц то, что чужих учётных данных у
 * процесса нет вовсе.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "statistics.persistence")
public class PersistenceProperties extends DatabaseConnectionProperties {
}
