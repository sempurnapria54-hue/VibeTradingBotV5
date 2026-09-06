package com.example.tradingcore.domain.command.payload;

import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Параметры создания обычной заявки.
 *
 * <p><b>Числа риска приходят рассчитанными.</b> Величину производит
 * преконтроль ЭТОГО ЖЕ действия, и внутри прохода она доезжает сюда полем:
 * durable-слот между проходами не нужен, а пересчёт формулы в исполнителе
 * завёл бы второй её экземпляр
 * (docs/components/CreateOrderExecutor.md §«Что пишет для входного
 * действия»).
 *
 * <p>Клиентский и биржевой идентификаторы в параметрах не едут: первый
 * генерирует исполнитель, второй приносит площадка.
 */
@Value
@Builder
public class CreateOrderCommandPayload implements ServiceCommandPayload {

    /** Бизнес-тип заявки. */
    Order.Type orderType;

    /** Сторона заявки — доменный перечень, не литерал площадки. */
    Order.Side side;

    /** Размер в контрактах — результат расчёта размера. */
    BigDecimal sizeContracts;

    /** Цена размещения. */
    BigDecimal price;

    /**
     * Слать ли цену на площадку. У рыночной заявки цена расчётная и
     * остаётся локальной: отправленная, она сделала бы заявку лимитной.
     */
    Boolean sendPriceToExchange;

    /** Доменное намерение «только уменьшать позицию». */
    Boolean positionReducingOnly;

    /** Встроенная защита, уходящая на площадку вместе с ногой; пусто — её нет. */
    AttachedProtectionPayload attachedProtection;

    /** Транш, чью экспозицию нога создаёт или гасит. */
    Long dealTrancheId;

    /**
     * Цена входа, по которой считался риск ноги. У рыночного входа —
     * расчётная референс-цена, на площадку не отправляемая.
     */
    BigDecimal plannedEntryPrice;

    /** Уровень стопа, под который считался риск ноги. */
    BigDecimal plannedStopPrice;

    /** Плановый риск ноги — убыток на её стопе при постановке. */
    BigDecimal plannedRiskAmount;

    /** Валюта планового риска ноги — расчётная валюта инструмента. */
    String plannedRiskCurrency;

    /** Размер контракта на момент постановки ноги. */
    BigDecimal plannedContractValue;

    /**
     * Измеритель: наблюдаемый запас до ликвидации. <b>Пуст законно</b> —
     * у открывающего входа позиции ещё нет, мерить не от чего.
     */
    BigDecimal liquidationDistanceRatio;

    /**
     * Измеритель: наблюдаемая ёмкость стакана. <b>Пуста законно</b> —
     * свежих рыночных данных в контексте расчёта могло не быть.
     */
    BigDecimal bookDepthAtPlacement;
}
