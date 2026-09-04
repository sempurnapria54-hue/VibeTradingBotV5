package com.example.tradingbot.config;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.integration.service.okx.OkxSigningInterceptor;
import com.example.tradingbot.integration.service.okx.OkxWriteLoggingInterceptor;
import com.example.tradingbot.util.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Собирает REST-клиенты к OKX: публичный (рыночные данные шага 1) и
 * приватный с подписью ({@link OkxSigningInterceptor}) для торговых и
 * account endpoint'ов. Для demo-окружения добавляется header
 * x-simulated-trading (docs/integrations/okx/contracts/service-urls.md).
 * {@code OkxProperties} регистрируется через @ConfigurationPropertiesScan.
 *
 * <p><b>OKX-статусы не бросаем.</b> Источник истины ошибки у OKX — поле
 * {@code code} в теле {@code OkxApiResponse}, не HTTP-статус: на ошибки
 * параметров demo-OKX отвечает HTTP 4xx (например 400 с
 * {@code {"code":"51000",...}}), а не HTTP 200. Дефолтный обработчик
 * RestClient на 4xx/5xx бросает {@code HttpClientErrorException} и тело-конверт
 * теряется. Поэтому на обоих клиентах гасим бросок на error-статусах
 * (no-op {@code defaultStatusHandler}) — тело декодируется в
 * {@code OkxApiResponse}, потребитель смотрит {@code code}. Так контур
 * {@code /raw} видит реджект OKX как нормальный конверт (HTTP 200 на нашей
 * границе, {@code code≠"0"} внутри).
 */
@Configuration
public class OkxConfig {

    @Bean
    public RestClient okxRestClientHttp(OkxProperties properties, RestClient.Builder builder) {
        RestClient.Builder configured = builder.baseUrl(properties.getBaseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { });
        if (isNotBlank(properties.getSimulated())) {
            configured = configured.defaultHeader(Constants.Okx.SIMULATED_HEADER, properties.getSimulated());
        }
        return configured.build();
    }

    @Bean
    public RestClient okxAuthRestClientHttp(OkxProperties properties, RestClient.Builder builder,
                                            OkxSigningInterceptor signingInterceptor,
                                            OkxWriteLoggingInterceptor writeLoggingInterceptor) {
        RestClient.Builder configured = builder.baseUrl(properties.getBaseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .requestInterceptor(signingInterceptor)
                .requestInterceptor(writeLoggingInterceptor)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (isNotBlank(properties.getSimulated())) {
            configured = configured.defaultHeader(Constants.Okx.SIMULATED_HEADER, properties.getSimulated());
        }
        return configured.build();
    }
}
