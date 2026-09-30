package com.example.connector.okx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Публичный клиент площадки — тот, которому ключи не нужны.
 *
 * <p>Подписывающий клиент бином не бывает: он собирается на ключи
 * конкретного счёта
 * ({@code com.example.connector.okx.integration.external.api.client.SignedRestClientFactory}).
 * Публичный — наоборот, один на процесс: у чтения листинга и свечей нет
 * счёта, от которого он мог бы зависеть.
 *
 * <p><b>Демо-заголовка на публичном клиенте нет.</b> Контур принадлежит
 * счёту, а у публичного чтения счёта нет: заголовок на процесс приписал бы
 * все рыночные данные одному контуру, а {@code prod} допускает счета обоих.
 * Что DEMO-счёт получает инструменты и цены боевого контура — допущение, а
 * не факт источника; довод и цена — дом
 * {@code docs/integrations/okx/contracts/service-urls.md}.
 *
 * <p><b>Ошибочные статусы не бросаются</b> — по той же причине, что и у
 * подписывающего клиента: тело-конверт с полем причины дороже статуса.
 *
 * <p><b>Фабрика запросов — общая на процесс, и это не мелочь.</b>
 * Подписывающий клиент собирается НА ВЫЗОВ (ключи — операнд), а
 * {@code build()} без явной фабрики заводит свою: соединения перестали бы
 * переиспользоваться, и каждый вызов площадке открывал бы новое. Общая
 * фабрика оставляет пул один на процесс, а на вызов приходится только
 * тонкая обёртка с перехватчиком подписи.
 */
@Configuration
public class OkxClientConfig {

    /**
     * Транспорт, общий для публичного и подписывающих клиентов: пул
     * соединений живёт здесь, а не в клиенте, который собирается на вызов.
     */
    @Bean
    public ClientHttpRequestFactory okxRequestFactory() {
        return ClientHttpRequestFactoryBuilder.detect().build();
    }

    @Bean
    public RestClient okxRestClientHttp(OkxProperties properties, RestClient.Builder builder,
                                        ClientHttpRequestFactory requestFactory) {
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }
}
