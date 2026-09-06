package com.example.bff.integration;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.bff.config.PerimeterProperties;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Пересылка запроса владельцу.
 *
 * <p><b>Повторяются только ЧТЕНИЯ.</b> Бюджет повторов вызывающего на
 * мутирующий запрос не распространяется: повторённое чтение стоит
 * запроса, повторённая команда — выполненного дважды действия, и
 * обезвредить её сегодня нечем — ни имени ключа идемпотентности, ни
 * энфорсера у владельцев не построено
 * (docs/architecture/contracts.md §«Периметр повторяет только чтения»).
 *
 * <p><b>Повтор — только на отказе транспорта.</b> Ответ владельца,
 * каким бы он ни был, есть его решение и повтору не подлежит: периметр
 * решений не принимает и чужие не переигрывает.
 *
 * <p><b>Тело пересылается как есть, байтами.</b> Разбирать его периметру
 * незачем: контракт остаётся у владельца и версионируется вместе с ним,
 * а разбор завёл бы вторую его запись.
 */
@Slf4j
@Component
public class OwnerProxyClient {

    private final RestClient restClient;
    private final OwnerAddressResolver addressResolver;
    private final PerimeterProperties properties;

    /**
     * Клиент собирается ОДИН РАЗ. Сборка на каждый запрос заводила бы
     * свою фабрику соединений на вызов — то есть пул на один запрос, и
     * периметр, через который идёт весь трафик браузера, платил бы за
     * соединение каждой пересылкой.
     */
    public OwnerProxyClient(RestClient.Builder builder, OwnerAddressResolver addressResolver,
                            PerimeterProperties properties) {
        this.restClient = builder.build();
        this.addressResolver = addressResolver;
        this.properties = properties;
    }

    /**
     * Переслать запрос владельцу и вернуть его ответ как есть.
     *
     * @param owner   имя единицы-владельца, выведенное из первого сегмента пути
     * @param method  глагол запроса
     * @param path    путь и строка запроса, как их прислал браузер
     * @param headers заголовки, которые периметр передаёт дальше
     * @param body    тело запроса; пусто у чтений
     * @return ответ владельца: статус, заголовки содержимого и тело
     */
    public ResponseEntity<byte[]> forward(String owner, HttpMethod method, String path,
                                          HttpHeaders headers, byte[] body) {
        URI uri = URI.create(addressResolver.baseUrlOf(owner) + path);
        Integer attempts = isRetryable(method) ? properties.getReadRetries() + 1 : 1;
        ResourceAccessException lastFailure = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return call(method, uri, headers, body);
            } catch (ResourceAccessException failure) {
                lastFailure = failure;
                log.warn("The owner did not answer owner={} method={} attempt={}", owner, method, attempt, failure);
            }
        }
        throw new PeerServiceUnavailableException("Владелец недоступен: " + owner, lastFailure);
    }

    /**
     * Повторяемость глагола: читающие безопасны по определению, всё
     * прочее меняет состояние владельца.
     */
    private Boolean isRetryable(HttpMethod method) {
        return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);
    }

    private ResponseEntity<byte[]> call(HttpMethod method, URI uri, HttpHeaders headers, byte[] body) {
        RestClient.RequestBodySpec request = restClient
                .method(method)
                .uri(uri)
                .headers(target -> target.addAll(headers));
        if (isFalse(isEmptyBody(body))) {
            request = request.body(body);
        }
        return request.retrieve()
                .onStatus(status -> true, (ignoredRequest, ignoredResponse) -> {
                })
                .toEntity(byte[].class);
    }

    /** Пустое тело — отсутствие тела, а не тело нулевой длины. */
    private Boolean isEmptyBody(byte[] body) {
        return isNull(body) || body.length == 0;
    }
}
