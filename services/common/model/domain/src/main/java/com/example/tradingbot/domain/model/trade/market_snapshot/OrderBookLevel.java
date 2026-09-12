package com.example.tradingbot.domain.model.trade.market_snapshot;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Один уровень книги заявок: цена, объём и число заявок на нём.
 *
 * <p>Своей таблицы не имеет — уровни живут навесом в строке среза
 * ({@code docs/models/domain/other/MarketOrderBook.md} §Персистентность).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookLevel {

    /** Цена уровня. */
    private BigDecimal price;

    /** Объём на уровне. */
    private BigDecimal size;

    /**
     * Число заявок на уровне. По нему видно, один это крупный участник
     * или много мелких, — различие, которое объём в одиночку скрывает.
     */
    private Integer orderCount;
}
