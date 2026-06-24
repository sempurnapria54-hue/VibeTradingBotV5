package com.example.tradingbot.domain.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Concurrency-guard оркестрационного прохода (D-M1): блокировка на уровне
 * БД на весь проход петли через Postgres advisory lock. Под одну
 * блокировку заходят и таймерный, и ручной запуск → проходы сериализуются,
 * перекрывающий пропускается, per-deal-коллизий (двойной SUBMIT) нет.
 * База, а не in-memory {@link JobExecutionGuard} — чтобы держать при
 * нескольких копиях/перезапуске (мультиинстанс). Лок держится на одном
 * соединении весь проход; работа прохода идёт по другим соединениям пула.
 * См. docs/components/DealOrchestratorJob.md §Concurrency-guard.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorPassLock {

    /**
     * Стабильный ключ advisory-lock прохода оркестратора.
     */
    private static final long LOCK_KEY = 776_644_220_001L;

    private final DataSource dataSource;

    /**
     * Выполнить проход эксклюзивно под advisory-lock. Лок занят (перекрытие)
     * → задача не запускается, возвращается {@code false}.
     */
    public Boolean runExclusively(Runnable task) {
        try (Connection connection = dataSource.getConnection()) {
            if (isFalse(tryLock(connection))) {
                return false;
            }
            try {
                task.run();
                return true;
            } finally {
                unlock(connection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Orchestrator advisory lock failed", e);
        }
    }

    private Boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        }
    }
}
