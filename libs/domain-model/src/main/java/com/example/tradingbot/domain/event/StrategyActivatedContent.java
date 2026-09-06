package com.example.tradingbot.domain.event;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;

/**
 * Содержимое события активации: <b>неизменяемый снимок определения</b>
 * целиком (docs/architecture/contracts.md §События).
 *
 * <p><b>Дерево едет полностью, и это не избыточность.</b> Потребитель —
 * торговое ядро — держит копию, по которой ведёт сделки при недоступном
 * владельце (docs/architecture/data-ownership.md §«Копии чужих данных»);
 * дочитать недостающее синхронно он не может по построению — ради
 * снятия этой зависимости копия и заведена.
 *
 * <p>Форма дерева — общий артефакт, поэтому вторым описанием она здесь не
 * повторяется: содержимое несёт саму доменную модель.
 *
 * @param definition снимок определения на момент активации
 */
public record StrategyActivatedContent(Strategy definition) {
}
