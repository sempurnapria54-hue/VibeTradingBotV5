package com.example.tradingcore.box;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * запрашивает: он заводит наблюдательную строку и начинает на ней серию, и
 * подтверждением служит живая серия той же строки — последнее наблюдение
 * не моложе минимального возраста подтверждения
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
 * классами:</b> выключатель тика — {@link DisabledDetectionBoxTest}, размер
 * страницы обхода контура — {@link DetectionContourPagingBoxTest}.
 */
class ProactiveDetectionBoxTest extends SharedLiveDealBox {

    /** Основа идентичности определения группы. */
    private static final String DEFINITION = "S-SCAN";

    /** Биржевой момент открытия наблюдённого эпизода: половина его адреса. */
    private static final String POSITION_MOMENT = "2026-09-20T10:00:05Z";

    /** Мягкая ступень биржевого счёта. */
    private static final String HOLD = "HOLD";

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

    /** Код расхождения суммы экспозиций траншей с нетто-размером эпизода. */
    private static final String EXPOSURE_MISMATCH = "EXCHANGE_EXPOSURE_MISMATCH";

    /** Нетто-размер эпизода, заведомо не равный наливу входа. */
    private static final String DIVERGED_NET_SIZE = "3";

    /** Код непроэнфорсенной жёсткой ступени радиуса. */
    private static final String RUNG_NOT_ENFORCED = "SAFETY_RUNG_NOT_ENFORCED";

    /** Код ненаблюдённого прохода. */
    private static final String PASS_INCOMPLETE = "ANOMALY_PASS_INCOMPLETE";

    /** Колонка момента последнего наблюдённого прохода на строке счёта. */
    private static final String OBSERVED_PASS_AT = "observed_pass_at";

    /**
     * Клиентский идентификатор НАШЕЙ заявки: маркер контура впереди
     * ({@code InternalIdFactory#isOurs}).
     */
    private static final String OUR_CLIENT_ID = "vtbboxorderone";

    /** Клиентский идентификатор ЧУЖОЙ заявки: маркера контура на нём нет. */
    private static final String FOREIGN_CLIENT_ID = "someone-else-1";

    /** Реализованный результат закрытого эпизода сделки: убыток. */
    private static final String LOSS = "-5";

    /** Режим маржи записи, которую открыла не наша заявка. */
    private static final String CROSS = "CROSS";

    /** Режим маржи контура: им уходят наши заявки. */
    private static final String CONTOUR_MARGIN_MODE = "ISOLATED";

    /** Query-параметр режима маржи у закрытия позиции. */
    private static final String MARGIN_MODE = "marginMode";

    /**
     * Предел попыток снятия риска — {@code kill-switch.max-teardown-attempts}
     * субстрата (умолчание конфигурации ядра).
     */
    private static final Integer TEARDOWN_ATTEMPTS = 3;

    /** Запись иного режима уходит из среза после своего закрытия. */
    private static final Boolean RECORD_LEAVES_ON_CLOSE = Boolean.TRUE;

    /** Запись иного режима закрытие переживает: срез отдаёт её на каждом чтении. */
    private static final Boolean RECORD_SURVIVES_CLOSE = Boolean.FALSE;

    /** Сценарий стаба: позиция сделки и запись иного режима на одной паре. */
    private static final String PAIR_SCENARIO = "pair-with-foreign-margin-record";

    /** Сценарий стаба: одиночная запись иного режима на паре без сделки. */
    private static final String LONE_RECORD_SCENARIO = "lone-foreign-margin-record";

    /** Состояние сценария: закрыта позиция сделки, запись иного режима жива. */
    private static final String DEAL_CLOSED = "deal-closed";

    /** Состояние сценария: закрыта запись иного режима. */
    private static final String RECORD_CLOSED = "record-closed";

    /** Состояние сценария: закрыты обе записи. */
    private static final String BOTH_CLOSED = "both-closed";

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
        // Срез позиций читают детекция и снятие риска; после закрытия
        // позиции её строки в срезе нет.
        connector.answersInTurn(positionsPath(ACCOUNT), Feed.array(livePositionOn(FOREIGN_INSTRUMENT)),
                Feed.array(livePositionOn(FOREIGN_INSTRUMENT)), Feed.emptyArray());
        connector.answers(closurePath(ACCOUNT), Feed.ack("ex-close-1", "close-1"));

        tick(Tick.ANOMALY_DETECTION);

        // Гистерезиса у признака нет, и это не послабление: строка
        // инструмента нашим ходом не исчезает, гонка чтения его не
        // производит — реакция идёт с первого наблюдения.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(eventTypes()).contains(HOLD_RAISED);
        // Позицию инструмента вне контура снятие риска закрывает само:
        // валюты расчёта у него нет, и закрытие уходит без неё — контракт
        // закрытия её не требует.
        List<LoggedRequest> closures = connector.requests(closurePath(ACCOUNT));
        assertThat(closures).hasSize(1);
        assertThat(closures.getFirst().queryParameter("externalInstrumentId").firstValue())
                .isEqualTo(FOREIGN_INSTRUMENT);
        assertThat(closures.getFirst().queryParameter("settleCurrency").isPresent()).isFalse();
        // Подтверждение пришло срезом позиций: отчёт критичной тропы
        // доведён до терминала.
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
    @DisplayName("B7.7 — расхождение суммы экспозиций с нетто-размером сворачивает счёт")
    void theExposureSumDivergingFromTheNetSizeTearsTheAccountDown() {
        standDivergedEpisode();
        standScan(Feed.array(livePositionOf(DIVERGED_NET_SIZE)), Feed.emptyArray(), Feed.emptyArray());
        connector.answers(closurePath(ACCOUNT), Feed.ack("ex-close-1", "close-1"));

        tick(Tick.ANOMALY_DETECTION);
        // Признак сравнивает базу с биржей и подтверждается следующим тиком:
        // на первом ступени нет, есть наблюдательная строка.
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(codesOfReports()).containsExactly(EXPOSURE_MISMATCH);
        ageObservations();
        tick(Tick.ANOMALY_DETECTION);

        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(codesOfReports()).containsOnly(EXPOSURE_MISMATCH);
        // Живой риск снимается: он есть, но ни одному траншу не приписан.
        assertThat(connector.requests(closurePath(ACCOUNT))).isNotEmpty();
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

    @Test
    @DisplayName("B7.14 — наблюдённый проход отмечает свой момент, ненаблюдённый его не двигает")
    void anObservedPassStampsItsMomentAndAnUnobservedOneDoesNot() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        // Возраст данных ставится в данных: наблюдения у счёта ещё не было.
        rows.put("update exchange_accounts set observed_pass_at = null where internal_id = ?", ACCOUNT);

        standScan(Feed.emptyArray(), Feed.emptyArray(), null);
        tick(Tick.ANOMALY_DETECTION);

        // Ненаблюдённый проход считается слепым, а момента не ставит: его
        // возраст — операнд гейта входа, и «не смотрели» не может его
        // освежить.
        assertThat(blindPasses()).isEqualTo(1);
        assertThat(accountRow().get(OBSERVED_PASS_AT)).isNull();

        standScan(Feed.emptyArray(), Feed.emptyArray(), Feed.emptyArray());
        tick(Tick.ANOMALY_DETECTION);

        OffsetDateTime firstObserved = (OffsetDateTime) accountRow().get(OBSERVED_PASS_AT);
        assertThat(firstObserved).isNotNull();
        assertThat(blindPasses()).isZero();
        Object changedAt = accountRow().get("modified_at");

        tick(Tick.ANOMALY_DETECTION);

        // Следующий наблюдённый проход момент двигает вперёд, а момента
        // изменения строки — нет: значение колонки и есть момент её
        // изменения, и здоровый счёт иначе менял бы строку каждым тактом
        // (docs/models/domain/core/ExchangeAccount.md §Персистентность).
        assertThat((OffsetDateTime) accountRow().get(OBSERVED_PASS_AT)).isAfter(firstObserved);
        assertThat(accountRow().get("modified_at")).isEqualTo(changedAt);
        OffsetDateTime lastObserved = (OffsetDateTime) accountRow().get(OBSERVED_PASS_AT);

        standScan(Feed.emptyArray(), Feed.emptyArray(), null);
        tick(Tick.ANOMALY_DETECTION);

        assertThat(accountRow().get(OBSERVED_PASS_AT)).isEqualTo(lastObserved);
    }

    /**
     * Живая нога налита половиной, встроенная защита в её теле в постановке:
     * площадка ставит её только на терминале родителя, и покрытие налитой
     * части отложенное (docs/rules/live-risk-protection.md §«Покрытие»).
     * Ни детектор, ни выходная проверка отправленного входа ступени по нему
     * не просят — и молчание здесь не тавтологично: половина (б) на той же
     * сборке тем же предикатом ступень даёт.
     */
    @Test
    @DisplayName("B7.16 (а) — встроенная защита живого частично налитого входа: налитая часть покрыта отложенно")
    void b7_16a_theLivePartiallyFilledEntryHasItsFilledPartCoveredDeferred() {
        openPartiallyFilledDeal();
        standScan(Feed.array(livePositionOf(partialFill())),
                Feed.array(Feed.pendingOrder(entryExternalId(), entryClientId(), EXTERNAL_INSTRUMENT)),
                Feed.emptyArray());
        Long uncoveredBefore = rows.countWhere("anomaly_reports", "code", LIVE_RISK_UNCOVERED);

        tick(Tick.ANOMALY_DETECTION);
        ageObservations();
        tick(Tick.ANOMALY_DETECTION);
        assertThat(rows.countWhere("anomaly_reports", "code", LIVE_RISK_UNCOVERED)).isEqualTo(uncoveredBefore);
        tick(Tick.DEAL_ORCHESTRATOR);

        assertThat(rows.countWhere("anomaly_reports", "code", LIVE_RISK_UNCOVERED)).isEqualTo(uncoveredBefore);
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        // Защита остаётся в постановке — ассерт прямой по строке защиты.
        assertThat(protectionRow().get("status")).isEqualTo("PENDING");
        // К площадке ушла только добыча: команд нет ни одной.
        assertThat(commandCalls()).isEmpty();
    }

    /**
     * Нога снята после частичного налива, и элемент защиты в её теле несёт
     * код отказа постановки: добыча, увидевшая терминал, уводит защиту в
     * ошибку, а следующий проход читает выходную проверку отправленного
     * входа — без гистерезиса и раньше консолидации
     * (docs/components/TrancheEntrySubmittedHandler.md §«Выходные проверки»).
     */
    @Test
    @DisplayName("B7.16 (б) — встроенная защита частично налитого входа: отказ постановки сворачивает счёт")
    void b7_16b_theFailedPlacementOfThePartiallyFilledEntryProtectionTearsTheAccountDown() {
        openPartiallyFilledDeal();
        standExchangeFollowingCommands(LOSS, LOSS, partialFill());
        standEntryWithFailedProtection(partiallyFilledEntryWithFailedProtection("CANCELED"));
        passesUntilProtectionFailsToPlace();
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        Long raisedBefore = countEvents(HOLD_RAISED_EVENT);
        Long shutdownsBefore = countEvents(DEAL_SHUTDOWN_INITIATED);
        PeerStub.all().forEach(PeerStub::forgetRequests);

        passesUntil(() -> isFalse(Objects.equals("ACTIVE", dealStatus())));

        // Биржевая ступень 2 без гистерезиса: отчёт критичной тяжести, и
        // наблюдательной строки того же кода перед ним нет.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        List<Map<String, Object>> uncovered = rows.rowsWhere("anomaly_reports", "code", LIVE_RISK_UNCOVERED);
        assertThat(uncovered).hasSize(1);
        assertThat(uncovered.getFirst().get("severity")).isEqualTo("CRITICAL");
        assertThat(countEvents(HOLD_RAISED_EVENT)).isEqualTo(raisedBefore + 1);
        // Консолидация не затребована: проверка читается раньше её эмиссии.
        assertThat(trancheStatus()).isEqualTo("ENTRY_SUBMITTED");
        // Ребро в ошибку — решение обработчика: причина остановки пуста.
        assertThat(dealStatus()).isEqualTo("ERROR");
        assertThat(dealRow().get("shutdown_reason")).isNull();
        assertThat(countEvents(DEAL_SHUTDOWN_INITIATED)).isEqualTo(shutdownsBefore);
        // Снятие риска: позиция закрыта; живой ноги нет, а встроенная защита
        // в ошибке в очередь снятия не входит.
        assertThat(connector.requests(closurePath(ACCOUNT))).isNotEmpty();
        assertThat(connector.requests(cancellationPath(ACCOUNT))).isEmpty();
        assertThat(connector.requests(attachedCancellationPath(ACCOUNT))).isEmpty();
    }

    @Test
    @DisplayName("B7.17 (а) — запись иного режима маржи рядом с позицией сделки — чужая сущность, а не нарушение"
            + " режима позиций")
    void b7_17a_aForeignMarginModeRecordBesideTheDealPositionIsAForeignEntity() {
        openLiveDeal();
        standPairWithForeignMarginRecord(RECORD_LEAVES_ON_CLOSE);

        tick(Tick.ANOMALY_DETECTION);

        assertForeignEntityRaisedOnTheFirstTick();
    }

    @Test
    @DisplayName("B7.17 (б) — одиночная запись иного режима маржи — чужая сущность, восстановительной сделки нет")
    void b7_17b_aLoneForeignMarginModeRecordIsAForeignEntityAndIsNotRecovered() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        connector.answersInState(LONE_RECORD_SCENARIO, positionsPath(ACCOUNT), PeerStub.INITIAL,
                Feed.array(foreignMarginRecord()));
        connector.answersInState(LONE_RECORD_SCENARIO, positionsPath(ACCOUNT), RECORD_CLOSED, Feed.emptyArray());
        connector.flipsOnWhen(LONE_RECORD_SCENARIO, closurePath(ACCOUNT), PeerStub.INITIAL, MARGIN_MODE, CROSS,
                Feed.ack("ex-close-record", "close-record"), RECORD_CLOSED);
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());

        tick(Tick.ANOMALY_DETECTION);

        assertForeignEntityRaisedOnTheFirstTick();
        // Одиночная запись иного режима позицией без сделки не читается:
        // сделка ведёт позицию своего режима и чужую ни закрыть, ни защитить
        // не может.
        assertThat(rows.count("deals")).isZero();
        assertThat(eventTypes()).doesNotContain(DEAL_OPENED);
    }

    /**
     * Тот же тик, что поднимает ступень (клетка {@code B7.17 (а)}), гонит и
     * снятие риска: позицию сделки — ходом сделки без параметра режима, запись
     * иного режима — отдельным шагом вне графа сделок её режимом
     * (docs/components/KillSwitchExecutor.md §«Риск вне графа сделок»).
     */
    @Test
    @DisplayName("B7.18 (а) — снятие риска закрывает запись иного режима её режимом, и уход записи подтверждает"
            + " радиус")
    void b7_18a_theTeardownClosesTheForeignMarginModeRecordInItsModeAndConfirmsTheScope() {
        openLiveDeal();
        standPairWithForeignMarginRecord(RECORD_LEAVES_ON_CLOSE);

        tick(Tick.ANOMALY_DETECTION);

        assertTheDealAndTheRecordAreClosedEachInItsMode();
        assertThat(closuresInMode(CROSS)).hasSize(1);
        assertThat(criticalForeignEntityReport().get("status")).isEqualTo("COMPLETED");
    }

    /**
     * Запись иного режима закрытие переживает: остаток любого режима значит,
     * что риск не снят, — подтверждает радиус ЛЮБАЯ живая позиция, и попытки
     * ограничены тем же пределом {@code kill-switch.max-teardown-attempts}
     * (docs/components/KillSwitchExecutor.md §«Риск вне графа сделок»).
     */
    @Test
    @DisplayName("B7.18 (б) — запись иного режима, пережившая закрытие, держит радиус неподтверждённым")
    void b7_18b_aForeignMarginModeRecordSurvivingItsClosureKeepsTheScopeUnconfirmed() {
        openLiveDeal();
        standPairWithForeignMarginRecord(RECORD_SURVIVES_CLOSE);

        tick(Tick.ANOMALY_DETECTION);

        assertTheDealAndTheRecordAreClosedEachInItsMode();
        assertThat(closuresInMode(CROSS)).hasSize(TEARDOWN_ATTEMPTS);
        assertThat(criticalForeignEntityReport().get("status")).isNotEqualTo("COMPLETED");
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
    }

    // ------------------------------------------------------------------
    // Предусловия группы
    // ------------------------------------------------------------------

    /**
     * Площадка с позицией сделки режима контура и записью режима {@code CROSS}
     * по тому же инструменту, отвечающая по ИСХОДУ команд.
     *
     * <p><b>Сценарий один на пару записей, и состояний у него четыре</b>: обе
     * живы, закрыта позиция сделки, закрыта запись иного режима, закрыты обе.
     * Срез счёта читает обе записи одним путём, поэтому его ответ зависит от
     * того, что из двух уже исполнено, а две команды закрытия различаются
     * только параметром режима: у позиции сделки его нет — режим контура и
     * есть умолчание закрытия, — у записи иного режима он её режим
     * ({@link PeerStub#flipsOnWhen}).
     *
     * <p>Защиту, движения и прочие срезы ставит
     * {@link #standExchangeFollowingCommands(String)}; заготовки этого метода
     * новее и покрывают все четыре состояния, поэтому позиционные заготовки
     * того сценария на них не отвечают.
     *
     * @param recordLeavesOnClose уходит ли запись иного режима после своего
     *                            закрытия
     */
    private void standPairWithForeignMarginRecord(Boolean recordLeavesOnClose) {
        standExchangeFollowingCommands(LOSS);
        String ours = livePositionOf(entrySize());
        String record = foreignMarginRecord();
        String closed = Feed.array(Feed.closedPosition(positionExternalId(), POSITION_CREATED_AT, CLOSED_AT, LOSS));
        String dealAck = Feed.ack("ex-close-" + dealOrdinal(), "close-" + dealOrdinal());
        String recordAck = Feed.ack("ex-close-record", "close-record");
        Map<String, String> afterDealClose = Map.of(PeerStub.INITIAL, DEAL_CLOSED, DEAL_CLOSED, DEAL_CLOSED,
                RECORD_CLOSED, BOTH_CLOSED, BOTH_CLOSED, BOTH_CLOSED);
        Map<String, String> afterRecordClose = isTrue(recordLeavesOnClose)
                ? Map.of(PeerStub.INITIAL, RECORD_CLOSED, DEAL_CLOSED, BOTH_CLOSED,
                        RECORD_CLOSED, RECORD_CLOSED, BOTH_CLOSED, BOTH_CLOSED)
                : Map.of(PeerStub.INITIAL, PeerStub.INITIAL, DEAL_CLOSED, DEAL_CLOSED,
                        RECORD_CLOSED, RECORD_CLOSED, BOTH_CLOSED, BOTH_CLOSED);
        Map<String, String> slice = Map.of(PeerStub.INITIAL, Feed.array(ours, record),
                DEAL_CLOSED, Feed.array(record), RECORD_CLOSED, Feed.array(ours), BOTH_CLOSED, Feed.emptyArray());
        for (String state : List.of(PeerStub.INITIAL, DEAL_CLOSED, RECORD_CLOSED, BOTH_CLOSED)) {
            boolean dealPositionLive = PeerStub.INITIAL.equals(state) || RECORD_CLOSED.equals(state);
            connector.flipsOnWhen(PAIR_SCENARIO, closurePath(ACCOUNT), state, MARGIN_MODE, null, dealAck,
                    afterDealClose.get(state));
            connector.flipsOnWhen(PAIR_SCENARIO, closurePath(ACCOUNT), state, MARGIN_MODE, CROSS, recordAck,
                    afterRecordClose.get(state));
            connector.answersInState(PAIR_SCENARIO, positionsPath(ACCOUNT), state, slice.get(state));
            connector.answersInState(PAIR_SCENARIO, positionPath(ACCOUNT), state,
                    dealPositionLive ? ours : Feed.absent());
            connector.answersInState(PAIR_SCENARIO, closedPositionsPath(ACCOUNT), state,
                    dealPositionLive ? Feed.emptyArray() : closed);
        }
    }

    /** Запись позиции режима {@code CROSS} по инструменту контура: её открыла не наша заявка. */
    private String foreignMarginRecord() {
        return Feed.livePositionInMode("ex-cross-1", EXTERNAL_INSTRUMENT, "1", LAST_PRICE, POSITION_MOMENT, CROSS);
    }

    /**
     * Исход {@code B7.17} в обоих прогонах: с ПЕРВОГО тика счёт свёрнут кодом
     * чужой сущности критичной тяжести, а запись иного режима в счёт записей
     * режима контура не вошла.
     */
    private void assertForeignEntityRaisedOnTheFirstTick() {
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(criticalForeignEntityReport()).isNotEmpty();
        assertThat(eventTypes()).contains(HOLD_RAISED);
        assertThat(codesOfReports()).doesNotContain(POSITION_MODE_VIOLATION);
    }

    /**
     * Исход {@code B7.18} общий обоим прогонам: позиция сделки закрыта ходом
     * сделки без параметра режима, запись иного режима — её режимом, и
     * закрытий режимом контура вне хода сделки нет.
     */
    private void assertTheDealAndTheRecordAreClosedEachInItsMode() {
        List<LoggedRequest> dealClosures = closuresInMode(null);
        assertThat(dealClosures).hasSize(1);
        assertThat(dealClosures.getFirst().queryParameter("externalInstrumentId").firstValue())
                .isEqualTo(EXTERNAL_INSTRUMENT);
        assertThat(closuresInMode(CONTOUR_MARGIN_MODE)).isEmpty();
    }

    /**
     * Закрытия позиции, ушедшие к стабу с названным режимом маржи.
     *
     * @param marginMode режим в параметре закрытия; пусто — параметра нет
     */
    private List<LoggedRequest> closuresInMode(String marginMode) {
        return connector.requests(closurePath(ACCOUNT)).stream()
                .filter(request -> isNull(marginMode)
                        ? isFalse(request.queryParameter(MARGIN_MODE).isPresent())
                        : request.queryParameter(MARGIN_MODE).isPresent()
                                && Objects.equals(marginMode, request.queryParameter(MARGIN_MODE).firstValue()))
                .toList();
    }

    /** Строка отчёта чужой сущности критичной тяжести; пусто — её нет. */
    private Map<String, Object> criticalForeignEntityReport() {
        return rows.rowsWhere("anomaly_reports", "code", FOREIGN_ORDER).stream()
                .filter(row -> Objects.equals("CRITICAL", row.get("severity")))
                .findFirst()
                .orElse(Map.of());
    }

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
     * классом поднимает биржевую ступень 2 ловцом прохода
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
        assignLeverage(ACCOUNT, INSTRUMENT);
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
        // Отправка ищет ногу по клиентскому идентификатору перед постановкой.
        connector.answers(lookupPath(ACCOUNT), Feed.absent());
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

    /**
     * Сделка, чья сумма экспозиций траншей НЕ сходится с нетто-размером её
     * живого эпизода: вход налит целиком, а площадка отдаёт эпизод меньшего
     * размера.
     *
     * <p><b>Оба операнда поставлены ТРОПОЙ ЯЩИКА:</b> экспозиция транша —
     * наливом, наблюдённым добычей ноги, нетто-размер — эпизодом, добытым
     * следующим проходом (docs/models/domain/aggregate/Deal.md
     * §«Экспозиция сделки и сверка с биржей»). Детектор читает эпизод из
     * базы, а не срез площадки, поэтому расхождение обязано лечь в базу.
     */
    private void standDivergedEpisode() {
        submitEntry(workingDefinition());
        String size = entrySize();
        connector.answers(lookupPath(ACCOUNT), Feed.filledOrder(entryExternalId(), entryClientId(), size, LAST_PRICE));
        connector.answers(positionPath(ACCOUNT), livePositionOf(DIVERGED_NET_SIZE));
        connector.answers(pendingProtectionsPath(ACCOUNT), Feed.array(Feed.materializedProtection(
                protectionClientId(), protectionExternalId(), size, protectionTrigger())));
        passesUntil(() -> rows.count("positions") == 1L);
        assertThat(rows.all("positions").getFirst().get("external_size").toString())
                .startsWith(DIVERGED_NET_SIZE + ".");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /** Счёт подряд идущих ненаблюдённых проходов на строке счёта. */
    private Integer blindPasses() {
        return ((Number) accountRow().get("blind_pass_count")).intValue();
    }
}
