package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingcore.TradingCoreApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

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
 * там пустым значением, и контекст упал бы «не по той причине» — то есть
 * клетка была бы зелёной, ничего о контуре доступа не утверждая.
 *
 * <p><b>Клетка красна по построению, и долг добыт этим прогоном.</b> Дом
 * величины ({@code application.yaml}, комментарий оси {@code issuer-uri}:
 * «пустое означает, что контур доступа не настроен, и поверхность не
 * поднимется — это отказ») дерево кода не несёт: контекст поднимается,
 * Tomcat слушает, проба живости отвечает. Ожидание под текущий факт не
 * ослаблено — оно и есть предъявление долга
 * (.claude/work/backlog.md §«Пустой издатель у ядра поверхность
 * поднимает»).
 */
class UnconfiguredAccessContourTest {

    @Test
    @Tag("debt")
    @DisplayName("B13.1 — ненастроенный контур доступа не поднимает поверхности")
    void anUnconfiguredAccessContourRaisesNoSurface() {
        Boolean rose = Boolean.FALSE;
        ConfigurableApplicationContext context = null;
        try {
            context = new SpringApplicationBuilder(TradingCoreApplication.class)
                    .run(argumentsWithoutIssuer());
            rose = Boolean.TRUE;
        } catch (RuntimeException refused) {
            rose = Boolean.FALSE;
        } finally {
            // Поднявшийся контекст закрывается в любом исходе: оставленный
            // живым, он держал бы порт и пул соединений до конца прогона.
            if (context != null) {
                context.close();
            }
        }

        assertThat(rose).isFalse();
    }

    private String[] argumentsWithoutIssuer() {
        List<String> arguments = new ArrayList<>();
        for (Map.Entry<String, String> axis : TradingCoreSubstrate.defaults().entrySet()) {
            arguments.add("--" + axis.getKey() + "=" + axis.getValue());
        }
        arguments.add("--spring.security.oauth2.resourceserver.jwt.issuer-uri=");
        arguments.add("--server.port=0");
        return arguments.toArray(new String[0]);
    }
}
