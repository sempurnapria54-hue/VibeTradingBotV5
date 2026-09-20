package com.example.connector.okx.box;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Стаб поверхности соседа по ярусу — наблюдатель отрицания {@code B10.3}:
 * «соседей по системе сервис не зовёт».
 *
 * <p><b>Стаб отвечает на что угодно и ничего не знает о коннекторе, и это
 * ровно предмет клетки.</b> Адрес соседа коннектору не задан ни одной
 * осью конфигурации; поднятый и отвечающий стаб делает отрицание
 * наблюдаемым: будь у сервиса тропа к соседу, попавшая сюда мимо
 * объявленных осей, запрос был бы записан.
 *
 * <p><b>Ограничение названо:</b> стаб ловит вызов, ушедший на ЕГО адрес, а
 * не всякий исходящий вызов вообще. Что исходящих адресов у процесса ровно
 * три, читается конфигурацией, а не этим наблюдателем.
 */
final class NeighbourStub {

    private static final NeighbourStub INSTANCE = new NeighbourStub();

    private final WireMockServer server;

    private NeighbourStub() {
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        this.server.start();
        this.server.stubFor(WireMock.any(WireMock.anyUrl()).willReturn(WireMock.okJson("{}")));
    }

    static NeighbourStub stub() {
        return INSTANCE;
    }

    /** Адрес соседа: никакой осью конфигурации коннектору он не задан. */
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
