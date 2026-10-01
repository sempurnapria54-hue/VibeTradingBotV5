package com.example.tradingbot.message;

import java.math.BigDecimal;

/**
 * Содержимое события «решение об отдельной условной заявке»
 * (docs/architecture/contracts.md §«Решение об отдельной условной заявке —
 * свой класс `AlgoOrderDecided`, а не значение решения о заявке»).
 *
 * <p><b>Идентичности — теми же компонентами верхнего уровня, что у решения
 * о заявке:</b> читатель журнала форм не знает и достаёт из содержимого
 * только одноимённые компоненты верхнего уровня
 * (docs/models/domain/other/AuditRecord.md).
 *
 * <p><b>Предшественник в цепочке замещений несущий:</b> без него
 * перестановка ноги и первичная постановка в содержимом неразличимы, а
 * потребитель базы ядра не читает. Пустота — значение «первичная
 * постановка», а не пробел (docs/architecture/contracts.md §«Решение о
 * заявке несёт предшественника в цепочке замещений»).
 *
 * <p><b>Параметры условия — ради них решение о защите и журналируется:</b>
 * уровни триггеров обеих ног с их ценовой базой и параметры трейлинга.
 * Биржевые поля условия ({@code external*}) не едут — на момент решения их
 * нет. Нога, которой у условия нет, едет пустой: пустота кладётся
 * отсутствующей, а не текстом (docs/architecture/contracts.md §«Пустое
 * значение едет пустым, а не текстом»).
 *
 * <p><b>Актора нет:</b> ручной тропы у класса нет, решение принимает проход
 * (docs/spec/event-actor-presence.json).
 *
 * <p>Запись, а не {@code @Value}: у значения есть ЧИТАТЕЛЬ за
 * сериализацией (.claude/rules/codestyle.md §«Неизменяемое значение,
 * пересекающее сериализацию»). Строит её маппер производителя, а не домен.
 *
 * @param algoOrderInternalId         идентичность условной заявки
 * @param dealInternalId              сделка условной заявки
 * @param dealTrancheInternalId       транш, чью экспозицию заявка защищает
 * @param exchangeAccountInternalId   биржевой счёт условной заявки
 * @param instrumentInternalId        инструмент условной заявки
 * @param replacesInternalId          предшественник в цепочке замещений;
 *                                    пусто — первичная постановка
 * @param conditionType               тип условия — имя значения
 *                                    {@code AlgoOrder.ConditionType};
 *                                    область значений домовая
 *                                    (docs/models/domain/core/AlgoOrder.md),
 *                                    и здесь она не переписывается
 * @param direction                   сторона — имя значения
 *                                    {@code AlgoOrder.Direction}; область
 *                                    значений домовая (там же)
 * @param sizeContracts               размер в контрактах
 * @param stopLossTriggerPrice        уровень триггера ноги остановки убытка;
 *                                    пусто — ноги нет
 * @param stopLossTriggerPriceType    ценовая база триггера ноги остановки
 *                                    убытка — имя значения
 *                                    {@code AlgoOrder.TriggerPriceType}
 * @param takeProfitTriggerPrice      уровень триггера ноги фиксации
 *                                    прибыли; пусто — ноги нет
 * @param takeProfitTriggerPriceType  ценовая база триггера ноги фиксации
 *                                    прибыли — имя значения
 *                                    {@code AlgoOrder.TriggerPriceType}
 * @param trailingPercents            отступ трейлинга в процентах; пусто у
 *                                    триггерного условия
 * @param trailingStepValue           отступ трейлинга в абсолютном значении;
 *                                    пусто у триггерного условия
 * @param trailingActivationPrice     цена активации трейлинга; пусто —
 *                                    активен сразу либо условие триггерное
 * @param trailingActivationPriceType ценовая база цены активации трейлинга —
 *                                    имя значения
 *                                    {@code AlgoOrder.TriggerPriceType}
 */
public record AlgoOrderDecidedMessage(String algoOrderInternalId,
                                      String dealInternalId,
                                      String dealTrancheInternalId,
                                      String exchangeAccountInternalId,
                                      String instrumentInternalId,
                                      String replacesInternalId,
                                      String conditionType,
                                      String direction,
                                      BigDecimal sizeContracts,
                                      BigDecimal stopLossTriggerPrice,
                                      String stopLossTriggerPriceType,
                                      BigDecimal takeProfitTriggerPrice,
                                      String takeProfitTriggerPriceType,
                                      BigDecimal trailingPercents,
                                      BigDecimal trailingStepValue,
                                      BigDecimal trailingActivationPrice,
                                      String trailingActivationPriceType) {
}
