package com.example.connector.okx.unit.mapping;

import com.example.connector.okx.integration.external.api.model.okx.response.OkxApiResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;

/**
 * Сериализатор провода для групп {@code -J} документа
 * {@code .claude/tests/cases/okx-mapping.md}.
 *
 * <p><b>Берётся тот же конвертер, каким разбирает ответ клиент
 * площадки, а не собранный руками объект.</b> Клиент собирается
 * {@code RestClient.Builder}'ом, и тело ответа читает конвертер
 * сообщений Spring; в дереве зависимостей лежат обе линии
 * сериализатора, и конвертеры Spring 7 выбирают третью
 * ({@code .claude/rules/tech-radar.md}, строка Jackson 3). Кейс,
 * собранный на второй линии, доказал бы бинд, которого на проводе
 * нет — а ровно этого класса дефект группы {@code U2} и ловит:
 * выводимое имя свойства у поля {@code sCode} с ключом площадки не
 * совпадает, и поле биндится в пустоту.
 */
final class WireJson {

    private static final JacksonJsonHttpMessageConverter CONVERTER = new JacksonJsonHttpMessageConverter();

    private WireJson() {
    }

    /** Тело элемента ответа → разобранная форма источника. */
    @SuppressWarnings("unchecked")
    static <T> T read(String body, Class<T> form) {
        return (T) read(body, ResolvableType.forClass(form));
    }

    /** Тело ответа площадки → конверт с объектными элементами. */
    @SuppressWarnings("unchecked")
    static <T> OkxApiResponse<T> envelope(String body, Class<T> element) {
        return (OkxApiResponse<T>) read(body,
                ResolvableType.forClassWithGenerics(OkxApiResponse.class, element));
    }

    /** Тело ответа площадки → конверт с позиционными элементами (свеча). */
    @SuppressWarnings("unchecked")
    static OkxApiResponse<List<String>> positionalEnvelope(String body) {
        ResolvableType element = ResolvableType.forClassWithGenerics(List.class, String.class);
        return (OkxApiResponse<List<String>>) read(body,
                ResolvableType.forClassWithGenerics(OkxApiResponse.class, element));
    }

    private static Object read(String body, ResolvableType type) {
        MockHttpInputMessage message = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        message.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            return CONVERTER.read(type, message, null);
        } catch (IOException failure) {
            throw new IllegalStateException("тело провода не прочитано: " + body, failure);
        }
    }
}
