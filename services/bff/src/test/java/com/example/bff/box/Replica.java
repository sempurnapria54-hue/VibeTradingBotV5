package com.example.bff.box;

import static java.util.Objects.nonNull;

import com.example.bff.BffApplication;
import com.example.bff.domain.jobs.StreamPulseJob;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Реплика периметра, которую клетка поднимает сама: процесс той же формы,
 * что и контекст ящика, на тех же осях субстрата.
 *
 * <p><b>Зачем клетке своя реплика, называет клетка:</b> момент подъёма
 * ({@link ReplicaStartBoxTest}) либо набор подписок, в котором нет ни
 * одной брошенной ({@link FreshReplicaPulseBoxTest}).
 *
 * <p><b>Оси подаются АРГУМЕНТАМИ запуска, а не умолчаниями</b>: умолчания
 * стоя́т ниже {@code application.yaml} приложения, где адреса объявлены
 * пустыми, и реплика не поднялась бы вовсе (ловушка TC-036).
 */
final class Replica implements AutoCloseable {

    private final ConfigurableApplicationContext context;
    private final Subscription probe;

    private Replica(ConfigurableApplicationContext context, Subscription probe) {
        this.context = context;
        this.probe = probe;
    }

    /**
     * Реплика с ОТКРЫТЫМ зондом назначения: слушатель уже раздаёт, а
     * брошенных подписок в наборе нет ни одной.
     *
     * @param box       ящик клетки: им ставится зонд
     * @param overrides оси, которые клетка сдвигает
     */
    static Replica delivering(BffBox box, Map<String, String> overrides) {
        ConfigurableApplicationContext context = launch(overrides);
        return new Replica(context, box.deliveringProbeAt(portOf(context)));
    }

    /**
     * Поднимает процесс реплики.
     *
     * @param overrides оси, которые клетка сдвигает
     * @return поднятая реплика; закрывает её вызывающий
     */
    static ConfigurableApplicationContext launch(Map<String, String> overrides) {
        Map<String, String> properties = new LinkedHashMap<>(BffSubstrate.defaults());
        properties.putAll(overrides);
        properties.put("server.port", "0");
        // Остановка без ожидания открытых запросов: провод подписки — запрос,
        // который не кончается сам, и плавная остановка ждала бы его до
        // своего срока. Предмет клеток — не форма остановки.
        properties.put("server.shutdown", "immediate");
        String[] arguments = properties.entrySet().stream()
                .map(axis -> "--" + axis.getKey() + "=" + axis.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(BffApplication.class).run(arguments);
    }

    /** Порт поднятой реплики. */
    static Integer portOf(ConfigurableApplicationContext replica) {
        return ((WebServerApplicationContext) replica).getWebServer().getPort();
    }

    /** Порт этой реплики. */
    Integer port() {
        return portOf(context);
    }

    /**
     * Тик пульса этой реплики — ВХОД клетки: прямой вызов метода джобы есть
     * единственная точка, где кейс уровня 1 касается бина
     * (.claude/decisions/test-contour-design-pass.md, решение 6).
     */
    StreamPulseJob pulse() {
        return context.getBean(StreamPulseJob.class);
    }

    @Override
    public void close() {
        if (nonNull(probe)) {
            probe.close();
        }
        context.close();
    }
}
