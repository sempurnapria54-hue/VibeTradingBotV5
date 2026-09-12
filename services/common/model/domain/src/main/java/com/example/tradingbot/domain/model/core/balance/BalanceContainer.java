package com.example.tradingbot.domain.model.core.balance;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.Auditable;
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

    /** Время обновления account-level snapshot на бирже (база freshness-check). */
    private OffsetDateTime externalUpdatedAt;

    /** Total equity аккаунта по данным биржи. */
    private BigDecimal externalTotalEquity;

    /** Adjusted / effective equity (предпочтительная база risk-policy). */
    private BigDecimal externalAdjustedEquity;

    /** Account-level available equity. */
    private BigDecimal externalAvailableEquity;

    /** Балансы по валютам (для SWAP/USDT обязательна settle currency). */
    private List<Balance> balances;

    /**
     * Снимок не старше названного рубежа: время обновления на стороне
     * биржи лежит после него.
     *
     * <p><b>Толерантность приносит спрашивающий</b> — тем же доводом, что
     * у свежести рыночных данных (docs/rules/market-data-freshness.md):
     * один и тот же снимок для сайзинга свеж, а для сверки уже нет.
     * Пустое время обновления читается как несвежесть: «не знаем, когда»
     * благоприятного прочтения не имеет
     * (docs/rules/absent-value-semantics.md).
     */
    public Boolean isFresherThan(OffsetDateTime threshold) {
        return nonNull(externalUpdatedAt) && nonNull(threshold) && externalUpdatedAt.isAfter(threshold);
    }

    /** Полная замена currency-level списка: новый валидный snapshot вытесняет старый (REFRESH_BALANCE). */
    public void replaceBalances(List<Balance> newBalances) {
        this.balances = newBalances;
    }
}
