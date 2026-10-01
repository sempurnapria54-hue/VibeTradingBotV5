package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Срез позиций площадки по радиусу — форма пятого признака живого риска,
 * «неизвестная живая сущность на бирже», у тех, чей предмет радиус, а не
 * сделка (docs/lifecycles/Deal.md §«Живой риск»).
 *
 * <p><b>Читателей у среза два, и форма у них одна.</b> Снятие риска
 * подтверждает им радиус (docs/components/KillSwitchExecutor.md §«Риск вне
 * графа сделок»), снятие жёсткой ступени читает им своё предусловие «риска
 * не осталось» (docs/rules/manual-halt.md §«Выборка и производитель
 * предусловия названы»): снятие проверяет ровно то, что обязано было
 * подтвердиться. Две копии чтения разошлись бы первой же правкой одной из
 * них.
 *
 * <p><b>Живой считается строка с ненулевым размером:</b> статуса строке
 * среза никто не резолвит, а по закрытой позиции источник отдаёт строку с
 * нулём.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionSliceReader {

    private final ExchangeOperationsClient exchangeOperationsClient;

    /**
     * Позиции радиуса с ненулевым размером. Читается срез счёта целиком и
     * сужается инструментом: одно чтение на оба радиуса.
     *
     * <p><b>Пусто — срез не добыт</b>, и это не «позиций нет»: не добытый
     * срез отсутствия риска не доказывает, и вызывающий читает пустоту
     * живым риском, а не его отсутствием.
     *
     * @param externalInstrumentId инструмент радиуса пары; пусто — радиус
     *                             счёта целиком
     */
    public List<Position> livePositions(ExchangeAccount account, String externalInstrumentId) {
        try {
            return emptyIfNull(exchangeOperationsClient.getPositions(account.getInternalId())).stream()
                    .filter(position -> isTrue(position.hasLiveSize()))
                    .filter(position -> isNull(externalInstrumentId)
                            || Objects.equals(externalInstrumentId, position.getExternalInstrumentId()))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            log.warn("Position slice read failed exchangeAccountId={}: {}", account.getId(), e.getMessage());
            return null;
        }
    }
}
