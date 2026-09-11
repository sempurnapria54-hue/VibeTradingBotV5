package com.example.auditstatistics.persistence.repository.journal;

import com.example.auditstatistics.persistence.model.journal.AccessDenialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Строки отвергнутых по правам вызовов.
 *
 * <p><b>Читающих запросов здесь нет и превентивно не заводится:</b>
 * единственный потребитель модели — разбор человеком
 * (docs/models/domain/other/AccessDenial.md), а метод, который никто не
 * вызывает, запрещён конвенцией (.claude/rules/codestyle.md
 * §«Неиспользуемый код»).
 *
 * <p><b>Вставка идёт сохранением сущности, а не поглощающим конфликт
 * запросом</b>, как у строки журнала: поглощать здесь нечего — дедупа у
 * происшествия нет по природе факта, и ключа, по которому две попытки
 * считались бы одной, не существует.
 */
public interface AccessDenialRepository extends JpaRepository<AccessDenialEntity, Long> {
}
