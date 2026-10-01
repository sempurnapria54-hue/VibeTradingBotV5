package com.example.marketdata.box;

import static java.util.Objects.isNull;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Замок записи на строки таблицы, взятый СНАРУЖИ ящика своим соединением
 * ({@link Rows#lockForWrites(String)}) и удерживаемый до {@link #close()}.
 *
 * <p><b>Им подаётся вход «посреди приёма команды», у которой нет обращения
 * к стабу.</b> Удержанный ответ площадки ({@link HeldAnswer}) ставит ход
 * кейса внутрь шага цикла, потому что шаг ходит на площадку между чтением
 * и записью; приём требования потребителя не ходит никуда, и окно между
 * его чтением и записью открывает только очередь замка: записи встают в
 * неё в порядке прихода, и порядок наблюдается, а не угадывается паузой.
 *
 * <p><b>Отпускание — откат:</b> транзакция замка ничего не пишет.
 */
final class RowLock implements AutoCloseable {

    private final Connection connection;

    RowLock(Connection connection) {
        this.connection = connection;
    }

    /** Отпускает замок: записи из очереди проходят в порядке прихода. */
    @Override
    public void close() {
        try {
            connection.rollback();
        } catch (SQLException failure) {
            throw new IllegalStateException("База субстрата не отпустила замок", failure);
        } finally {
            closeQuietly(connection);
        }
    }

    /** Закрывает соединение, не маскируя исходный отказ отказом закрытия. */
    static void closeQuietly(Connection connection) {
        if (isNull(connection)) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Закрытие после отказа: исходный отказ уже брошен вызывающим.
        }
    }
}
