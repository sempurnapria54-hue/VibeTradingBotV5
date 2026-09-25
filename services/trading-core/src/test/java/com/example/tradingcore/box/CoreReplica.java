package com.example.tradingcore.box;

import com.example.tradingcore.TradingCoreApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Процесс ядра, который клетка поднимает сама: той же формы, что контекст
 * ящика, на тех же осях субстрата — база, брокер, стабы соседей, провайдер
 * идентичности.
 *
 * <p><b>Зачем клетке свой процесс, называет клетка:</b> перезапуск
 * ({@code B3.15}) выражается процессом, у которого памяти прежнего нет, а
 * есть только то, что прежний оставил в базе.
 *
 * <p><b>Группа потребителя и тема владельца определений — свои</b>, по
 * доводу шапки {@link TradingCoreSubstrate}: живой контекст ящика не
 * закрывается, и общее имя группы делило бы партии его темы.
 *
 * <p><b>Оси подаются АРГУМЕНТАМИ запуска, а не умолчаниями</b>: умолчания
 * стоя́т ниже {@code application.yaml} приложения, где адреса объявлены
 * пустыми, и процесс не поднялся бы вовсе.
 */
final class CoreReplica implements AutoCloseable {

    private final ConfigurableApplicationContext context;

    private CoreReplica(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /**
     * Поднимает процесс на штатном положении осей.
     *
     * @param name имя процесса: им названы его группа потребителя и тема
     * @return поднятый процесс; закрывает его вызывающий
     */
    static CoreReplica launch(String name) {
        Map<String, String> properties = new LinkedHashMap<>(TradingCoreSubstrate.defaults());
        properties.put(TradingCoreSubstrate.CONSUMER_GROUP_KEY, name);
        properties.put(TradingCoreSubstrate.STRATEGY_TOPIC_KEY, TradingCoreSubstrate.ownStrategyTopic(name));
        properties.put("server.port", "0");
        properties.put("server.shutdown", "immediate");
        String[] arguments = properties.entrySet().stream()
                .map(axis -> "--" + axis.getKey() + "=" + axis.getValue())
                .toArray(String[]::new);
        return new CoreReplica(new SpringApplicationBuilder(TradingCoreApplication.class).run(arguments));
    }

    /** Порт поверхности процесса: им подаётся тик его фасадом. */
    Integer port() {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @Override
    public void close() {
        context.close();
    }
}
