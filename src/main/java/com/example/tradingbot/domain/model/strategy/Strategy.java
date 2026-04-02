package com.example.tradingbot.domain.model.strategy;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.market.MarketPhase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Getter
@Setter
public class Strategy extends Auditable {

    private Long id;

    /**
     * Инструмент, для которого предназначена стратегия.
     */
    private Long instrumentId;

    /**
     * Имя стратегии.
     */
    private String name;

    /**
     * Текущий статус стратегии.
     */
    private Status status;

    /**
     * Детали стратегии по фазам рынка.
     */
    private List<StrategyDetails> details;

    public StrategyDetails getActiveDetails(MarketPhase.Type marketPhaseType) {
        if (this.details == null) {
            return null;
        }

        return this.details.stream()
                           .filter(Objects::nonNull)
                           .filter(item -> item.getStatus() == StrategyDetails.Status.ACTIVE)
                           .filter(item -> item.getMarketPhaseType() == marketPhaseType)
                           .findFirst()
                           .orElse(null);
    }

    /**
     * Статус стратегии как контейнера.
     * <p>
     * Этот enum определяет,
     * можно ли использовать стратегию в целом,
     * независимо от деталей по конкретным фазам рынка.
     */
    public enum Status {

        /**
         * Черновик стратегии.
         * <p>
         * Такая стратегия ещё находится в работе:
         * - может быть неполной,
         * - может меняться по структуре,
         * - не должна использоваться в live-торговле.
         */
        DRAFT,

        /**
         * Активная стратегия.
         * <p>
         * Именно такая стратегия может быть выбрана сервисом стратегий
         * для конкретного инструмента и использована оркестратором.
         */
        ACTIVE,

        /**
         * Временно приостановленная стратегия.
         * <p>
         * Используется, когда:
         * - стратегию не нужно удалять,
         * - но торговать по ней сейчас нельзя.
         */
        PAUSED,

        /**
         * Архивная стратегия.
         * <p>
         * Нужна для:
         * - хранения истории,
         * - сравнения версий,
         * - анализа старых сделок и тестов.
         * <p>
         * В активной торговле использоваться не должна.
         */
        ARCHIVED
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isNotActive() {
        return isFalse(isActive());
    }
}