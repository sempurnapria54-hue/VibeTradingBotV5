package com.example.strategies.integration;

import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Разводит отказ соседа по ярусу на два класса и держит эту границу в
 * одном месте.
 *
 * <p><b>Граница проведена домом класса, а не удобством:</b> недоступностью
 * названы таймаут, обрыв и {@code 5xx}
 * (docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу —
 * свой класс, и сделку в ошибку он не уводит»); осознанный отказ соседа
 * ({@code 4xx}) — наш дефект и повтором не лечится.
 *
 * <p>Живёт хелпером, а не методом каждого клиента: вызовов к соседям у
 * владельца определений два — числа риск-аппетита тенанта и проверка
 * ссылок определения, — и копии одного разбора разошлись бы первой же
 * правкой класса.
 *
 * <p><b>Недоступность соседа отвергает создание, а не пропускает его.</b>
 * Операнд не добыт — сверять объявленное автором не с чем, и пропуск был
 * бы разрешающей ошибкой ровно там, где стои́т охрана
 * (docs/rules/strategy-validation.md).
 */
@UtilityClass
public class PeerCall {

    /**
     * Исполняет чтение соседа, переводя отказ в класс своей природы.
     *
     * @param peer     сосед, к которому шёл вызов, — для сообщения
     * @param endpoint что именно читалось
     * @param read     само чтение
     */
    public static <T> T execute(String peer, String endpoint, Supplier<T> read) {
        try {
            return read.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new PeerServiceUnavailableException(
                        "Peer " + peer + " failed on [" + endpoint + "]: " + e.getStatusCode(), e);
            }
            throw new PeerReadException(
                    "Peer " + peer + " refused [" + endpoint + "]: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new PeerServiceUnavailableException(
                    "Peer " + peer + " transport error on [" + endpoint + "]", e);
        }
    }
}
