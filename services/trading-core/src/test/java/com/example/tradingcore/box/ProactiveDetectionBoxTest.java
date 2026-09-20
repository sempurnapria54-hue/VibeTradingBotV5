package com.example.tradingcore.box;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B7} — проактивная детекция: что ищет и чем отвечает.
 *
 * <p><b>Предмет группы — ПРИЗНАК детектора и его исход.</b> Состав и
 * порядок полной реакции ступени — предмет группы {@code B5}; какие пары
 * «класс × радиус» принимает ручная поверхность — предмет {@code B6}.
 * Здесь наблюдается, на каком срезе детектор срабатывает, каким кодом
 * отчитывается и какую ступень запрашивает
 * (.claude/tests/cases/trading-core.md §«Объём»).
 *
 * <p><b>Вход у всех клеток один — ручной тик детекции</b>, а предусловие
 * ставится СРЕЗОМ счёта у стаба коннектора: три счёт-широкие выборки —
 * позиции, живые заявки, живые отдельные условные
 * ({@code AnomalyScanReader#read}). Срез задаётся ЦЕЛИКОМ: на незаданной
 * выборке стаб отвечает отказом, проход становится неполным, и клетка
 * наблюдала бы молчание гейта вместо признака детектора.
 *
 * <p><b>Гистерезис — вход клеток, а не помеха.</b> Детектор, чей признак
 * производит наш собственный незавершённый ход, ступени на первом тике не
 * запрашивает: он заводит наблюдательную строку, и подтверждением служит
 * она же — заведённая в окне наблюдения и не позже, чем разрешает
 * минимальный возраст подтверждения
 * (docs/components/AnomalyJob.md §«Такт и гистерезис»). Отсюда у таких
 * клеток два тика, а между ними — {@link #ageObservations()}.
 *
 * <p><b>Прямая запись в предусловиях группы одна, и строк она не
 * заводит:</b> {@link #ageObservations()} отодвигает НАЗАД момент строки,
 * которую завёл сам сервис. Иначе второй тик обязан был бы отстоять от
 * первого на минимальный возраст подтверждения — тридцать секунд стенных
 * часов на каждую клетку, — а подменять эту величину конфигурацией
 * значило бы отнять предмет у клетки {@code B7.11}, которая ровно её и
 * мерит. Момент ставится В ДАННЫХ — так его и называет сам кейс.
 *
 * <p><b>Клетки, чей предмет есть положение оси конфигурации, живут своими
 * классами:</b> выключатель тика — {@link DisabledDetectionBoxTest}, окно
 * выборки контура — {@link DetectionContourWindowBoxTest}.
 */
class ProactiveDetectionBoxTest extends SharedTradingCoreBox {

    /** Основа идентичности определения группы. */
    private static final String DEFINITION = "S-SCAN";

    /** Биржевой момент, который отдаёт коннектор. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /** Последняя цена момента. */
    private static final String LAST_PRICE = "100";

    /** Биржевой момент открытия наблюдённого эпизода: половина его адреса. */
    private static final String POSITION_MOMENT = "2026-09-20T10:00:05Z";

    /** Отсутствие ступени: рабочее состояние радиуса. */
    private static final String NO_RUNG = "ACTIVE";

    /** Мягкая ступень биржевого счёта. */
    private static final String HOLD = "HOLD";

    /** Жёсткая ступень обоих радиусов: сворачивание. */
    private static final String TRADE_BLOCKED = "TRADE_BLOCKED";

    /** Мягкая ступень инструментного радиуса: запрет входов. */
    private static final String ENTRY_BLOCKED = "ENTRY_BLOCKED";

    /** Класс события подъёма ступени: его пишет ребро самого перехода. */
    private static final String HOLD_RAISED = "HOLD_RAISED";

    /** Класс события создания сделки: им наблюдается восстановительная тропа. */
    private static final String DEAL_OPENED = "DEAL_OPENED";

    /** Биржевое имя инструмента, строки которого в каталоге ядра нет вовсе. */
    private static final String FOREIGN_INSTRUMENT = "SOL-USDT-SWAP";

    /** Код живого риска по инструменту вне контура. */
    private static final String FOREIGN_INSTRUMENT_RISK = "EXCHANGE_FOREIGN_INSTRUMENT_RISK";

    /** Код нарушения режима позиций счёта. */
    private static final String POSITION_MODE_VIOLATION = "EXCHANGE_POSITION_MODE_VIOLATION";

    /** Код живой заявки без нашего маркера. */
    private static final String FOREIGN_ORDER = "EXCHANGE_FOREIGN_ORDER";

    /** Код хвостов заявок, не объяснимых живой сделкой. */
    private static final String ORPHAN_ORDERS = "INSTRUMENT_ORPHAN_ORDERS";

    /** Код локально терминальной сущности, живой на бирже. */
    private static final String TERMINAL_ALIVE = "LOCAL_TERMINAL_ALIVE_ON_EXCHANGE";

    /** Код непроэнфорсенной жёсткой ступени радиуса. */
    private static final String RUNG_NOT_ENFORCED = "SAFETY_RUNG_NOT_ENFORCED";

    /** Код ненаблюдённого прохода. */
    private static final String PASS_INCOMPLETE = "ANOMALY_PASS_INCOMPLETE";

    /**
     * Клиентский идентификатор НАШЕЙ заявки: маркер контура впереди
     * ({@code InternalIdFactory#isOurs}).
     */
    private static final String OUR_CLIENT_ID = "vtbboxorderone";

    /** Клиентский идентификатор ЧУЖОЙ заявки: маркера контура на нём нет. */
    private static final String FOREIGN_CLIENT_ID = "someone-else-1";

    @Test
    @DisplayName("B7.1 — активная позиция без объясняющей сделки заводит сделку восстановлением")
    void theUnexplainedLivePositionOpensARecoveryDeal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        standScan(Feed.array(livePositionOn(EXTERNAL_INSTRUMENT)), Feed.emptyArray(), Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);

        // Реакция на этот признак — не ступень, а заведение сделки ВОКРУГ
        // уже живого риска: без него найденный вне приложения риск
        // остаётся невидимым всему, что считает по сделке.
        assertThat(rows.count("deals")).isEqualTo(1L);
        Map<String, Object> deal = rows.all("deals").getFirst();
        assertThat(deal.get("entry_reason")).isEqualTo("RECOVERY");
        assertThat(deal.get("status")).isEqualTo("ACTIVE");
        // Закреплённой детали нет, и это значение, а не пробел: выбора
        // входа не было вовсе.
        assertThat(deal.get("strategy_detail_id")).isNull();
        // Транш сразу в сопровождении: штатные рёбра входа ему
        // недостижимы — заводил его не выбор входа.
        assertThat(rows.count("deal_tranches")).isEqualTo(1L);
        assertThat(rows.all("deal_tranches").getFirst().get("status")).isEqualTo("MANAGING");
        assertThat(eventTypes()).containsExactly(DEAL_OPENED);
        // Ступеней не поднято ни на одном радиусе: это принятие в модель,
        // а не чужая сущность.
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(rows.count("anomaly_reports")).isZero();
    }

    @Test
    @DisplayName("B7.2 — живой риск по инструменту вне контура сворачивает счёт")
    void theLiveRiskOutsideTheContourTearsTheAccountDown() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        standScan(Feed.array(livePositionOn(FOREIGN_INSTRUMENT)), Feed.emptyArray(), Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);

        // Гистерезиса у признака нет, и это не послабление: строка
        // инструмента нашим ходом не исчезает, гонка чтения его не
        // производит — реакция идёт с первого наблюдения.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(eventTypes()).contains(HOLD_RAISED);
        // Состав полной реакции отработал: отчёт критичной тропы доведён до
        // терминала и несёт оба снимка.
        Map<String, Object> report = rows.row("anomaly_reports", "code", FOREIGN_INSTRUMENT_RISK);
        assertThat(report.get("severity")).isEqualTo("CRITICAL");
        assertThat(report.get("status")).isEqualTo("COMPLETED");
        assertThat(report.get("internal_before")).isNotNull();
        assertThat(report.get("internal_after")).isNotNull();
        // Восстановительной тропы на этом признаке нет: строки инструмента
        // не существует, и приписать риск нечему.
        assertThat(rows.count("deals")).isZero();
    }

    @Test
    @DisplayName("B7.3 — чужая заявка опознаётся нашим маркером, а не отсутствием строки")
    void theForeignOrderIsToldByOurMarkerAndNotByAMissingRow() {
        openActiveDeal();
        // Половина (б) идёт ПЕРВОЙ: её предикат отрицательный, и после
        // поднятой половиной (а) ступени пустоту уже не отличить.
        standScan(Feed.emptyArray(),
                Feed.array(Feed.pendingOrder("ex-1", OUR_CLIENT_ID, EXTERNAL_INSTRUMENT)),
                Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);
        ageObservations();
        tick(Tick.ANOMALY_DETECTION);

        // Наша заявка, пережившая рестарт между отправкой и коммитом своей
        // строки, чужой не объявляется: дискриминатор — сторона БИРЖИ, а
        // не наличие строки у нас. Хвостом её не объявляет и соседний
        // детектор — пару объясняет живая сделка.
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(rows.count("anomaly_reports")).isZero();

        standScan(Feed.emptyArray(),
                Feed.array(Feed.pendingOrder("ex-2", FOREIGN_CLIENT_ID, EXTERNAL_INSTRUMENT)),
                Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);

        // Первый тик признак только наблюдает: строка заведена, ступени
        // нет — гонку чтения детектор переживает молча. Гистерезис ему
        // нужен потому, что наша рыночная закрывающая заявка висит в срезе
        // БЕЗ маркера: у эндпоинта закрытия позиции клиентского поля нет.
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(codesOfReports()).containsExactly(FOREIGN_ORDER);
        assertThat(rows.row("anomaly_reports", "code", FOREIGN_ORDER).get("severity"))
                .isEqualTo("NON_CRITICAL");

        ageObservations();
        tick(Tick.ANOMALY_DETECTION);

        // Подтверждённый признак сворачивает счёт: им распоряжается кто-то
        // ещё. Строк у детектора с жёсткой ступенью получается две —
        // наблюдение и эскалация, — и различает их критичность.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(codesOfReports()).containsExactly(FOREIGN_ORDER, FOREIGN_ORDER);
        assertThat(rows.allOrderedBy("anomaly_reports", "id").getLast().get("severity"))
                .isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("B7.4 — больше одной позиции на инструмент — нарушение режима счёта")
    void theSecondPositionOnOneInstrumentViolatesTheAccountMode() {
        openActiveDeal();
        standScan(Feed.array(livePositionOn(EXTERNAL_INSTRUMENT), secondLivePositionOn(EXTERNAL_INSTRUMENT)),
                Feed.emptyArray(), Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);

        // Признак читается ЦЕЛИКОМ со стороны биржи: вторую позицию по
        // одному инструменту наша команда не создаёт никогда, и
        // подтверждения следующим тиком он не требует.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(codesOfReports()).containsExactly(POSITION_MODE_VIOLATION);
        // Посылка детектора — кардинальность модели, а не перечень
        // инструментов: имя инструмента в каталоге есть, и детектор
        // чужого риска молчит.
        assertThat(codesOfReports()).doesNotContain(FOREIGN_INSTRUMENT_RISK);
    }

    @Test
    @DisplayName("B7.5 — хвосты заявок без позиции дают мягкую ступень пары")
    void theOrphanOrdersRaiseTheSoftRungOfThePair() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        standScan(Feed.emptyArray(),
                Feed.array(Feed.pendingOrder("ex-1", OUR_CLIENT_ID, EXTERNAL_INSTRUMENT)),
                Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);
        assertThat(pairRung(INSTRUMENT)).isEqualTo(NO_RUNG);
        ageObservations();
        tick(Tick.ANOMALY_DETECTION);

        // Ступень мягкая по оси: живого направленного риска у хвоста нет,
        // снимать нечего, а под сомнением наш учёт по инструменту — то
        // есть право заводить на нём новое.
        assertThat(pairRung(INSTRUMENT)).isEqualTo(ENTRY_BLOCKED);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        // Строка одна: наблюдательная и есть строка реакции — ключ дедупа
        // у мягкой ступени не меняется, критичность обеих одна.
        assertThat(codesOfReports()).containsExactly(ORPHAN_ORDERS);
        assertThat(rows.row("anomaly_reports", "code", ORPHAN_ORDERS).get("severity"))
                .isEqualTo("NON_CRITICAL");
        // Снятия риска не было: ни снятой заявки, ни закрытой позиции —
        // рвать у хвоста нечего. Инструмент у площадки при этом СПРОШЕН, и
        // спросил его снимок отчёта: у отчёта с известным инструментом
        // внешний снимок собирается чтением, и к снятию риска оно
        // отношения не имеет ({@code AnomalyReportService#externalSnapshot}).
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(closurePath(ACCOUNT))).isEmpty();
    }

    @Test
    @DisplayName("B7.6 — локально терминальная сущность, живая на бирже, даёт только отчёт")
    void theLocallyTerminalEntityAliveOnTheExchangeOnlyGetsAReport() {
        String clientId = standTerminalOrder();
        standScan(Feed.emptyArray(), Feed.array(Feed.pendingOrder("ex-1", clientId, EXTERNAL_INSTRUMENT)),
                Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);

        // Реакция журнальная: блокировки у неё нет ни на одном радиусе —
        // «мы её закрыли, а она жива» есть расхождение УЧЁТА, и рвать по
        // нему покрытый риск нечем.
        assertThat(codesOfReports()).contains(TERMINAL_ALIVE);
        assertThat(rows.row("anomaly_reports", "code", TERMINAL_ALIVE).get("severity"))
                .isEqualTo("NON_CRITICAL");
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(pairRung(INSTRUMENT)).isEqualTo(NO_RUNG);

        ageObservations();
        tick(Tick.ANOMALY_DETECTION);

        // Подтверждение признака ступени не приносит и на втором тике:
        // журнальная находка её не запрашивает вовсе, а стоящая строка
        // служит ей дедупом, а не операндом ступени.
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(pairRung(INSTRUMENT)).isEqualTo(NO_RUNG);
    }

    @Test
    @DisplayName("B7.8 — неполный срез заставляет прочие детекторы молчать")
    void theIncompleteSliceSilencesTheOtherDetectors() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        // Два среза из трёх добыты, и в добытом лежит признак, который
        // сработал бы с первого наблюдения.
        standScan(Feed.array(livePositionOn(FOREIGN_INSTRUMENT)), Feed.emptyArray(), null);

        tick(Tick.ANOMALY_DETECTION);

        // На неполном срезе расхождение производит сама неполнота, и
        // детекторы МОЛЧАТ: ложный триггер снял бы риск, которого нет.
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(codesOfReports()).containsExactly(PASS_INCOMPLETE);
        // Слепота счётна с первого же неполного прохода: «ничего не нашли»
        // и «не смотрели» различимы в данных.
        assertThat(blindPasses()).isEqualTo(1);
    }

    @Test
    @DisplayName("B7.9 — серия ненаблюдённых проходов поднимает мягкую ступень счёта")
    void theSeriesOfUnobservedPassesRaisesTheSoftAccountRung() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        standScan(Feed.emptyArray(), Feed.emptyArray(), null);

        ticks(Tick.ANOMALY_DETECTION, 2);

        // До предела ступени нет: молчание прочих детекторов записано, а
        // право набирать новый риск ещё не отнято.
        assertThat(blindPasses()).isEqualTo(2);
        assertThat(accountRung()).isEqualTo(NO_RUNG);

        standScan(Feed.emptyArray(), Feed.emptyArray(), Feed.emptyArray());
        tick(Tick.ANOMALY_DETECTION);

        // Наблюдённый проход обнуляет счёт: предел считает ПОДРЯД идущие.
        assertThat(blindPasses()).isZero();
        assertThat(accountRung()).isEqualTo(NO_RUNG);

        standScan(Feed.emptyArray(), Feed.emptyArray(), null);
        ticks(Tick.ANOMALY_DETECTION, 3);

        // Ступень мягкая, и это следствие оси, а не смягчение: биржевая
        // защита стои́т и исполняется, пока мы её не видим, — принятый риск
        // покрыт, и снимать его нечем.
        assertThat(blindPasses()).isEqualTo(3);
        assertThat(accountRung()).isEqualTo(HOLD);
        assertThat(codesOfReports()).containsExactly(PASS_INCOMPLETE);
    }

    @Test
    @DisplayName("B7.11 — свежая строка подтверждением признака не служит")
    void theFreshRowDoesNotServeAsConfirmation() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        standScan(Feed.emptyArray(),
                Feed.array(Feed.pendingOrder("ex-1", OUR_CLIENT_ID, EXTERNAL_INSTRUMENT)),
                Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);
        tick(Tick.ANOMALY_DETECTION);

        // Два прохода подряд отстоят друг от друга СЕКУНДАМИ, а гонка
        // чтения, против которой гистерезис заведён, живёт такт: строка
        // такого возраста подтверждением не служит, иначе ручной триггер
        // поверх планового тика «подтверждал» бы транзиторное расхождение.
        assertThat(pairRung(INSTRUMENT)).isEqualTo(NO_RUNG);
        assertThat(codesOfReports()).containsExactly(ORPHAN_ORDERS);

        ageObservations();
        tick(Tick.ANOMALY_DETECTION);

        // Тот же вход на строке старше порога даёт реакцию: различает их
        // ровно возраст стоящей строки.
        assertThat(pairRung(INSTRUMENT)).isEqualTo(ENTRY_BLOCKED);
    }

    @Test
    @DisplayName("B7.13 — жёсткая ступень без энфорсмента даёт отчёт, но не вторую реакцию")
    void theUnenforcedHardRungOnlyGetsAReport() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        fullHalt(ACCOUNT);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        Object rungStandingSince = accountRow().get("modified_at");
        Long raisedOnce = countEvents(HOLD_RAISED);
        standScan(Feed.emptyArray(),
                Feed.array(Feed.pendingOrder("ex-1", OUR_CLIENT_ID, EXTERNAL_INSTRUMENT)),
                Feed.emptyArray());
        PeerStub.all().forEach(PeerStub::forgetRequests);

        tick(Tick.ANOMALY_DETECTION);

        // Ступень детектор не запрашивает вовсе — запрашивать нечего, она
        // уже стои́т; реакция журнальная и некритичная.
        assertThat(codesOfReports()).contains(RUNG_NOT_ENFORCED);
        assertThat(rows.row("anomaly_reports", "code", RUNG_NOT_ENFORCED).get("severity"))
                .isEqualTo("NON_CRITICAL");
        // Второй реакции нет: статус не переставлялся, факта подъёма не
        // прибавилось, снятие риска этой тропой не гонялось.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(accountRow().get("modified_at")).isEqualTo(rungStandingSince);
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedOnce);
        // Снятие риска этой тропой не гоняется: ни снятой заявки, ни
        // закрытой позиции. Чтение инструмента у площадки сюда не входит —
        // его делает внешний снимок самого отчёта.
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(closurePath(ACCOUNT))).isEmpty();
    }

    // ------------------------------------------------------------------
    // Предусловия группы
    // ------------------------------------------------------------------

    /**
     * Срез счёта: три счёт-широкие выборки поимённо.
     *
     * <p><b>Задаются ВСЕ три, и это не формальность.</b> Незаданную
     * выборку стаб отвергает, отказ уходит в неполноту прохода, и прочие
     * детекторы молчат гейтом — то есть клетка наблюдала бы тишину гейта
     * вместо своего признака. Неполнота поэтому ставится НАЗВАННОЙ:
     * пустой аргумент означает «этот срез не добыт».
     *
     * @param positions  тело перечня позиций; пусто — срез не добыт
     * @param orders     тело перечня живых заявок; пусто — срез не добыт
     * @param algoOrders тело перечня живых отдельных условных; пусто — срез
     *                   не добыт
     */
    private void standScan(String positions, String orders, String algoOrders) {
        standSlice(positionsPath(ACCOUNT), positions);
        standSlice(pendingOrdersPath(ACCOUNT), orders);
        standSlice(pendingAlgoOrdersPath(ACCOUNT), algoOrders);
    }

    /**
     * Одна выборка среза: добытая либо недобытая.
     *
     * <p><b>Недобытая ставится отказом БЕЗ класса границы.</b> Отказ с
     * классом поднимает биржевую ступень 2 сам и до прохода не доходит
     * (docs/rules/controlled-exchange-exceptions.md); гейту полноты нужен
     * ровно тот класс, до которого граница не достаёт, — молчание соседа
     * по ярусу.
     */
    private void standSlice(String path, String body) {
        if (isNull(body)) {
            connector.answers(path, 503, "{\"message\": \"box stub silence\"}");
            return;
        }
        connector.answers(path, body);
    }

    /**
     * Отодвигает назад момент СТОЯЩИХ наблюдательных строк, чтобы
     * следующий тик читал их подтверждением признака.
     *
     * <p><b>Строк она не заводит и содержания их не трогает:</b> предмет
     * правки — только возраст строки, которую завёл сам сервис. Второй тик
     * иначе обязан был бы отстоять от первого на минимальный возраст
     * подтверждения, то есть платить тридцать секунд стенных часов за
     * каждую клетку с гистерезисом; подменять эту величину конфигурацией
     * нельзя — ровно её мерит {@code B7.11}.
     */
    private void ageObservations() {
        rows.put("update anomaly_reports set created_at = created_at - interval '2 minutes'");
    }

    /**
     * Активная сделка счёта: ею ставится операнд «пара объяснена живой
     * сделкой», без которого детекторы хвостов и непрошеной позиции
     * срабатывали бы на каждом штатном входе.
     *
     * <p><b>Заявок у сделки нет, и это названо:</b> команда площадке
     * требует ещё одного прохода, а предметом клеток группы она не
     * является.
     */
    private void openActiveDeal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithPrice(MarketPhase.Type.BULL_TREND.name(), LAST_PRICE));
        connector.answers(balancePath(ACCOUNT), balanceBody());
        connector.answers(positionPath(ACCOUNT), Feed.absent());
        connector.answers(closedPositionsPath(ACCOUNT), Feed.emptyArray());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND));
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(1L);
        assertThat(rows.all("deals").getFirst().get("status")).isEqualTo("ACTIVE");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Нога входа, доведённая до ТЕРМИНАЛА тропой ящика: создание →
     * подтверждённое размещение → добыча, на которой площадка предъявляет
     * её отменённой.
     *
     * <p><b>Отменённой, а не ошибочной, и различие несущее.</b> Терминал
     * через отказ границы поднял бы жёсткую биржевую ступень самим
     * предусловием (docs/rules/controlled-exchange-exceptions.md), и
     * клетка {@code B7.6}, утверждающая «ступеней не поднято», мерила бы
     * собственную постановку. Отмена же второго цикла добычи не
     * запускает: у родителя с нулевым наливом встроенная защита
     * отменяется вместе с ним
     * ({@code AttachedAlgoOrderStateResolver#resolve}).
     *
     * @return клиентский идентификатор терминальной ноги
     */
    private String standTerminalOrder() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        assertThat(put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("5", "10", "4")).status())
                .isEqualTo(200);
        connector.answers(feeRatePath(ACCOUNT), Feed.array(Feed.tradeFeeRate()));
        tick(Tick.TRADE_FEE_RATES);
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithPrice(MarketPhase.Type.BULL_TREND.name(), LAST_PRICE));
        connector.answers(balancePath(ACCOUNT), balanceBody());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND));
        tick(Tick.ENTRY_SCANNER);
        tick(Tick.DEAL_ORCHESTRATOR);
        tick(Tick.DEAL_ORCHESTRATOR);
        String clientId = String.valueOf(rows.all("orders").getFirst().get("internal_id"));
        connector.answers(placementPath(ACCOUNT), Feed.ack("ex-1", clientId));
        tick(Tick.DEAL_ORCHESTRATOR);
        connector.answers(lookupPath(ACCOUNT), Feed.order("ex-1", clientId, "CANCELED"));
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(rows.all("orders").getFirst().get("status")).isEqualTo("CANCELED");
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        PeerStub.all().forEach(PeerStub::forgetRequests);
        return clientId;
    }

    /** Живой эпизод позиции по названному биржевому имени. */
    private String livePositionOn(String externalInstrumentId) {
        return Feed.livePosition("ex-live-1", externalInstrumentId, "1", LAST_PRICE, POSITION_MOMENT);
    }

    /** Второй живой эпизод по тому же имени: свой адрес, свой момент. */
    private String secondLivePositionOn(String externalInstrumentId) {
        return Feed.livePosition("ex-live-2", externalInstrumentId, "2", LAST_PRICE,
                "2026-09-20T10:00:06Z");
    }

    /** Строка биржевого счёта, как её видит база. */
    private Map<String, Object> accountRow() {
        return rows.row("exchange_accounts", "internal_id", ACCOUNT);
    }

    /** Ступень счёта, как её видит база. */
    private String accountRung() {
        return String.valueOf(accountRow().get("safety_rung"));
    }

    /** Счёт подряд идущих ненаблюдённых проходов на строке счёта. */
    private Integer blindPasses() {
        return ((Number) accountRow().get("blind_pass_count")).intValue();
    }

    /** Ступень пары «счёт, инструмент»; строки пары нет — рабочее состояние. */
    private String pairRung(String instrumentInternalId) {
        List<Map<String, Object>> found = rows.select("select * from account_instrument_states"
                        + " where exchange_account_id = ? and instrument_id = ?",
                accountId(ACCOUNT), instrumentId(instrumentInternalId));
        return found.isEmpty() ? NO_RUNG : String.valueOf(found.getFirst().get("safety_rung"));
    }

    /** Машинные коды заведённых отчётов в порядке записи. */
    private List<String> codesOfReports() {
        return rows.allOrderedBy("anomaly_reports", "id").stream()
                .map(row -> String.valueOf(row.get("code")))
                .toList();
    }

    /** Классы событий строк outbox в порядке записи. */
    private List<String> eventTypes() {
        return rows.all("outbox_events").stream()
                .map(row -> String.valueOf(row.get("event_type")))
                .toList();
    }

    /** Сколько строк outbox несёт названный класс. */
    private Long countEvents(String eventType) {
        return eventTypes().stream().filter(eventType::equals).count();
    }

    /** Путь чтения связки фич момента у владельца рыночных данных. */
    private String featuresPath(String instrumentInternalId) {
        return PEER_INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }

    /** Корень путей счёта у коннектора. */
    private String accountPath(String accountInternalId) {
        return "/api/v1/accounts/" + accountInternalId;
    }

    /** Путь чтения снимка средств у коннектора. */
    private String balancePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/balance";
    }

    /** Путь приватного чтения ставок комиссии у коннектора. */
    private String feeRatePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/trade-fee-rates";
    }

    /** Путь размещения обычной заявки у коннектора. */
    private String placementPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders";
    }

    /** Первая нога лестницы добычи: поиск ноги по идентификатору. */
    private String lookupPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/lookup";
    }

    /** Путь снятия обычной заявки: первый ход снятия живого риска. */
    private String cancellationPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/cancellations";
    }

    /** Рыночное закрытие позиции: второй ход снятия живого риска. */
    private String closurePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/closures";
    }

    /** Живой эпизод позиции по инструменту: след хода снятия риска. */
    private String positionPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/instrument";
    }

    /** История закрытых эпизодов: нога 2 добычи позиции. */
    private String closedPositionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/closed";
    }

    /** Живые заявки счёта целиком: первый срез проактивной детекции. */
    private String pendingOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/pending";
    }

    /** Живые отдельные условные заявки счёта целиком: второй срез. */
    private String pendingAlgoOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/algo-orders/pending";
    }

    /** Живые позиции счёта целиком: третий срез. */
    private String positionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions";
    }

    /** Снимок средств моментом прогона: возраст ставится В ДАННЫХ. */
    private String balanceBody() {
        String moment = OffsetDateTime.now(ZoneOffset.UTC).toString();
        return """
                {
                  "externalUpdatedAt": "%s",
                  "externalTotalEquity": "100000",
                  "externalAdjustedEquity": "100000",
                  "externalAvailableEquity": "100000",
                  "balances": [
                    {
                      "externalCurrency": "USDT",
                      "externalUpdatedAt": "%s",
                      "externalEquity": "100000",
                      "externalCashBalance": "100000",
                      "externalAvailableBalance": "100000",
                      "externalFrozenBalance": "0"
                    }
                  ]
                }
                """.formatted(moment, moment);
    }
}
