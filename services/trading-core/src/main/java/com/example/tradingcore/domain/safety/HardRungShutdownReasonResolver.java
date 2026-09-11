package com.example.tradingcore.domain.safety;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.persistence.service.DealDataService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Единственный читатель соответствия «стоящая ступень → причина выхода из
 * штатного ведения» (docs/lifecycles/Deal.md §«Причина выхода из штатного
 * ведения»; исполнимая форма — docs/spec/hard-rung-shutdown-reason.json,
 * величина {@code hardRungShutdownReason}).
 *
 * <p><b>Зачем отдельный носитель.</b> Затребователей у ребра энфорсмента
 * два — первый ход при подъёме ступени
 * ({@link SafetyHoldCoordinator}) и шаг прохода
 * (docs/components/DealOrchestratorJob.md), — а правило резолва у них
 * одно. Вторая его копия разошлась бы с первой
 * (.claude/rules/policy-home.md) — и разошлась: первый ход брал причину из
 * радиуса ПОДНИМАЕМОГО сигнала, и на сделке под обеими стоящими ступенями
 * писал инструментную причину там, где дом предписывает биржевую.
 *
 * <p><b>Причина читается по СТОЯЩЕЙ ступени, а не по радиусу сигнала.</b>
 * Поле отвечает на «почему сделка перестала вестись штатно» — потому что
 * на её радиусе стои́т ступень; кем и каким сигналом ступень поднята,
 * отвечают строка отчёта и разрез счётчика по коду тропы
 * (docs/rules/statistics-aggregates.md). <b>Стоящая ступень старше, но не
 * единственна:</b> сделке, под которой не стои́т ни одной, первый ход
 * энфорсмента отвечает радиусом собственной реакции
 * ({@link #resolveForFirstMove}) — и только он.
 *
 * <p><b>Счёт читается первым:</b> биржевой радиус старше, и старшинство
 * согласовано с доминированием биржевых ступеней
 * (docs/rules/exchange-hold.md). Цена названа в доме: причина
 * одновременно стоявшего инструментного холда в поле не видна — её несёт
 * строка статуса инструмента.
 *
 * <p><b>Ступени читаются ПАКЕТОМ, а не по сделке.</b> Радиусов у сделки
 * два, и чтение каждого по строке дало бы два обращения на сделку — то
 * есть выборку, растущую вместе с числом торговых строк
 * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
 * одного поля»).
 */
@Service
@RequiredArgsConstructor
public class HardRungShutdownReasonResolver {

    private final DealDataService dealDataService;

    /**
     * Раскладка «сделка → причина» по стоящим ступеням её радиусов.
     *
     * <p><b>Сделки, под которой не стои́т ни одна жёсткая ступень, в
     * раскладке нет вовсе, и пустота значащая</b>
     * (docs/rules/absent-value-semantics.md). Что с ней делать, решает
     * ЗАТРЕБОВАТЕЛЬ, и решения у них разные: шаг прохода такую сделку
     * пропускает — она вне радиуса всякой стоящей ступени; у первого хода
     * есть последний резерв ({@link #resolveForFirstMove}), потому что
     * риск этой сделки его же реакция уже погасила.
     *
     * @param dealIds идентичности сделок, по которым нужен ответ
     */
    public Map<Long, Deal.ShutdownReason> resolveByStandingRung(List<Long> dealIds) {
        Set<Long> underAccountRung = new HashSet<>(dealDataService.findIdsUnderAccountRung(dealIds));
        Set<Long> underInstrumentRung = new HashSet<>(dealDataService.findIdsUnderInstrumentRung(dealIds));
        Map<Long, Deal.ShutdownReason> resolved = new HashMap<>();
        for (Long dealId : dealIds) {
            if (underAccountRung.contains(dealId)) {
                resolved.put(dealId, HoldScope.EXCHANGE_ACCOUNT.getShutdownReason());
                continue;
            }
            if (underInstrumentRung.contains(dealId)) {
                resolved.put(dealId, HoldScope.INSTRUMENT.getShutdownReason());
            }
        }
        return resolved;
    }

    /**
     * Тот же резолв для ПЕРВОГО ХОДА энфорсмента, у которого есть последний
     * резерв — радиус собственной реакции.
     *
     * <p><b>Резерв нужен ровно одному затребователю, и довод не в
     * симметрии.</b> Снятие живого риска идёт шагом 3 реакции, а каскад —
     * шагом 5 (docs/components/SafetyHoldCoordinator.md §Последовательность):
     * ступень, снятая держателем в этом окне, оставляет сделку активной с
     * УЖЕ погашенным риском. Пропустить такую сделку нельзя — шаг прохода её
     * не подберёт (стоящей ступени нет), а расхождение экспозиции поднимет
     * жёсткую ступень на ВЕСЬ счёт, то есть шире того радиуса, что держатель
     * только что снял. Шагу прохода резерв не нужен и не даётся: он ничего не
     * поднимал и риска не снимал, и сделка без стоящей ступени ему не
     * предмет.
     *
     * <p><b>Стоящая ступень старше резерва</b>, и это то же старшинство, что
     * и в {@link #resolveByStandingRung}: резерв доходит только до сделки, у
     * которой не стои́т НИ ОДНОЙ ступени. Исполнимая форма —
     * docs/spec/hard-rung-shutdown-reason.json, величина
     * {@code firstMoveShutdownReason}.
     *
     * @param reactionScope радиус реакции, снявшей риск этих сделок
     */
    public Map<Long, Deal.ShutdownReason> resolveForFirstMove(List<Long> dealIds,
                                                              HoldScope reactionScope) {
        Map<Long, Deal.ShutdownReason> resolved = resolveByStandingRung(dealIds);
        for (Long dealId : dealIds) {
            resolved.putIfAbsent(dealId, reactionScope.getShutdownReason());
        }
        return resolved;
    }
}
