package com.example.bff.integration;

/**
 * Сосед по ярусу не ответил: операнд не добыт, и вызывающему это
 * сообщается отдельным классом, а не выдаётся за его собственную ошибку
 * (docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу —
 * свой класс»).
 */
public class PeerServiceUnavailableException extends RuntimeException {

    public PeerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
