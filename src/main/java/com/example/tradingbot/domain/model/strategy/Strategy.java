package com.example.tradingbot.domain.model.strategy;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.market.MarketPhase;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.BooleanUtils;

import java.util.List;
import java.util.Objects;

/**
 * Стратегия хранит торговые правила, условия и ожидаемые действия.
 * <p>
 * FSM сделки управляет реальными runtime-сущностями,
 * а стратегия говорит, что должно быть сделано
 * и при каких условиях.
 */
@Getter
@Setter
public class Strategy extends Auditable {

    /**
     * Технический ID БД.
     */
    private Long id;

    /**
     * Безопасный внешний / межсервисный идентификатор стратегии.
     */
    private String internalId;

    /**
     * Инструмент, для которого предназначена стратегия.
     */
    private Long instrumentId;

    /**
     * Человекочитаемое имя стратегии.
     */
    private String name;

    /**
     * Версия append-only стратегии.
     * При изменении стратегия не редактируется, а создаётся заново.
     */
    private Integer version;

    /**
     * Статус контейнера стратегии.
     */
    private StrategyStatus status;

    /**
     * Ровно одна detail на одну фазу рынка.
     */
    private List<StrategyDetails> details;

    public StrategyDetails getActiveDetails(MarketPhase.Type marketPhaseType) {
        if (Objects.isNull(marketPhaseType) || Objects.isNull(this.details)) {
            return null;
        }

        return this.details.stream()
                           .filter(Objects::nonNull)
                           .filter(item -> Objects.equals(item.getMarketPhaseType(), marketPhaseType))
                           .findFirst()
                           .orElse(null);
    }

    public boolean isActive() {
        return Objects.equals(this.status, StrategyStatus.ACTIVE);
    }

    public boolean isNotActive() {
        return BooleanUtils.isFalse(isActive());
    }
}
