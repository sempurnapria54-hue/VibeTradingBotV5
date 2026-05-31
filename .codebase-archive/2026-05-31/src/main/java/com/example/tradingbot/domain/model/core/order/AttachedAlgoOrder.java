package com.example.tradingbot.domain.model.core.order;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

import static io.micrometer.common.util.StringUtils.isNotBlank;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Прикреплённый защитный algo-ордер обычного ордера.
 */
@Getter
@Setter
public class AttachedAlgoOrder extends Auditable {

    private static final Set<String> ACTIVE_LIKE_STATUS_NAMES = Set.of(
            Status.ATTACHED.name(),
            Status.ACTIVE.name()
    );

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

    public static Set<String> activeLikeStatusNames() {
        return ACTIVE_LIKE_STATUS_NAMES;
    }

    public boolean isActiveLike() {
        return Objects.equals(status, Status.ATTACHED) || Objects.equals(status, Status.ACTIVE);
    }

    public boolean isTerminal() {
        return Objects.equals(status, Status.CLOSED) || Objects.equals(status, Status.FAILED);
    }

    public boolean canTransitionTo(Status targetStatus) {
        if (Objects.isNull(targetStatus)) {
            return false;
        }
        if (Objects.equals(status, targetStatus)) {
            return true;
        }
        if (Objects.isNull(status)) {
            return Objects.equals(targetStatus, Status.CREATED);
        }

        return switch (status) {
            case CREATED -> Objects.equals(targetStatus, Status.ATTACHED)
                    || Objects.equals(targetStatus, Status.FAILED);
            case ATTACHED -> Objects.equals(targetStatus, Status.ACTIVE)
                    || Objects.equals(targetStatus, Status.CLOSED)
                    || Objects.equals(targetStatus, Status.FAILED);
            case ACTIVE -> Objects.equals(targetStatus, Status.CLOSED)
                    || Objects.equals(targetStatus, Status.FAILED);
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
        if (isFalse(canTransitionTo(targetStatus))) {
            throw new IllegalStateException(
                    "Illegal AttachedAlgoOrder transition: "
                            + status
                            + " -> "
                            + targetStatus
                            + " for internalId="
                            + internalId
                            + ", externalId="
                            + externalId
                            + ", externalAttachedId="
                            + externalAttachedId
            );
        }

        this.status = targetStatus;
    }
}
