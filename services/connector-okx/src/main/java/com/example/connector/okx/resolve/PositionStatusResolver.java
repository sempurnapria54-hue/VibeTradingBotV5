package com.example.connector.okx.resolve;

import com.example.connector.okx.snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.resolve.StatusResolveResult;

/**
 * Резолв статуса позиции по граничному снапшоту площадки.
 *
 * <p><b>Почему интерфейс живёт у коннектора, а не в общей библиотеке.</b>
 * Его параметр — {@link PositionExternalSnapshot}, форма, обязанная
 * источнику. Контракт, чей параметр сформирован источником, общим не
 * бывает: у второй площадки снапшот свой. Соседние резолверы — заявки и
 * algo-заявки — принимают СТРОКУ статуса и потому лежат в
 * `libs/domain-model` (Д633; критерий предъявлен компилятором, а не
 * рассуждением).
 */
public interface PositionStatusResolver {

    StatusResolveResult<Position.Status, Position.CloseReason> resolve(PositionExternalSnapshot snapshot);
}
