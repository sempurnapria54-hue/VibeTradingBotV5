package com.example.tradingbot.message;

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
 * <p><b>Форму строит маппер производителя, а не домен.</b> Прежде здесь
 * стояла фабрика {@code of(...)}, принимавшая доменную модель, — то есть
 * форма провода собиралась доменным кодом. Перевод в форму сообщения
 * принадлежит границе (.claude/rules/codestyle.md §«Слой сообщения:
 * внутренняя шина»), и делает его маппер сервиса-производителя.
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
public record DealOpenedMessage(String dealInternalId,
                                String exchangeAccountInternalId,
                                String instrumentInternalId,
                                String strategyInternalId,
                                String entryReason,
                                String direction,
                                String entryMarketPhase) {
}
