package com.example.tradingbot.domain.model.aggregate.strategy;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Главный immutable-контейнер торговой стратегии: торговые правила,
 * условия, настройки расчёта рыночных данных и ожидаемые действия.
 * Говорит, что должно быть создано/изменено/отменено и при каких
 * условиях; FSM сделки решает, когда интерпретировать правила.
 * Изменение правил = новая стратегия (правок на месте нет — меняется
 * только административный статус контейнера). Устаревшие рыночные
 * данные статус не меняют (свежесть — MarketDataExpirationChecker).
 * См. docs/models/domain/aggregate/Strategy.md,
 * docs/lifecycles/Strategy.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class Strategy extends Auditable {

    /** Технический ID. */
    private Long id;

    /** Безопасный внешний/межсервисный идентификатор. */
    private String internalId;

    /** Внутренний ID инструмента стратегии. */
    private Long instrumentId;

    /** Человекочитаемое имя стратегии. */
    private String name;

    /** Административный статус (lifecycle). */
    private Status status;

    /** Настройка расчёта фазы рынка (одна: фаза нужна до выбора detail). */
    private StrategyMarketPhaseSetting marketPhaseSetting;

    /** Детали по фазам рынка: ровно одна на один MarketPhase.Type. */
    private List<StrategyDetail> details;

    /** Стратегия активна (единственная активная стратегия инструмента). */
    public Boolean isActive() {
        return Objects.equals(status, Status.ACTIVE);
    }

    /** Стратегия логически удалена (терминальный статус). */
    public Boolean isDeleted() {
        return Objects.equals(status, Status.DELETED);
    }

    /**
     * Допустим ли административный переход статуса: CREATED → ACTIVE,
     * ACTIVE ↔ INACTIVE, любой нетерминальный → DELETED; DELETED —
     * терминален (docs/lifecycles/Strategy.md).
     */
    public Boolean canTransitionTo(Status target) {
        if (isDeleted()) {
            return false;
        }
        return switch (target) {
            case ACTIVE -> Objects.equals(status, Status.CREATED) || Objects.equals(status, Status.INACTIVE);
            case INACTIVE -> isActive();
            case DELETED -> true;
            case CREATED -> false;
        };
    }

    /**
     * Административный статус стратегии. Задаёт жизненный цикл всех
     * вложенных immutable-настроек; runtime-свежесть данных не
     * описывает. Эффекты на сделки — docs/lifecycles/Strategy.md.
     */
    public enum Status {

        /** Создана, в активное использование не введена. */
        CREATED,

        /** Единственная активная стратегия инструмента: разрешает новые сделки. */
        ACTIVE,

        /** Временно не участвует в новых сделках; открытые живут по pinned detail. */
        INACTIVE,

        /** Логически удалена; открытые сделки — graceful shutdown. */
        DELETED
    }
}
