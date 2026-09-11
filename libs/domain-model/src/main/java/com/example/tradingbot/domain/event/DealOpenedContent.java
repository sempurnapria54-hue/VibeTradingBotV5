package com.example.tradingbot.domain.event;

import static com.example.tradingbot.domain.util.EnumNames.name;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;

/**
 * Содержимое события «сделка создана»: идентичности, причина заведения и
 * <b>контекст входа</b> — фаза рынка, по которой выбрана деталь, и
 * идентичность определения (docs/architecture/contracts.md §«Событие
 * создания сделки несёт контекст входа — фазу рынка и идентичность
 * определения»).
 *
 * <p><b>Половины пары адресуют разное.</b> Идентичность определения несут
 * оба класса сделки — без неё отчёт по определению не собирается с момента
 * входа; фаза остаётся атрибутом входа и на терминале вторым полем не
 * заводится.
 *
 * <p><b>У восстановленной сделки обе половины пусты, и это значение, а не
 * пробел:</b> входа по объявлению у неё не было, и пустота означает «сделка
 * заведена вокруг живого риска»
 * (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Области значений перечней — домовые, и здесь они не
 * переписываются:</b> {@code Deal.EntryReason} —
 * docs/models/domain/aggregate/Deal.md, {@code StrategyTradeDirection} —
 * docs/models/domain/aggregate/Strategy.md, {@code MarketPhase.Type} —
 * docs/models/domain/other/MarketPhase.md. Дома у направления и у причины
 * <b>разные</b>, хотя оба поля лежат на сделке: направление — перечень слоя
 * стратегии. Правило формы — docs/architecture/contracts.md, домен значения
 * содержимого объявляется указателем.
 *
 * @param dealInternalId            идентичность сделки
 * @param exchangeAccountInternalId биржевой счёт сделки
 * @param instrumentInternalId      инструмент сделки
 * @param strategyInternalId        определение стратегии; пусто у
 *                                  восстановленной сделки
 * @param entryReason               причина заведения — имя значения
 *                                  {@code Deal.EntryReason}; она и есть то,
 *                                  ради чего событие заведено
 * @param direction                 направление сделки — имя значения
 *                                  {@code StrategyTradeDirection}
 * @param entryMarketPhase          фаза рынка — имя значения
 *                                  {@code MarketPhase.Type}; по ней выбрана
 *                                  закреплённая деталь; пусто у
 *                                  восстановленной сделки
 */
public record DealOpenedContent(String dealInternalId,
                                String exchangeAccountInternalId,
                                String instrumentInternalId,
                                String strategyInternalId,
                                String entryReason,
                                String direction,
                                String entryMarketPhase) {

    /**
     * Содержимое создания по сделке и идентичностям её радиуса.
     *
     * <p>Фабрика держит приведение перечней к именам: у фазы входа пустота
     * законна (восстановительная тропа), и {@code String.valueOf} завёл бы
     * на её месте литерал {@code "null"}.
     */
    public static DealOpenedContent of(Deal deal, String exchangeAccountInternalId,
                                       String instrumentInternalId, String strategyInternalId) {
        return new DealOpenedContent(deal.getInternalId(),
                exchangeAccountInternalId,
                instrumentInternalId,
                strategyInternalId,
                name(deal.getEntryReason()),
                name(deal.getDirection()),
                name(deal.getEntryMarketPhase()));
    }
}
