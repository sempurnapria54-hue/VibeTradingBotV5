package com.example.connector.okx.integration.client;

import com.example.connector.okx.credentials.ExchangeCredentials;
import org.springframework.web.client.RestClient;

/**
 * Клиент, подписывающий запрос ключами КОНКРЕТНОГО счёта.
 *
 * <p><b>Почему фабрика, а не один клиент с перехватчиком.</b> В доноре
 * подпись ставил перехватчик, читавший ключи из конфигурации процесса, —
 * это работало, пока счёт был один. Коннектор стейтлесс и обслуживает
 * любой счёт любого тенанта: ключи стали операндом вызова, и клиент,
 * несущий одни ключи на процесс, подписал бы чужой запрос своими.
 *
 * <p>Ключи через поле не текут и в логи не попадают: они живут в
 * аргументе вызова и в заголовках запроса, который строит реализация.
 */
public interface SignedRestClientFactory {

    /**
     * Клиент, подписывающий этими ключами.
     *
     * <p>Реализация вправе кэшировать клиента по счёту: подпись зависит от
     * ключей, а ключи у счёта не меняются между ротациями — событие
     * `ExchangeKeysRotated` и есть момент сброса кэша
     * (docs/architecture/contracts.md §События).
     */
    RestClient forCredentials(ExchangeCredentials credentials);
}
