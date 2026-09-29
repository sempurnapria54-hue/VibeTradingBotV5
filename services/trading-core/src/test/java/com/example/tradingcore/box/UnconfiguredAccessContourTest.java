package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.example.tradingcore.TradingCoreApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Ненастроенный контур доступа — клетка {@code B13.1}.
 *
 * <p><b>Без {@code @SpringBootTest}, и это следствие предмета:</b>
 * ожидание клетки — что контекст НЕ поднимается, а поднятый контекст есть
 * предусловие всякого кейса ящика (решение 1). Поэтому подъём здесь —
 * вход, и производит его сам кейс.
 *
 * <p><b>Оси подаются АРГУМЕНТАМИ, а не {@code properties(…)}.</b>
 * {@code properties(…)} кладёт умолчания, а они НИЖЕ {@code application.yaml}
 * приложения: адрес базы, поданный умолчанием, перекрылся бы объявленным
 * там пустым значением, и контекст упал бы «не по той причине».
 *
 * <p><b>Пустой издатель ЗАМЕНЯЕТ ось субстрата, а не дописывается вторым
 * аргументом.</b> Повторённый ключ командной строки Spring склеивает через
 * запятую: {@code --issuer-uri=<стаб>} и {@code --issuer-uri=} дают
 * непустое {@code "<стаб>,"}, декодер собирается по нему лениво, и
 * контекст поднимается — клетка краснела, мерив не пустой издатель, а
 * склейку.
 *
 * <p><b>Причина отказа пинится.</b> Пустой издатель гасит условие
 * автоконфигурации декодера, и цепочка фильтров, требующая бин
 * {@link JwtDecoder}, не собирается: отказ — отсутствие ровно этого бина.
 * Засчитанный любой отказ позеленил бы клетку и на недоступной базе.
 */
class UnconfiguredAccessContourTest {

    private static final String ISSUER_KEY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    @Test
    @DisplayName("B13.1 — ненастроенный контур доступа не поднимает поверхности")
    void anUnconfiguredAccessContourRaisesNoSurface() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(TradingCoreApplication.class)
                .run(argumentsWithoutIssuer())
                .close())
                .as("незаданный издатель означает отказ подъёма, а не открытую поверхность")
                .rootCause()
                .asInstanceOf(type(NoSuchBeanDefinitionException.class))
                .extracting(NoSuchBeanDefinitionException::getBeanType)
                .isEqualTo(JwtDecoder.class);
    }

    private String[] argumentsWithoutIssuer() {
        Map<String, String> axes = new LinkedHashMap<>(TradingCoreSubstrate.defaults());
        axes.put(ISSUER_KEY, "");
        axes.put("server.port", "0");
        return axes.entrySet().stream()
                .map(axis -> "--" + axis.getKey() + "=" + axis.getValue())
                .toArray(String[]::new);
    }
}
