package com.example.tradingbot.domain.model.aggregate.strategy;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    /**
     * <b>Тенант-владелец определения</b> — его {@code internalId}.
     *
     * <p><b>Несётся строкой у ВЛАДЕЛЬЦА определений и пуст у копии
     * ядра.</b> Признак применимости механический: держит ли сервис
     * локальную строку счёта. У {@code strategies} её нет вовсе, поэтому
     * тенант обязан лежать на строке; у ядра есть проекция реестра, и
     * там тенант резолвится по счёту, как у сделки
     * (docs/architecture/tenant-and-exchange.md §«Торговая строка
     * называет счёт, и радиусы читаются от него»).
     *
     * <p><b>Пустота у объекта, собранного из копии, читается как «не
     * хранится этой стороной», а не «тенанта нет»</b>
     * (docs/rules/absent-value-semantics.md).
     *
     * <p>Писатель — приёмник команды создания у владельца, значение
     * берётся <b>из контекста вызова</b>, а не из тела: тело приходит от
     * вызывающего, и принятый из него тенант был бы его собственным
     * объявлением о себе.
     */
    private String tenantId;

    /**
     * <b>Биржевой счёт тенанта, на котором стратегия торгует</b> — его
     * {@code internalId}, а не ключ чьей-либо базы: числовые ключи баз
     * границу сервиса не пересекают
     * (docs/architecture/data-ownership.md §Идентификаторы). Радиусы
     * читаются от счёта (docs/architecture/tenant-and-exchange.md
     * §«Торговая строка называет счёт, и радиусы читаются от него»).
     */
    private String exchangeAccountInternalId;

    /** Инструмент стратегии — его {@code internalId}. */
    private String instrumentInternalId;

    /**
     * Числовой ключ инструмента в базе донора.
     *
     * <p><b>Целевой модели поле не принадлежит:</b> определение адресует
     * контекст идентичностями, а числовые ключи баз границу сервиса не
     * пересекают (docs/architecture/data-ownership.md §Идентификаторы);
     * у сервисов монорепозитория этот ключ живёт на строке
     * персистентности и резолвится из {@link #instrumentInternalId} на
     * границе domain → persistence (docs/models/mapping/Strategy.md).
     *
     * <p>Поле держится ради донора, который читает его в пяти файлах, а
     * условие его жизни — «собирается и зелёный» (donor/README.md):
     * порт сверяется с работающим оригиналом. Сервисы его не пишут и не
     * читают. Снятие — `.claude/work/backlog.md` §«Донорский числовой
     * ключ инструмента у определения в общей библиотеке».
     */
    private Long instrumentId;

    /** Человекочитаемое имя стратегии. */
    private String name;

    /** Административный статус (lifecycle). */
    private Status status;

    /** Настройка расчёта фазы рынка (одна: фаза нужна до выбора detail). */
    private StrategyMarketPhaseSetting marketPhaseSetting;

    /** Детали по фазам рынка: ровно одна на один MarketPhase.Type. */
    private List<StrategyDetail> details;

    /**
     * Реестр (каталог) настроек индикаторов **этой стратегии**: объявлены
     * раз на стратегию, UNIQUE(strategy_id, key). Принадлежит стратегии —
     * per-strategy объявление: настройка принадлежит стратегии, а
     * результат её вычисления — нет (он ключуется идентичностью
     * вычисления, docs/models/domain/other/IndicatorValue.md). Фаза, детали,
     * действия и условия настройки не хранят — ссылаются по {@code key} в
     * пределах стратегии.
     */
    private List<StrategyIndicatorSetting> indicatorSettings;

    /**
     * Реестр (каталог) настроек структуры рынка **этой стратегии** (объявлены
     * раз, UNIQUE(strategy_id, key); per-strategy, не общий config-реестр) —
     * адресуются по {@code key}.
     */
    private List<StrategyMarketStructureSetting> marketStructureSettings;

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
     * Деталь для типа фазы рынка (инвариант — ровно одна на MarketPhase.Type);
     * пусто, если детали под эту фазу нет.
     */
    public Optional<StrategyDetail> detailForPhase(MarketPhase.Type phaseType) {
        if (isEmpty(details)) {
            return Optional.empty();
        }
        return details.stream()
                .filter(detail -> Objects.equals(phaseType, detail.getMarketPhaseType()))
                .findFirst();
    }

    /**
     * Хоть одна деталь стратегии читает цену.
     *
     * <p><b>Вопрос агрегатный, потому что задаётся ДО выбора детали.</b>
     * Деталь выбирается фазой, а фаза приезжает тем же чтением рыночных
     * операндов, что и цена: спросить «читает ли цену выбранная деталь»
     * на отборе входа не у кого. Ответ по всей стратегии — надмножество
     * ответа по детали, и цена этого надмножества — round-trip наружу у
     * стратегии, чья цену читает не всякая деталь
     * ({@link StrategyDetail#readsPrice()}).
     */
    public Boolean readsPrice() {
        return emptyIfNull(details).stream()
                .anyMatch(detail -> isTrue(detail.readsPrice()));
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
