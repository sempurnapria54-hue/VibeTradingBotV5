package com.example.connector.okx.integration.client;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.connector.okx.config.OkxProperties;
import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.util.OkxConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Собирает подписывающего клиента на ключи конкретного счёта.
 *
 * <p><b>Кэша клиентов нет, и это выбор, а не упущение.</b> Контракт кэш
 * разрешает, но кэшированный клиент держит ключи в памяти процесса
 * дольше вызова — то есть ровно то, чего коннектор избегает, не имея
 * базы. (Короткий кэш самих ключей — отдельная вещь и живёт у
 * {@code CachingExchangeCredentialsResolver}: его требует архитектура.)
 * Цена отказа мала **потому, что фабрика запросов передана явно**:
 * соединения держит она, одна на процесс, а на вызов приходится тонкая
 * обёртка с перехватчиком подписи. Без явной фабрики {@code build()}
 * заводил бы свою, и каждый вызов открывал бы новое соединение — это и
 * была находка фокуса производительности. Условие пересмотра — замер,
 * показывающий сборку обёртки в профиле горячего пути.
 *
 * <p><b>Контур выражается заголовком здесь, а не в конфигурации.</b>
 * Демо-ключ приходит вместе со своим контуром
 * ({@link ExchangeCredentials}), поэтому заголовок демо-контура ставится
 * на клиента этого счёта — процесс обслуживает счета обоих контуров
 * одновременно, и общий заголовок на процесс отправил бы боевой запрос в
 * демо.
 *
 * <p><b>Ошибочные HTTP-статусы не бросаются.</b> Источник истины ошибки у
 * площадки — поле {@code code} в теле конверта, не статус: на отвергнутый
 * параметр приходит 4xx с телом-конвертом, и дефолтный обработчик потерял
 * бы тело вместе с кодом причины.
 */
@Component
@RequiredArgsConstructor
public class OkxSignedRestClientFactory implements SignedRestClientFactory {

    private final OkxProperties properties;
    private final RestClient.Builder builder;
    private final ClientHttpRequestFactory requestFactory;
    private final OkxWriteLoggingInterceptor writeLoggingInterceptor;

    @Override
    public RestClient forCredentials(ExchangeCredentials credentials) {
        RestClient.Builder configured = builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .requestInterceptor(new OkxSigningInterceptor(credentials))
                .requestInterceptor(writeLoggingInterceptor)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (isTrue(credentials.isDemo())) {
            configured = configured.defaultHeader(OkxConstants.SIMULATED_HEADER, OkxConstants.SIMULATED_ON);
        }
        return configured.build();
    }
}
