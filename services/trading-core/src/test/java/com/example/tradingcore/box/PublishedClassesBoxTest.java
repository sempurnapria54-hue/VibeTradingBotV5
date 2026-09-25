package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетка {@code B9.9} группы {@code B9} — перечень публикуемых классов ядра.
 *
 * <p><b>Класс свой, а не место в {@link OutboxRelayBoxTest}, и довод —
 * контекст, а не группа.</b> Клетка проходит тропы ВСЕХ классов ядра, и
 * половина из них стоит на живой сделке ({@link LiveDealBox}); её
 * контекст — ветвь {@link SharedLiveDealBox}, а прочие клетки группы
 * живут на штатном и сборки живой сделки не требуют.
 *
 * <p><b>Ожидаемый перечень — классы, у которых контракт называет писателя
 * с кодом.</b> Контракт перечисляет у производителя девять классов
 * (docs/architecture/contracts.md §События), и у трёх из них писателя
 * кода нет по объявлению самого контракта: сигналы приезжают со своим
 * предметом, ребра снятия ступени нет. Клетка утверждает, что пришли
 * все шесть с писателем и ни одного вне перечня контракта.
 */
class PublishedClassesBoxTest extends SharedLiveDealBox {

    /** Классы контракта у производителя {@code trading-core}, все девять. */
    private static final Set<String> CONTRACT_CLASSES = Set.of("ORDER_DECIDED", "DEAL_OPENED",
            "DEAL_SHUTDOWN_INITIATED", "DEAL_CLOSED", "SIGNAL_DETECTED", "SIGNAL_OUTCOME_RECORDED",
            "HOLD_RAISED", "HOLD_RELEASED", "ANOMALY_REPORTED");

    /** Классы контракта, у которых писатель есть в коде. */
    private static final Set<String> WRITTEN_CLASSES = Set.of("ORDER_DECIDED", "DEAL_OPENED",
            "DEAL_SHUTDOWN_INITIATED", "DEAL_CLOSED", "HOLD_RAISED", "ANOMALY_REPORTED");

    /** Реализованный результат эпизода сделки прогона. */
    private static final String LOSS = "-5";

    /** Потолок тиков реле: окно публикации ограничено, строк у прогона больше окна не бывает. */
    private static final Integer RELAY_TICKS = 5;

    @Test
    @DisplayName("B9.9 — каждый публикуемый класс приходит со своего ребра, и сверх них нет ничего")
    void everyPublishedClassComesFromItsEdgeAndNothingBeyond() {
        Wire.Mark mark = Wire.mark();
        // Решение о заявке, открытие, затребование остановки и закрытие —
        // жизнью одной сделки; подъём ступени и отчёт — ручной полной
        // постановкой счёта.
        closeLiveDealWith("S-PUB-1", LOSS);
        fullHalt(ACCOUNT);

        ticks(Tick.OUTBOX_RELAY, RELAY_TICKS);

        List<Wire.Published> published = Wire.publishedSince(mark);
        Set<String> classes = published.stream().map(Wire.Published::eventType).collect(Collectors.toSet());
        // Пришли все шесть классов с писателем — каждый своей тропой прогона.
        assertThat(classes).containsExactlyInAnyOrderElementsOf(WRITTEN_CLASSES);
        // Сверх перечня контракта нет ничего.
        assertThat(CONTRACT_CLASSES).containsAll(classes);
        // Версия формы содержимого у всех одна.
        assertThat(published.stream().map(record -> record.headers().get("version")).distinct().toList())
                .hasSize(1);
        // Опубликованы все строки outbox прогона: реле ничего не оставило.
        assertThat(published).hasSize(rows.count("outbox_events").intValue());
    }
}
