package com.example.tradingcore.domain.fsm.tranche;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.TrancheActionDisposition;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Готовит транш к созданию его входной заявки: перепроверяет условие
 * входа, обеспечивает свежий снимок средств и выпускает вход
 * (docs/components/TranchePrecheckHandler.md).
 *
 * <p><b>Условие входа перепроверяется здесь.</b> Стало ложным до
 * появления живого риска — закрывается ТРАНШ, а не сделка; сделка
 * закрывается, когда так закрылись все её транши.
 *
 * <p><b>Объявление и деталь требуются безусловно, и ветки «их нет» не
 * возникает.</b> Восстановленный транш до предвходовой проверки не
 * доходит: он материализуется сразу в сопровождении и штатных рёбер входа
 * не имеет (docs/lifecycles/DealTranche.md).
 *
 * <p><b>Проверок «нет активной позиции» и «нет активной сделки» здесь
 * нет.</b> Транш по построению живёт внутри активной сделки, а уровни
 * сетки входят как раз при уже открытой позиции: условие «позиции нет»
 * ложно на всех тропах, кроме первого транша первой сделки, и делало бы
 * обработчик неисполнимым.
 *
 * <p><b>Названное ограничение: чистота ИНСТРУМЕНТА здесь не мерится.</b>
 * Дом требует инструмент-скоупного сбора итерации — запроса «что живо на
 * инструменте», видящего и НЕЗНАКОМЫЕ сущности; такого сбора в системе
 * нет ни одного, и его вводит компонент поиска нарушений инвариантов
 * (docs/components/AnomalyJob.md). Здесь мерится то, чей операнд есть:
 * живая сущность СДЕЛКИ, не приписанная ни одному её траншу. Условие
 * снятия ограничения — ход, вводящий инструмент-скоупный сбор; задача и
 * владелец — .claude/work/backlog.md §«Названные ограничения кодирования
 * шага 7 — возврат по появлению носителя».
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranchePrecheckHandler implements DealTrancheHandler {

    private final TrancheWorkPass workPass;
    private final TrancheActionDisposition disposition;
    private final DealContextProperties properties;

    @Override
    public DealTranche.Status handledStatus() {
        return DealTranche.Status.PRECHECK;
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isTrue(deal.unattributedLiveRisk()) || isTrue(deal.moreThanOneLiveEpisode())) {
            log.warn("Foreign live risk on the deal, precheck escalates dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate();
        }
        if (isNull(dealContext.declarationOf(tranche))) {
            log.warn("Tranche in precheck carries no declaration dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate();
        }
        // Энфорсер запрета «нового риска в окне сворачивания не берёт ни один
        // транш» (docs/rules/exit-teardown-order.md). Стои́т на статусном ребре
        // и ДО условия входа: под сворачиванием штатная ветвь не применяется
        // вовсе, и на пересечении окон пишется причина сделки
        // (docs/lifecycles/DealTranche.md §«Писатель причины закрытия транша»).
        // Транш не ждёт конца сворачивания, а закрывается: ожидание оставило бы
        // нетерминальный транш, и выходная проверка сделки не сошлась бы никогда.
        if (isTrue(deal.isCollapsing())) {
            return TrancheTransition.close(disposition.inheritedCloseReason(deal));
        }
        // Свежий снимок средств обеспечивается ДО работы: на этой итерации
        // обработчик ни преконтроля, ни создания заявки не запускает
        // (docs/components/TranchePrecheckHandler.md §«Рабочая логика»).
        if (isTrue(balanceStale(dealContext))) {
            return disposition.balanceFetch(dealContext);
        }
        TrancheTransition work = workPass.run(dealContext, tranche);
        if (isTrue(workPass.spoke(work))) {
            return exitCheck(work, tranche);
        }
        return conditionFalse(tranche);
    }

    /**
     * Выходная проверка: вход отправлен — переход к статусу отправленного
     * входа.
     *
     * <p>Проверка стои́т на самой НОГЕ, а не на исходе плана: команда
     * прохода отправляет ногу ПОСЛЕ того, как обработчик отдал переход
     * (docs/components/DealOrchestratorJob.md §«Цикл прохода»), и ребро
     * поэтому едет проходом, на котором отправка уже подтверждена фактом.
     */
    private TrancheTransition exitCheck(TrancheTransition transition, DealTranche tranche) {
        if (isTrue(transition.movesStatus()) || isFalse(tranche.entrySubmitted())) {
            return transition;
        }
        return transition.withStatus(DealTranche.Status.ENTRY_SUBMITTED);
    }

    /**
     * Работа прохода молчит и вход не отправлен — условие входа ложно.
     *
     * <p>Живого риска нет — закрывается ТРАНШ как истёкшее условие; живой
     * риск при ложном условии есть — разбирать его предвходовой проверке
     * нечем, и она поднимает сделку ошибочной тропой
     * (docs/components/TranchePrecheckHandler.md §«Рабочая логика»).
     */
    private TrancheTransition conditionFalse(DealTranche tranche) {
        if (isTrue(tranche.entrySubmitted())) {
            return TrancheTransition.moveTo(DealTranche.Status.ENTRY_SUBMITTED);
        }
        return isTrue(tranche.isRiskBearing())
                ? TrancheTransition.escalate()
                : TrancheTransition.close(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
    }

    /**
     * Снимок средств отсутствует либо старше объявленной толерантности.
     * Пустая толерантность возрастом не ограничивает — срока, по которому
     * снимок устарел бы, никто не объявил.
     */
    private Boolean balanceStale(DealContext dealContext) {
        if (isNull(dealContext.getBalanceContainer())) {
            return true;
        }
        if (isNull(properties.getBalanceFreshness())) {
            return false;
        }
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getBalanceFreshness());
        return isFalse(dealContext.getBalanceContainer().isFresherThan(threshold));
    }
}
