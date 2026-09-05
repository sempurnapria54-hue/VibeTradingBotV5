package com.example.tradingcore.integration;

/**
 * Сосед по ярусу домена не ответил: таймаут, обрыв, {@code 5xx}
 * (docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу —
 * свой класс, и сделку в ошибку он не уводит»).
 *
 * <p><b>Это штатное эксплуатационное событие, а не наш баг.</b> Проход по
 * объекту пропускается целиком — статус не двигается, строка исполнения
 * не заводится, ступень не поднимается; следующий тик повторяет.
 * Уводить сделку в {@code ERROR} на перезапуске сервиса данных значило бы
 * делать плановую эксплуатацию аварией.
 */
public class PeerServiceUnavailableException extends RuntimeException {

    public PeerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
