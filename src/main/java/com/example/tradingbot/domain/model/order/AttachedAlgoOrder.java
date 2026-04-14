package com.example.tradingbot.domain.model.order;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import static io.micrometer.common.util.StringUtils.isNotBlank;

@Getter
@Setter
public class AttachedAlgoOrder extends Auditable {

    /**
     * Внутренний идентификатор.
     */
    private Long id;

    /**
     * Ссылка на ордер.
     */
    private Long orderId;

    /**
     * Межсервисный идентификатор.
     */
    private String internalId;

    /**
     * Идентификатор привязанного algo-ордера на бирже, пока он не выполнен.
     */
    private String externalAttachedId;

    /**
     * Идентификатор algo-ордера на бирже, когда он уже создан на бирже.
     */
    private String externalId;

    /**
     * Текущий внутренний статус.
     */
    private Status status;

    /**
     * Внутренний тип algo-ордера.
     */
    private Type type;

    /**
     * Состояние algo-ордера на стороне биржи.
     */
    private String externalStatus;

    /**
     * Биржевой тип algo-ордера.
     */
    private String externalType;

    /**
     * Объём algo-ордера.
     */
    private BigDecimal size;

    /**
     * Триггерная цена stop-loss. (Всегда заходим по рынку в slOrdPx)
     */
    private BigDecimal stopLossTriggerPrice;

    public enum Type {
        ATTACHED_STOP_LOSS,
    }

    public enum Status {
        /**
         * Запись создана локально, ещё не отправляли
         */
        CREATED,
        /**
         * Запрос на attach ушёл/принят (или внешний attached id получен)
         */
        ATTACHED,
        /**
         * SL реально активен на бирже (после fill)
         */
        ACTIVE,
        /**
         * Отменён/сработал
         */
        CLOSED,
        /**
         * Не удалось создать/обновить
         */
        FAILED
    }

    public boolean isActiveLike() {
        return status == Status.ATTACHED || status == Status.ACTIVE;
    }

    public boolean isTerminal() {
        return status == Status.CLOSED || status == Status.FAILED;
    }

    public boolean canTransitionTo(Status targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        if (status == targetStatus) {
            return true;
        }
        if (status == null) {
            return targetStatus == Status.CREATED
                    || targetStatus == Status.ATTACHED
                    || targetStatus == Status.FAILED;
        }

        return switch (status) {
            case CREATED -> targetStatus == Status.ATTACHED || targetStatus == Status.FAILED;
            case ATTACHED -> targetStatus == Status.ACTIVE
                    || targetStatus == Status.CLOSED
                    || targetStatus == Status.FAILED;
            case ACTIVE -> targetStatus == Status.CLOSED || targetStatus == Status.FAILED;
            case CLOSED, FAILED -> false;
        };
    }

    public void toAttached() {
        transitionTo(Status.ATTACHED);
    }

    public void toActive() {
        transitionTo(Status.ACTIVE);
    }

    public void toClose() {
        transitionTo(Status.CLOSED);
    }

    public void toFail() {
        transitionTo(Status.FAILED);
    }

    public boolean hasExternalType() {
        return isNotBlank(externalType);
    }

    private void transitionTo(Status targetStatus) {
        if (canTransitionTo(targetStatus)) {
            this.status = targetStatus;
        }
    }
}
