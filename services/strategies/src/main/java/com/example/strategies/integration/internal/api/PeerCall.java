package com.example.strategies.integration.internal.api;

import com.example.platform.exception.PeerServiceUnavailableException;
import com.example.strategies.exception.PeerReadException;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Разводит отказ соседа по ярусу на два класса и держит эту границу в
 * одном месте. Граница — дом класса, а не удобство:
 * docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу — свой
 * класс, и сделку в ошибку он не уводит» и его подразделы. Недоступность —
 * транспорт ({@link ResourceAccessException}) и {@code 5xx}; осознанный отказ
 * соседа ({@code 4xx}), неразбираемое тело и негодный адрес — наш дефект,
 * повтором он не лечится; команда классифицируется так же, как чтение.
 *
 * <p>Живёт хелпером, а не методом каждого клиента: вызовов к соседям у
 * сервиса несколько, и копии одного разбора разошлись бы первой же правкой
 * класса. Копия этого хелпера у соседнего сервиса — объявленное семейство
 * ({@code tools/peer-copy-check.py}); javadoc у копий один.
 *
 * <p><b>Коннектор сюда не входит, и это не пропуск.</b> Он объявляет
 * класс отказа ОТДЕЛЬНЫМ ПОЛЕМ ответа
 * ({@code docs/components/IntegrationService.md} §«Классы отказа на
 * границе — дом здесь»), и разбор его вызова читает тело, а не статус.
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
        } catch (ResourceAccessException e) {
            throw new PeerServiceUnavailableException(
                    "Peer " + peer + " transport error on [" + endpoint + "]", e);
        } catch (RestClientException e) {
            throw new PeerReadException(
                    "Peer " + peer + " answered [" + endpoint + "] with a body we cannot read", e);
        } catch (IllegalArgumentException e) {
            throw new PeerReadException(
                    "Peer " + peer + " address is not usable for [" + endpoint + "]", e);
        }
    }
}
