package com.example.tradingbot.domain.event;

import static com.example.tradingbot.domain.util.EnumNames.name;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;

/**
 * Содержимое события «сделка перестала вестись штатно»: идентичности плюс
 * причина остановки и состояние, в которое сделка ушла.
 *
 * <p><b>Причина и есть то, ради чего класс заведён.</b> Клейма таймлайна —
 * «почему сделка перестала вестись штатно сейчас» — другого носителя не
 * имеет: терминальное событие называет причину ЗАКРЫТИЯ, а она эту не
 * заменяет (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
 *
 * <p><b>У одной сделки таких событий бывает два, и второе не подавляется:</b>
 * причина, записанная на ребре в отложенный выход, заменяется холдовой на
 * ребре в ошибочное состояние, и второе событие описывает ДРУГОЕ
 * происшествие (docs/architecture/contracts.md §«У одной сделки бывает
 * БОЛЬШЕ ОДНОГО `DealShutdownInitiated`, и это свойство класса, а не
 * дефект»).
 *
 * <p><b>Актор едет содержимым, потому что у класса есть ручная тропа:</b>
 * держатель сворачивает радиус той же ступенью, а первый ход её
 * энфорсмента уводит сделки радиуса тем же ребром, что и проход
 * (docs/rules/manual-halt.md, docs/spec/event-actor-presence.json).
 * Область значений — та же, что у полей аудита сущности: <b>класс</b>, а
 * не идентификатор пользователя (docs/models/domain/other/Auditable.md
 * §«Область значений актора»).
 *
 * @param dealInternalId            идентичность сделки
 * @param exchangeAccountInternalId биржевой счёт сделки
 * @param instrumentInternalId      инструмент сделки
 * @param strategyInternalId        определение стратегии; пусто у
 *                                  восстановленной сделки
 * @param status                    состояние, в которое сделка ушла этим
 *                                  ребром — имя значения
 *                                  {@code Deal.Status}; область значений
 *                                  домовая
 *                                  (docs/models/domain/aggregate/Deal.md),
 *                                  и здесь она не переписывается
 * @param shutdownReason            причина выхода из штатного ведения —
 *                                  имя значения
 *                                  {@code Deal.ShutdownReason}; область
 *                                  значений домовая (там же), и здесь она
 *                                  не переписывается
 * @param actor                     кто инициировал ход: имя предъявленного
 *                                  принципала либо класс собственного
 *                                  прохода
 */
public record DealShutdownInitiatedContent(String dealInternalId,
                                           String exchangeAccountInternalId,
                                           String instrumentInternalId,
                                           String strategyInternalId,
                                           String status,
                                           String shutdownReason,
                                           String actor) {

    /**
     * Содержимое по сделке, уже сведённой ребром, и идентичностям её
     * радиуса.
     *
     * <p><b>Фабрика существует потому, что рёбер два</b>, и второй список
     * компонентов разошёлся бы с первым молча
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
     * Перечни приводятся к именам своих значений; пустой перечень остаётся
     * пустым.
     */
    public static DealShutdownInitiatedContent of(Deal deal, String exchangeAccountInternalId,
                                                  String instrumentInternalId,
                                                  String strategyInternalId,
                                                  String actor) {
        return new DealShutdownInitiatedContent(deal.getInternalId(),
                exchangeAccountInternalId,
                instrumentInternalId,
                strategyInternalId,
                name(deal.getStatus()),
                name(deal.getShutdownReason()),
                actor);
    }
}
