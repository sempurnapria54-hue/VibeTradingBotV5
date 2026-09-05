package com.example.tradingbot.domain.model.trade.market_snapshot;

import com.example.tradingbot.domain.model.Auditable;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Срез книги заявок инструмента на момент.
 *
 * <p>Дом модели — {@code docs/models/domain/other/MarketOrderBook.md}.
 *
 * <p><b>Ряд невосполнимый:</b> пропущенный срез не добывается потом — у
 * площадки нет чтения «каким был стакан в прошлый вторник». Отсюда и
 * правило хранения ({@code docs/rules/market-data-retention.md}).
 *
 * <p><b>Слова «снимок» в имени нет намеренно:</b> маркер уровня в
 * проекте — суффикс {@code ExternalSnapshot} у формы ответа площадки, и
 * доменное имя, оканчивающееся тем же словом, дало бы граничную форму
 * {@code …SnapshotExternalSnapshot}, в которой маркер перестаёт
 * читаться.
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketOrderBook extends Auditable {

    /** Внутренний идентификатор среза. */
    private Long id;

    /** Инструмент, чья книга снята. */
    private Long instrumentId;

    /**
     * Метка времени ПЛОЩАДКИ: момент, к которому относится книга.
     * Вместе с {@code instrumentId} образует ключ идентичности среза.
     */
    private Long externalTimestamp;

    /**
     * Наша метка приёма. Разность с биржевой и есть задержка, по которой
     * разбирают инцидент; одна метка на обе роли делает её неизмеримой.
     */
    private Long observedTimestamp;

    /** Уровни покупки, от лучшего к худшему. */
    private List<OrderBookLevel> bids;

    /** Уровни продажи, от лучшего к худшему. */
    private List<OrderBookLevel> asks;
}
