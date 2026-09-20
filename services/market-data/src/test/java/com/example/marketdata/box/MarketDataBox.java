package com.example.marketdata.box;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Чёрный ящик `market-data`: реальный контекст сервиса со ВСЕМИ его
 * бинами, наблюдаемый только снаружи — своей поверхностью, базой, стабом
 * коннектора и журналом приложения
 * (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Признак ящика — не форма запуска, а то, что ни один внутренний
 * бин не подменён.</b> Отсюда и форма обращения: свой HTTP-клиент по
 * адресу случайного порта, а не {@code MockMvc} и не автовайренный
 * {@code CandleLoader}. Наблюдатели субстрата ({@link Rows},
 * {@link ConnectorStub}) ходят своим соединением — ящик смотрит на
 * субстрат снаружи.
 *
 * <p><b>ТИК — ВХОД, и это новая ось формы у этого предмета.</b> Пять
 * проходов по расписанию производят почти всё, что сервис производит, и
 * подаёт их кейс сам — ручным фасадом ({@link Tick}). Фасад асинхронен,
 * поэтому след тика ждётся его собственной записью в журнале, а не
 * {@code Thread.sleep}: пауза платит временем всегда и ничего не
 * гарантирует.
 *
 * <p><b>Возраст данных ставится В ДАННЫХ.</b> Часы процесса не двигаются
 * ни в одном кейсе: свеча приезжает с нужным открытием бара, значение —
 * с нужной меткой свечи, срез — со своей биржевой меткой. Срок свежести
 * при этом приезжает операндом чтения и потому является входом дважды.
 *
 * <p><b>Свойств контекста здесь не объявлено ни одного, и это несущее.</b>
 * Перечень свойств задаёт КАЖДЫЙ класс кейсов своим методом
 * {@code @DynamicPropertySource}: положение осей конфигурации есть ВХОД
 * ящика, а унаследованный метод реестра перекрывал бы свои
 * переопределения в порядке, которого контракт реестра не обещает.
 * Цена названа — контекст на класс конфигурации, а не один на прогон;
 * классы, которым довольно штатного положения осей, берут его
 * {@link SharedMarketDataBox} и делят один контекст.
 *
 * <p><b>База опустошается перед каждой клеткой.</b> Вход клетки есть её
 * собственное состояние каталога и рядов, и наследство соседки сделало бы
 * отрицания («строк не прибавилось», «запросов нет») утверждениями о
 * прогоне, а не о клетке.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class MarketDataBox {

    /** Поверхность требований потребителя. */
    protected static final String REQUIREMENTS = "/api/v1/market-data/requirements";

    /** Поверхность каталога и собранной истории. */
    protected static final String INSTRUMENTS = "/api/v1/market-data/instruments";

    /** Инструмент, которым ходит большинство кейсов. */
    protected static final String INSTRUMENT = "BTC-USDT-SWAP";

    /** Второй инструмент: им наблюдается поинструментный обход. */
    protected static final String SECOND_INSTRUMENT = "ETH-USDT-SWAP";

    /** Третий инструмент: им наблюдается прекращение обхода на середине. */
    protected static final String THIRD_INSTRUMENT = "SOL-USDT-SWAP";

    /** Таймфрейм, которым ходит большинство кейсов. */
    protected static final String HOUR = "ONE_HOUR";

    /** Длительность часового бара в миллисекундах. */
    protected static final Long HOUR_MILLIS = 3_600_000L;

    /** Потолок ожидания следа тика: он асинхронен, но не бесконечен. */
    private static final Duration TICK_TIMEOUT = Duration.ofSeconds(60);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private Integer port;

    /** Наблюдатель строк базы субстрата. */
    protected final Rows rows = Rows.shared();

    /** Стаб и наблюдатель поверхности коннектора. */
    protected final ConnectorStub connector = ConnectorStub.stub();

    /** Стаб провайдера идентичности: им подписываются токены кейсов. */
    protected final IdentityStub identity = IdentityStub.stub();

    /** Стабы соседей по ярусу: ими наблюдается, что их не зовут. */
    protected final List<NeighbourStub> neighbours = NeighbourStub.all();

    /**
     * Перед каждой клеткой: база пуста, стабы забыли записи.
     *
     * <p>Опустошение идёт БЕЗ сброса последовательностей — довод у
     * {@link Rows#clear()}.
     */
    @BeforeEach
    void resetSubstrate() {
        rows.clear();
        connector.reset();
        neighbours.forEach(NeighbourStub::reset);
    }

    /**
     * Заводит инструменты каталога ТРОПОЙ ЯЩИКА и отдаёт их идентичности
     * в порядке имён.
     *
     * <p><b>Предусловие ставится тиком синка, а не вставкой в базу:</b>
     * иначе кейс опирался бы на строку, которой сервис не производил, и
     * зелёный прогон говорил бы о нашей вставке, а не о его поведении.
     *
     * <p><b>Записи стаба после этого забываются.</b> Чтения предусловия
     * ушли в тот же журнал, что и чтения клетки, и без разделения
     * отрицание «запросов нет» не сошлось бы никогда.
     *
     * <p><b>Тиков здесь ДВА, и второй несущий.</b> Обход правил идёт
     * окном ЗА КУРСОРОМ, и первый тик оставляет курсор на последнем
     * заведённом инструменте — тик самой клетки застал бы пустое окно и
     * не обошёл бы никого. Второй тик это пустое окно и проходит,
     * возвращая курсор в начало круга: предусловие кончается там, где
     * круг обхода начинается.
     *
     * @param externalIds имена инструментов у площадки
     * @return их межсервисные идентификаторы в том же порядке
     */
    protected List<String> provisionInstruments(String... externalIds) {
        String[] listed = new String[externalIds.length];
        for (int index = 0; index < externalIds.length; index++) {
            listed[index] = Feed.instrument(externalIds[index], "BASE" + index, "USDT");
            connector.answers(ConnectorStub.rulesOf(externalIds[index]), Feed.rules(externalIds[index]));
        }
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(listed));
        tick(Tick.INSTRUMENT_SYNC);
        tick(Tick.INSTRUMENT_SYNC);
        connector.forgetRequests();
        return List.of(externalIds).stream().map(this::instrumentInternalId).toList();
    }

    /** Идентичность инструмента каталога по его имени у площадки. */
    protected String instrumentInternalId(String externalId) {
        Object internalId = rows.row("instruments", "external_id", externalId).get("internal_id");
        if (Objects.isNull(internalId)) {
            throw new AssertionError("Предусловие не поставлено: инструмента " + externalId + " в каталоге нет");
        }
        return String.valueOf(internalId);
    }

    /** Числовой ключ инструмента каталога: им ставятся предусловия без тропы. */
    protected Long instrumentId(String externalId) {
        return ((Number) rows.row("instruments", "external_id", externalId).get("id")).longValue();
    }

    /**
     * Заводит единицу сбора ТРОПОЙ ЯЩИКА — требованием потребителя.
     *
     * @param instrumentInternalId идентичность инструмента
     * @param timeframe            таймфрейм единицы сбора
     * @param depthBars            заказанная глубина истории
     * @return идентичность заведённой единицы сбора
     */
    protected String requireCandles(String instrumentInternalId, String timeframe, Long depthBars) {
        Answer answer = post(REQUIREMENTS + "/candles",
                Bodies.candleRequirement(instrumentInternalId, timeframe, depthBars));
        if (answer.status() != 200) {
            throw new AssertionError("Предусловие не поставлено: требование свечей ответило "
                    + answer.status() + " " + answer.body());
        }
        return String.valueOf(answer.asObject().get("internalId"));
    }

    /**
     * Заводит идентичность вычисления индикатора ТРОПОЙ ЯЩИКА.
     *
     * @param indicatorType тип индикатора
     * @param timeframe     таймфрейм серии
     * @param params        тело параметров как есть
     * @return идентичность заведённого вычисления
     */
    protected String requireIndicator(String indicatorType, String timeframe, String params) {
        Answer answer = post(REQUIREMENTS + "/indicators",
                Bodies.indicatorRequirement(indicatorType, timeframe, params));
        if (answer.status() != 200) {
            throw new AssertionError("Предусловие не поставлено: требование индикатора ответило "
                    + answer.status() + " " + answer.body());
        }
        return String.valueOf(answer.asObject().get("internalId"));
    }

    /**
     * Заводит идентичность вычисления структуры рынка ТРОПОЙ ЯЩИКА.
     *
     * @param timeframe                       таймфрейм серии
     * @param params                          тело параметров как есть
     * @param efficiencyRatioConfigInternalId идентичность входного ER либо {@code null}
     * @param atrConfigInternalId             идентичность входного ATR либо {@code null}
     * @return идентичность заведённого вычисления
     */
    protected String requireStructure(String timeframe, String params,
                                      String efficiencyRatioConfigInternalId, String atrConfigInternalId) {
        Answer answer = post(REQUIREMENTS + "/market-structures", Bodies.structureRequirement(
                timeframe, params, efficiencyRatioConfigInternalId, atrConfigInternalId));
        if (answer.status() != 200) {
            throw new AssertionError("Предусловие не поставлено: требование структуры ответило "
                    + answer.status() + " " + answer.body());
        }
        return String.valueOf(answer.asObject().get("internalId"));
    }

    /**
     * Открытие бара, лежащего названное число баров назад от момента
     * прогона и выровненного по длительности бара.
     *
     * <p><b>Возраст ставится В ДАННЫХ, а не сдвигом часов:</b> часы
     * процесса не двигаются ни в одном кейсе.
     *
     * @param stepMillis длительность бара
     * @param barsBack   сколько баров назад
     * @return открытие бара в миллисекундах эпохи
     */
    protected static Long barsAgo(Long stepMillis, Integer barsBack) {
        long now = System.currentTimeMillis();
        return (now / stepMillis) * stepMillis - barsBack * stepMillis;
    }

    /** Пять проходов по расписанию: путь ручного фасада и след его конца. */
    protected enum Tick {

        /** Синк листинга и справочных правил. */
        INSTRUMENT_SYNC("/instrument-sync", "Manual instrument sync job trigger finished"),

        /** Загрузка свечей по единицам сбора. */
        CANDLES("/candles", "Manual candle job trigger finished"),

        /** Расчёт индикаторов по заказанным идентичностям. */
        INDICATORS("/indicators", "Manual indicator job trigger finished"),

        /** Расчёт структуры рынка по заказанным идентичностям. */
        MARKET_STRUCTURES("/market-structures", "Manual market structure job trigger finished"),

        /** Проход сбора невосполнимых срезов. */
        SNAPSHOTS("/snapshots", "Manual snapshot collection job trigger finished");

        private final String suffix;
        private final String finishedMark;

        Tick(String suffix, String finishedMark) {
            this.suffix = suffix;
            this.finishedMark = finishedMark;
        }

        /** Путь ручного запуска. */
        String path() {
            return "/api/v1/market-data/jobs" + suffix;
        }

        /** Запись фасада о конце запуска: ею наблюдается, что тик отработал. */
        String finishedMark() {
            return finishedMark;
        }
    }

    /**
     * Подаёт тик ручным фасадом и ждёт, пока он отработает.
     *
     * <p><b>Ждётся запись ФАСАДА о конце запуска, а не пауза.</b> Фасад
     * асинхронен, и его ответ {@code 202} говорит только о запуске
     * (docs/rules/error-handling-policy.md); след работы наблюдается тем,
     * что фасад дописал в журнал.
     *
     * @param tick какой из пяти проходов подаётся
     * @return ответ фасада на запуск
     */
    protected Answer tick(Tick tick) {
        Integer mark = AppLog.mark();
        Answer answer = post(tick.path(), "");
        awaitFinished(tick, mark, 1);
        return answer;
    }

    /**
     * Подаёт тик названное число раз подряд, ждёт конца каждого.
     *
     * @param tick  какой из пяти проходов подаётся
     * @param times сколько раз подряд
     */
    protected void ticks(Tick tick, Integer times) {
        for (int index = 0; index < times; index++) {
            tick(tick);
        }
    }

    /**
     * Подаёт два запуска подряд, НЕ дожидаясь конца первого: вход клеток о
     * перекрытии. Ожидание конца обоих — после подачи.
     */
    protected void overlappingTicks(Tick tick) {
        Integer mark = AppLog.mark();
        post(tick.path(), "");
        post(tick.path(), "");
        awaitFinished(tick, mark, 2);
    }

    private void awaitFinished(Tick tick, Integer mark, Integer times) {
        Awaitility.await().atMost(TICK_TIMEOUT).pollInterval(Duration.ofMillis(50))
                .until(countOf(tick, mark), count -> count >= times);
    }

    private Callable<Integer> countOf(Tick tick, Integer mark) {
        return () -> {
            String written = AppLog.since(mark);
            int count = 0;
            int at = written.indexOf(tick.finishedMark());
            while (at >= 0) {
                count++;
                at = written.indexOf(tick.finishedMark(), at + 1);
            }
            return count;
        };
    }

    /** Чтение под сервисным токеном. */
    protected Answer get(String path) {
        return send(authorized(request(path)).GET());
    }

    /** Чтение без предъявленного принципала. */
    protected Answer getAnonymously(String path) {
        return send(request(path).GET());
    }

    /** Чтение под названным токеном. */
    protected Answer getWith(String path, String token) {
        return send(request(path).header("Authorization", "Bearer " + token).GET());
    }

    /** Чтение под сервисным токеном с дополнительным заголовком. */
    protected Answer getWithHeader(String path, String header, String value) {
        return send(authorized(request(path)).header(header, value).GET());
    }

    /** Мутирующий вызов под сервисным токеном. */
    protected Answer post(String path, String body) {
        return send(authorized(request(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Мутирующий вызов без предъявленного принципала. */
    protected Answer postAnonymously(String path, String body) {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Вызов неподдержанным методом под сервисным токеном. */
    protected Answer delete(String path) {
        return send(authorized(request(path)).DELETE());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60));
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder request) {
        return request.header("Authorization", "Bearer " + identity.serviceToken());
    }

    private static Answer send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> answer = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Answer(answer.statusCode(), answer.body());
        } catch (IOException failure) {
            throw new IllegalStateException("Поверхность ящика не ответила", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа поверхности прервано", failure);
        }
    }

    /**
     * Ответ поверхности: код и тело дословно.
     *
     * <p><b>Запись, а не разобранный объект:</b> часть кейсов утверждает о
     * ТЕЛЕ — что в нём нет числового ключа базы, нет сырого статуса, нет
     * значения исходящего токена, — и разбор в типизованную форму такие
     * ожидания выразить не даёт.
     *
     * @param status код ответа
     * @param body   тело ответа дословно
     */
    protected record Answer(Integer status, String body) {

        /** Тело как объект. */
        Map<String, Object> asObject() {
            return JsonParserFactory.getJsonParser().parseMap(body);
        }

        /** Тело как перечень объектов. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> asList() {
            return JsonParserFactory.getJsonParser().parseList(body).stream()
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }

        /** Единственный объект перечня. */
        Map<String, Object> single() {
            List<Map<String, Object>> items = asList();
            if (items.size() != 1) {
                throw new AssertionError("Ожидался ровно один объект, пришло " + items.size() + ": " + body);
            }
            return items.getFirst();
        }

        /** Вложенный объект тела по имени поля; пустая карта, когда его нет. */
        @SuppressWarnings("unchecked")
        Map<String, Object> nested(String field) {
            Object value = asObject().get(field);
            return Objects.isNull(value) ? Map.of() : (Map<String, Object>) value;
        }

        /**
         * Несёт ли тело единый error-DTO поверхности: класс отказа и
         * момент.
         *
         * <p>Форма читается по ПОЛЯМ, а не по коду ответа: контейнерный
         * отказ отдаёт своё тело с тем же кодом.
         */
        Boolean carriesErrorDto() {
            if (Objects.isNull(body) || body.isBlank()) {
                return Boolean.FALSE;
            }
            // Тело, которое объектом не является (перечень моделей, текст
            // контейнера), формы не несёт — и это ОТВЕТ клетки, а не её
            // отказ: иначе красная клетка падала бы разбором и переставала
            // называть, чем именно ожидание не сошлось.
            try {
                Map<String, Object> parsed = asObject();
                return parsed.containsKey("code") && parsed.containsKey("occurredAt");
            } catch (RuntimeException notAnObject) {
                return Boolean.FALSE;
            }
        }

        /** Класс отказа единого error-DTO. */
        String errorCode() {
            return String.valueOf(asObject().get("code"));
        }
    }
}
