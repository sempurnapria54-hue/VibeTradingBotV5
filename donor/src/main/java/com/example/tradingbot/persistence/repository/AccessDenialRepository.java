package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.security.AccessDenialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Строки отвергнутых по правам вызовов. Запросов чтения здесь нет и не
 * заводится превентивно: единственный читатель модели — разбор человеком
 * (docs/models/domain/other/AccessDenial.md), а метод, который никто не
 * вызывает, конвенцией запрещён.
 */
public interface AccessDenialRepository extends JpaRepository<AccessDenialEntity, Long> {
}
