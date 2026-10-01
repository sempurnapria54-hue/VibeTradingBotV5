package com.example.tradingbot.domain.model.core.balance;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.Auditable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted account-state snapshot aggregate по exchange account:
 * последняя известная картина equity / available средств. Не trading
 * runtime entity (нет Status / lifecycle / live risk). Обновляется
 * только REFRESH_BALANCE (replace semantics). Свежесть вычисляется
 * (externalUpdatedAt + expiration), не хранится. См.
 * docs/models/domain/core/BalanceContainer.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class BalanceContainer extends Auditable {

    /** Внутренний технический идентификатор snapshot в БД. */
    private Long id;

    /**
     * <b>Биржевой счёт</b>, которому принадлежит снимок
     * (docs/models/domain/core/BalanceContainer.md). Радиус — счёт, а не
     * площадка: средства одного тенанта не описывают состояние другого
     * (docs/architecture/tenant-and-exchange.md §«Торговая строка называет
     * счёт, и радиусы читаются от него»).
     */
    private Long exchangeAccountId;

    /**
     * Момент, на который биржа собрала сведения о счёте, — база проверки
     * свежести (docs/models/mapping/Balance.md).
     */
    private OffsetDateTime externalUpdatedAt;

    /** Total equity аккаунта по данным биржи. */
    private BigDecimal externalTotalEquity;

    /**
     * Adjusted / effective equity. Риск-контур его не читает
     * (docs/models/domain/core/BalanceContainer.md).
     */
    private BigDecimal externalAdjustedEquity;

    /** Account-level available equity. */
    private BigDecimal externalAvailableEquity;

    /**
     * Режим счёта площадки на момент снимка — посылка контура «только свои
     * средства» (docs/rules/trading-constraints.md). Пусто — снимок режима
     * не несёт либо площадка назвала значение вне словаря; преконтроль
     * читает пустоту как режим вне контура, а не как умолчание
     * (docs/models/mapping/Balance.md).
     */
    private AccountMode accountMode;

    /**
     * Режим позиций счёта на момент снимка — посылка нетто-позиций
     * (docs/rules/trading-constraints.md). Пусто — как у режима счёта.
     */
    private PositionMode positionMode;

    /** Балансы по валютам (для SWAP/USDT обязательна settle currency). */
    private List<Balance> balances;

    /**
     * Снимок не старше названного рубежа: момент снимка — момент, на
     * который биржа собрала сведения о счёте
     * (docs/models/mapping/Balance.md), — лежит после него.
     *
     * <p><b>Толерантность приносит спрашивающий</b> — тем же доводом, что
     * у свежести рыночных данных (docs/rules/market-data-freshness.md):
     * один и тот же снимок для сайзинга свеж, а для сверки уже нет.
     * Пустой момент снимка читается как несвежесть: «не знаем, когда»
     * благоприятного прочтения не имеет
     * (docs/rules/absent-value-semantics.md).
     */
    public Boolean isFresherThan(OffsetDateTime threshold) {
        return nonNull(externalUpdatedAt) && nonNull(threshold) && externalUpdatedAt.isAfter(threshold);
    }

    /**
     * Режим снимка вне контура: контур держит фьючерсный режим счёта и
     * нетто-позиции, всё прочее — выход из него (docs/rules/trading-constraints.md;
     * форма — docs/spec/risk-limits.json, величина
     * {@code accountModeOutOfContour}, чью посылку свежести снимка проверяет
     * вызывающий).
     *
     * <p><b>Пустой режим — тоже выход из контура:</b> непроверенная посылка
     * выполненной не читается (docs/concept.md П1), а пустота здесь значит
     * «снимок режима не несёт либо площадка назвала значение вне словаря».
     *
     * <p><b>Изъят из сериализации:</b> снимок уезжает по проводу из коннектора
     * в ядро, и нульарный {@code is}-метод сериализатор вывел бы ключом без
     * поля (.claude/rules/codestyle.md §«Предикат формы, пересекающей
     * сериализацию, не является её свойством»).
     */
    @JsonIgnore
    public Boolean isAccountModeOutOfContour() {
        return isFalse(AccountMode.FUTURES.equals(accountMode) && PositionMode.NET.equals(positionMode));
    }

    /** Полная замена currency-level списка: новый валидный snapshot вытесняет старый (REFRESH_BALANCE). */
    public void replaceBalances(List<Balance> newBalances) {
        this.balances = newBalances;
    }
}
