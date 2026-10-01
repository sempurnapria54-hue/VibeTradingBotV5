package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетка {@code B9.9} группы {@code B9} — перечень публикуемых классов ядра.
 *
 * <p><b>Класс свой, а не место в {@link OutboxRelayBoxTest}, и довод —
 * контекст, а не группа.</b> Клетка проходит тропы классов ядра, и
 * половина из них стоит на живой сделке ({@link LiveDealBox}); её
 * контекст — ветвь {@link SharedLiveDealBox}, а прочие клетки группы
 * живут на штатном и сборки живой сделки не требуют.
 *
 * <p><b>Ожидаемый перечень — классы, у которых контракт называет писателя
 * с кодом.</b> Контракт перечисляет у производителя десять классов
 * (docs/architecture/contracts.md §События), и у двух из них писателя кода
 * нет по объявлению самого контракта: сигналы приезжают со своим
 * предметом. Прогон клетки проходит тропы всех восьми с писателем и
 * утверждает, что пришли все восемь и ни одного вне перечня контракта.
 *
 * <p><b>Решение об отдельной условной заявке даёт вторая сделка, и шаг у
 * неё — сопровождения.</b> Отдельную заявку заводит шаг статуса
 * сопровождения транша, а не вход: штатное определение живой сделки несёт
 * только встроенный стоп. Вторая сделка стоит живой к полной постановке
 * счёта, и площадка отвечает по исходу команд снятия — тропа клетки
 * {@code B5.3}, где та же пара «отдельная защита плюс полная постановка»
 * уже проходится.
 */
class PublishedClassesBoxTest extends SharedLiveDealBox {

    /** Классы контракта у производителя {@code trading-core}, все десять. */
    private static final Set<String> CONTRACT_CLASSES = Set.of("ORDER_DECIDED", "ALGO_ORDER_DECIDED",
            "DEAL_OPENED", "DEAL_SHUTDOWN_INITIATED", "DEAL_CLOSED", "SIGNAL_DETECTED",
            "SIGNAL_OUTCOME_RECORDED", "HOLD_RAISED", "HOLD_RELEASED", "ANOMALY_REPORTED");

    /** Классы контракта с писателем в коде: тропы всех восьми проходит прогон клетки. */
    private static final Set<String> EXERCISED_CLASSES = Set.of("ORDER_DECIDED", "ALGO_ORDER_DECIDED",
            "DEAL_OPENED", "DEAL_SHUTDOWN_INITIATED", "DEAL_CLOSED", "HOLD_RAISED", "HOLD_RELEASED",
            "ANOMALY_REPORTED");

    /** Определение второй сделки — со стопом отдельной условной заявкой. */
    private static final String SEPARATE_STOP_DEFINITION = "S-PUB-2";

    /** Ключ действия отдельной условной защиты. */
    private static final String SEPARATE_STOP = "separate-stop";

    /** Дистанция отдельной защиты, процент якоря. */
    private static final String SEPARATE_STOP_PERCENTS = "3";

    /** Класс ручного вмешательства, мягкая ступень счёта. */
    private static final String FREEZE = "FREEZE";

    /** Реализованный результат эпизода сделки прогона. */
    private static final String LOSS = "-5";

    /** Потолок тиков реле: окно публикации ограничено, строк у прогона больше окна не бывает. */
    private static final Integer RELAY_TICKS = 5;

    @Test
    @DisplayName("B9.9 — каждый публикуемый класс приходит со своего ребра, и сверх них нет ничего")
    void everyPublishedClassComesFromItsEdgeAndNothingBeyond() {
        Wire.Mark mark = Wire.mark();
        // Решение о заявке, открытие, затребование остановки и закрытие —
        // жизнью одной сделки; подъём и снятие мягкой ступени — ручной
        // постановкой и снятием заморозки счёта; решение об отдельной
        // условной заявке — шагом сопровождения второй сделки; жёсткий
        // подъём и отчёт — ручной полной постановкой счёта.
        closeLiveDealWith("S-PUB-1", LOSS);
        freeze(ACCOUNT);
        post(HALT_CLEARANCES, Bodies.halt(FREEZE, ACCOUNT));
        openLiveDeal(Definitions.withManagingSteps(SEPARATE_STOP_DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND, List.of(Definitions.managingStep(StrategyStepType.MAIN_PROTECTION,
                        Definitions.stopLossAlgo(SEPARATE_STOP, SEPARATE_STOP_PERCENTS)))));
        standAlgoOrdersFollowingCommands(1);
        passesUntil(() -> algoOrdersOfDeal().size() == 1
                && Objects.equals("ACTIVE", algoOrdersOfDeal().getFirst().get("status")));
        standExchangeFollowingCommands(LOSS);
        fullHalt(ACCOUNT);

        ticks(Tick.OUTBOX_RELAY, RELAY_TICKS);

        List<Wire.Published> published = Wire.publishedSince(mark);
        Set<String> classes = published.stream().map(Wire.Published::eventType).collect(Collectors.toSet());
        // Пришли все восемь классов с писателем — каждый своей тропой прогона.
        assertThat(classes).containsExactlyInAnyOrderElementsOf(EXERCISED_CLASSES);
        // Сверх перечня контракта нет ничего.
        assertThat(CONTRACT_CLASSES).containsAll(classes);
        // Версия формы содержимого у всех одна.
        assertThat(published.stream().map(record -> record.headers().get("version")).distinct().toList())
                .hasSize(1);
        // Опубликованы все строки outbox прогона: реле ничего не оставило.
        assertThat(published).hasSize(rows.count("outbox_events").intValue());
    }
}
