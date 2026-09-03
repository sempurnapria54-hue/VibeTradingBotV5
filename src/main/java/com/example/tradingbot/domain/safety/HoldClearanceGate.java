package com.example.tradingbot.domain.safety;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.config.KillSwitchProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.deal.DealTerminalGate;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.persistence.service.DealDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Предусловие снятия ЖЁСТКОЙ ступени: доказано ли машинно, что живого
 * риска в радиусе не осталось.
 *
 * <p><b>Предусловие машинное, а не заявляемое</b> — прямое применение
 * «потолок обеспечен механизмом, а не заявлением». Тропа, ради которой
 * оно стои́т: реакция сворачивания best-effort по составу — kill-switch
 * мог не подтвердиться, и тогда снятие вернуло бы вход в торговлю поверх
 * непогашенного риска (docs/rules/manual-halt.md §«Предусловие «риска не
 * осталось» — машинное, а не заявляемое»).
 *
 * <p><b>Выборка — сделки радиуса, ВКЛЮЧАЯ терминальные:</b> к моменту
 * вызова снятия каскад уже увёл активные сделки в ошибочное состояние, и
 * остаточный риск живёт после терминала. Выборка только по нетерминальным
 * была бы пуста ровно на мотивирующей тропе, и энфорсер оказался бы
 * декоративным.
 *
 * <p><b>Признак — тот же предикат, что гейтит терминал сделки</b>
 * ({@link DealTerminalGate#riskProvenAbsent}); своего предиката живого
 * риска поверхность не заводит.
 *
 * <p><b>Названные ограничения — два.</b> Первое: предикат покрывает
 * четыре признака живого риска из пяти; пятый — неизвестная живая
 * сущность на бирже — операндом прохода не выражается по построению, и
 * это то же ограничение, с которым живёт гейт терминала сделки. Второе:
 * выборка берётся СВЕЖИМ ОКНОМ, а не всей историей радиуса — история
 * сделок инструмента растёт без границы, а остаточный риск живёт на
 * сделках, которых каскад коснулся, то есть на свежих
 * (.claude/rules/codestyle.md §«Выборка данных»).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldClearanceGate {

    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final DealTerminalGate dealTerminalGate;
    private final KillSwitchProperties properties;

    /** Живого риска не осталось ни у одной сделки инструмента в окне выборки. */
    public Boolean riskClearedOnInstrument(Long instrumentId) {
        return riskCleared(dealDataService.findRecentByInstrumentId(instrumentId,
                properties.getClearanceDealWindow()));
    }

    /** Живого риска не осталось ни у одной сделки биржи в окне выборки. */
    public Boolean riskClearedOnExchange(Long exchangeId) {
        return riskCleared(dealDataService.findRecentByExchangeId(exchangeId,
                properties.getClearanceDealWindow()));
    }

    private Boolean riskCleared(List<Deal> radius) {
        for (Deal deal : radius) {
            DealContext dealContext = dealContextService.build(deal);
            if (isTrue(dealTerminalGate.riskProvenAbsent(deal, deal.getTranches(),
                    dealContext.getGraphComplete()))) {
                continue;
            }
            log.warn("Hold clearance refused: live risk is not proven absent dealId={} status={}",
                    deal.getId(), deal.getStatus());
            return false;
        }
        return true;
    }
}
