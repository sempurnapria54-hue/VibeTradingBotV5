package com.example.tradingcore.domain.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.event.DealShutdownInitiatedContent;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Применяет статусное ребро сделки <b>вместе с фактом остановки</b> — одной
 * транзакцией на каждом из двух рёбер, на которых причина выхода из
 * штатного ведения присваивается
 * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
 *
 * <p><b>Зачем отдельный носитель.</b> Событие обязан писать тот код,
 * который пишет решение, и <b>той же транзакцией</b>
 * (docs/architecture/contracts.md §«У каждого класса события назван
 * писатель, и он же писатель решения»). Оба ребра применяет проход
 * оркестратора, а проход транзакции не открывает: без этой границы строка
 * сделки коммитилась бы раньше, чем пишется строка outbox, и сбой между
 * ними оставил бы решение без факта — ровно то, против чего заведён outbox.
 *
 * <p><b>Решений сервис не принимает.</b> Причину выбирает обработчик
 * активной сделки либо затребователь ребра энфорсмента ступени; здесь —
 * применение и публикация.
 *
 * <p><b>Актор берётся здесь, а не приезжает параметром:</b> ручная тропа
 * у класса есть (держатель сворачивает радиус той же ступенью), и
 * принципал живёт в контексте хода, а не в решении затребователя
 * (docs/spec/event-actor-presence.json).
 *
 * <p><b>Радиус едет верхним уровнем содержимого</b>: журнал аудита форм не
 * знает и достаёт из содержимого только одноимённые компоненты верхнего
 * уровня (docs/models/domain/other/AuditRecord.md).
 */
@Service
@RequiredArgsConstructor
public class DealStatusEdgeService {

    private final DealDataService dealDataService;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final InstrumentDataService instrumentDataService;
    private final StrategyDataService strategyDataService;
    private final ActorProvider actorProvider;
    private final OutboxWriter outboxWriter;

    /**
     * Статусное ребро прохода: применить его к строке сделки и, если этим
     * ребром присвоена причина остановки, опубликовать факт.
     *
     * <p><b>Публикует ребро, а не состояние сделки.</b> Причина
     * перезаписываема, и второе присвоение описывает ДРУГОЕ происшествие —
     * почему сделка перестала вестись штатно сейчас
     * (docs/architecture/contracts.md §«У одной сделки бывает БОЛЬШЕ ОДНОГО
     * `DealShutdownInitiated`, и это свойство класса, а не дефект»).
     * Поэтому операнд — причина, пришедшая ПЕРЕХОДОМ, а не поле сделки:
     * поле несёт и причину предыдущего ребра.
     *
     * <p><b>Не применившееся ребро события не производит:</b> строка ушла
     * из-под прохода, и объявлять фактом ход, которого не было, нельзя.
     *
     * @param assignedReason причина, присвоенная этим ребром; пусто — ребро
     *                       штатное, и факта остановки на нём нет
     * @return ребро применилось
     */
    @Transactional
    public Boolean applyPassEdge(DealContext dealContext, Deal.Status fromStatus,
                                 Deal.ShutdownReason assignedReason) {
        Deal deal = dealContext.getDeal();
        try {
            if (isFalse(dealDataService.applyStatusEdge(deal, fromStatus))) {
                return false;
            }
            if (nonNull(assignedReason)) {
                publishInitiated(deal,
                        dealContext.getExchangeAccount().getTenantId(),
                        dealContext.getExchangeAccount().getInternalId(),
                        dealContext.getInstrument().getInternalId(),
                        dealContext.strategyInternalId());
            }
            return true;
        } catch (RuntimeException failure) {
            throw carryingReason(deal, assignedReason, failure);
        }
    }

    /**
     * Ребро энфорсмента жёсткой ступени: увести активную сделку в ошибку с
     * резолвленной причиной и опубликовать факт.
     *
     * <p><b>Затребователей у ребра два, а писатель один — этот.</b> Первый
     * ход энфорсмента (docs/components/SafetyHoldCoordinator.md) уводит
     * активные сделки радиуса в момент подъёма ступени, шаг прохода
     * (docs/components/DealOrchestratorJob.md) — ставшие активными после
     * него. <b>Причину оба берут у одного читателя</b>
     * ({@code HardRungShutdownReasonResolver}): правило «по радиусу стоящей
     * ступени» исполняется резолвом, а не добросовестностью затребователя,
     * и второго прочтения у него не бывает. Второго писателя у величины не
     * заводится: он писал бы статус без причины и без факта, а гард этого
     * ребра не открылся бы уже никогда.
     *
     * <p><b>Безусловным правило резолва не является, и ветвь исключения —
     * не оговорка.</b> Сделке, под которой не стои́т НИ ОДНОЙ жёсткой
     * ступени, первый ход энфорсмента пишет причину радиуса СОБСТВЕННОЙ
     * реакции — последним резервом, потому что риск такой сделки его же
     * реакция уже погасила; у шага прохода резерва нет вовсе, и такую
     * сделку он не уводит. Обе ветви и их старшинство — в доме величины
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»;
     * исполнимая форма — docs/spec/hard-rung-shutdown-reason.json,
     * величины {@code hardRungShutdownReason} и
     * {@code firstMoveShutdownReason}).
     *
     * <p><b>Модель сводится здесь же</b>, потому что здесь же и транзакция:
     * содержимое читает уже сведённое состояние, а не собирает второе его
     * описание.
     *
     * <p><b>Идентичности радиуса резолвятся ПРОЕКЦИЯМИ полей.</b> Контекста
     * прохода на этом шаге ещё нет — энфорсмент стои́т раньше сборки
     * контекста намеренно (docs/components/DealOrchestratorJob.md), — а
     * строки счёта и инструмента ради одного поля не тянутся
     * (.claude/rules/codestyle.md §«Выборка данных»).
     *
     * @return ребро применилось, то есть сделка стояла нетерминальной
     */
    @Transactional
    public Boolean enforceHardRung(Deal deal, Deal.ShutdownReason reason) {
        try {
            if (isFalse(dealDataService.enforceHardRung(deal.getId(), reason))) {
                return false;
            }
            deal.setStatus(Deal.Status.ERROR);
            deal.setShutdownReason(reason);
            publishInitiated(deal,
                    exchangeAccountDataService.getRequiredTenantInternalIdById(deal.getExchangeAccountId()),
                    exchangeAccountDataService.getRequiredInternalIdById(deal.getExchangeAccountId()),
                    instrumentDataService.getRequiredInternalIdById(deal.getInstrumentId()),
                    strategyInternalId(deal));
            return true;
        } catch (RuntimeException failure) {
            throw carryingReason(deal, reason, failure);
        }
    }

    /**
     * Отказ ребра, ПРИСВАИВАВШЕГО причину, помечается своим типом; отказ
     * штатного ребра уходит как есть.
     *
     * <p><b>Помечается ровно то, чей откат теряет ответ навсегда.</b>
     * Транзакция здесь накрывает и резолвы идентичностей, и вставку в
     * outbox, и любой их отказ откатывает <b>само ребро</b> — а общий
     * перехватчик прохода увёл бы такую сделку в {@code ERROR} без
     * причины, откуда ребро причины больше не применится
     * ({@code DealShutdownEdgeException}). Ребро без причины такого
     * свойства не имеет: терять на нём нечего, и различать его не нужно.
     *
     * <p><b>Транзакция при этом не сужается, и это выбор, а не
     * недоделка.</b> Атомарность «ребро + факт» есть требование дома
     * (docs/architecture/contracts.md §«У каждого класса события назван
     * писатель, и он же писатель решения»); вынести резолвы наружу можно
     * было бы только вторым бином — самовызов транзакции не открывает, —
     * а с помеченным отказом их цена равна повтору на следующем проходе,
     * не потере.
     */
    private RuntimeException carryingReason(Deal deal, Deal.ShutdownReason assignedReason,
                                            RuntimeException failure) {
        return isNull(assignedReason)
                ? failure
                : new DealShutdownEdgeException(deal.getId(), failure);
    }

    /** Факт остановки — той же транзакцией, что и ребро. */
    private void publishInitiated(Deal deal, String tenantId, String exchangeAccountInternalId,
                                  String instrumentInternalId, String strategyInternalId) {
        outboxWriter.write(tenantId, CoreEventType.DEAL_SHUTDOWN_INITIATED,
                DealShutdownInitiatedContent.of(deal, exchangeAccountInternalId, instrumentInternalId,
                        strategyInternalId, actorProvider.currentActor()));
    }

    /**
     * Идентичность определения там, где контекста нет — проекцией по ключу
     * закреплённой детали. Там, где контекст собран, тот же ответ отдаёт он
     * сам ({@code DealContext.strategyInternalId()}): копия-владелец детали
     * в нём уже загружена проходом. Деталь не закреплена — определения у сделки нет,
     * и пустота здесь значащая
     * (docs/rules/absent-value-semantics.md).
     */
    private String strategyInternalId(Deal deal) {
        return isNull(deal.getStrategyDetailId())
                ? null
                : strategyDataService.getRequiredStrategyInternalIdByDetailId(deal.getStrategyDetailId());
    }
}
