package com.example.connector.okx.snapshot;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Уровень книги заявок в разобранной форме.
 *
 * <p>Отдельным типом, а не вложенным классом: так же разведены прочие
 * граничные снапшоты корпуса ({@code BalanceExternalSnapshot} рядом с
 * {@code BalanceContainerExternalSnapshot}).
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderBookLevelExternalSnapshot {

    /** Цена уровня. */
    private BigDecimal price;

    /** Объём на уровне. */
    private BigDecimal size;

    /** Число заявок на уровне. */
    private Integer orderCount;
}
