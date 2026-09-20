package com.example.marketdata.box;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;

/**
 * Стабы поверхностей соседей по ярусу — наблюдатель отрицания
 * {@code B10.3}: «соседей по системе сервис не зовёт».
 *
 * <p><b>Стаб отвечает на что угодно и ничего не знает о market-data, и это
 * ровно предмет клетки.</b> Адрес ядра, стратегий, периметра и
 * авторизации сервису не задан ни одной осью конфигурации; поднятый и
 * отвечающий стаб делает отрицание наблюдаемым: будь у сервиса тропа к
 * соседу, попавшая туда мимо объявленных осей, запрос был бы записан.
 *
 * <p><b>Ограничение названо:</b> стаб ловит вызов, ушедший на ЕГО адрес, а
 * не всякий исходящий вызов вообще. Что исходящих адресов у процесса ровно
 * два — коннектор и точки провайдера идентичности, — читается
 * конфигурацией, а не этим наблюдателем.
 */
final class NeighbourStub {

    private static final List<NeighbourStub> ALL = List.of(
            new NeighbourStub("trading-core"),
            new NeighbourStub("strategies"),
            new NeighbourStub("bff"),
            new NeighbourStub("auth"));

    private final String name;
    private final WireMockServer server;

    private NeighbourStub(String name) {
        this.name = name;
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        this.server.start();
        this.server.stubFor(WireMock.any(WireMock.anyUrl()).willReturn(WireMock.okJson("{}")));
    }

    /** Все поднятые стабы соседей. */
    static List<NeighbourStub> all() {
        return ALL;
    }

    /** Имя соседа: им называется несошедшееся ожидание. */
    String name() {
        return name;
    }

    /** Адрес соседа: никакой осью конфигурации market-data он не задан. */
    String baseUrl() {
        return "http://localhost:" + server.port();
    }

    /** Забывает записи запросов; заготовка ответа остаётся. */
    void reset() {
        server.resetRequests();
    }

    /** Сколько запросов сосед получил с последнего сброса. */
    Integer count() {
        return server.getAllServeEvents().size();
    }
}
