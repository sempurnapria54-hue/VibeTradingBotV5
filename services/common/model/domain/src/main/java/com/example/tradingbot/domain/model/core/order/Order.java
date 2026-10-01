package com.example.tradingbot.domain.model.core.order;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ordinary exchange order, связанный с Deal. Хранит локальный intent,
 * идентификаторы (internalId — stable client id + биржевой externalId),
 * доменный статус, сырой статус биржи (диагностика), цену/размер, факты
 * исполнения, embedded attached protection. Не действие стратегии:
 * связь StrategyAction ↔ Order — через DealActionState, поэтому не
 * хранит strategyActionId / role / level. См.
 * docs/models/domain/core/Order.md, docs/lifecycles/Order.md.
 *
 * <p><b>Нульарные {@code is}-предикаты изъяты из сериализации</b>
 * ({@code @JsonIgnore}): заявка едет телом команды к коннектору и ответом
 * его чтения, а вычисленный ответ свойством формы не является — ключом без
 * поля он ушёл бы к читателю на другой стороне провода.
 */
@Getter
@Setter
@NoArgsConstructor
public class Order extends Auditable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Сделка, к которой относится ордер. */
    private Long dealId;

    /**
     * Транш, чью экспозицию заявка создаёт или гасит. Заявка принадлежит
     * траншу; deal-уровень остаётся агрегатным представлением.
     */
    private Long dealTrancheId;


    /** Межсервисный id; stable client id (OKX clOrdId). */
    private String internalId;

    /** Биржевой id ordinary order (OKX ordId). */
    private String externalId;

    /**
     * Биржевое имя инструмента, к которому строка относится <b>по словам
     * источника</b>; непусто только у строки, приехавшей чтением границы.
     *
     * <p><b>Атрибут ГРАНИЦЫ, а не хранения:</b> у нашей строки инструмент
     * известен через сделку, и колонки под это поле нет. Заводится оно
     * ради <b>счёт-широких</b> чтений: они возвращают строки всего счёта
     * сразу, и адресовать их нечем — локального идентификатора у чужой
     * строки не бывает по построению, а именно она и есть предмет
     * проактивной детекции (docs/components/AnomalyJob.md §«Что ищет»,
     * `A2`).
     *
     * <p>Резолв «биржевое имя → наш инструмент» здесь не делается
     * намеренно: его отсутствие — сам признак детектора, и подставленный
     * идентификатор погасил бы находку.
     */
    private String externalInstrumentId;

    /** Доменный статус. */
    private Status status;

    /** Причина финализации / перевода в ERROR. */
    private CloseReason closeReason;

    /** Бизнес-тип заявки — наличие встроенной защиты; направление риска несёт намерение. */
    private Type type;

    /**
     * Сторона заявки — доменный перечень, не литерал площадки.
     * Отличается от направления сделки: закрывающая заявка на длинной
     * ноге имеет сторону {@link Side#SELL}.
     */
    private Side side;

    /** Сырой статус биржи (OKX state) — диагностический факт, FSM напрямую не использует. */
    private String externalStatus;

    /**
     * <b>Наблюдённая живость на площадке</b> — что показала ПОСЛЕДНЯЯ
     * добыча: живой статус — истина; терминальный статус либо полный цикл
     * без записи — ложь; статус не разобран либо наблюдения не было —
     * пусто, и пустота значаща («не наблюдалась»).
     *
     * <p>Читается только у ноги в {@code ERROR}: у прочих живость несёт сам
     * статус, а пометка ошибки — наше safety-состояние, а не факт площадки
     * (docs/lifecycles/Order.md §«Нога в {@code ERROR}: живость на площадке
     * читается наблюдением»). Писатель — добыча заявки
     * (docs/components/RefreshOrderExecutor.md).
     */
    private Boolean externalLive;

    /** Цена (для market-like может быть null). */
    private BigDecimal price;

    /** Размер (для SWAP/FUTURES — контракты). */
    private BigDecimal size;

    /** Накопленный исполненный объём. */
    private BigDecimal accumulatedFillSize;

    /** Средняя цена исполнения. */
    private BigDecimal averagePrice;

    /** Накопленная комиссия. */
    private BigDecimal fee;

    /**
     * Доменное намерение: ордер только уменьшает позицию (не из снапшота биржи).
     * Ось направления риска и предикат отбора ноги — {@link #isEntryLeg()}.
     */
    private Boolean positionReducingOnly;

    /** internalId предшественника в цепочке REPLACE (nullable; обратная ссылка выводится запросом). */
    private String replacesInternalId;

    /**
     * Эпизод сделки, к которому относится заявка. Пусто, пока эпизода
     * нет либо заявка его не открыла. Write-once — <b>ось отбора</b>
     * живого эпизода в числах риска: без неё ноги закрытых эпизодов
     * неотличимы от ног текущего, и пара «взятое ↔ снятое защитой»
     * считалась бы по всей истории сделки
     * (docs/spec/deal-risk-numbers.json, {@code onLiveEpisode}).
     */
    private Long positionId;

    /**
     * Цена входа, по которой считался риск ЭТОЙ ноги. У рыночного входа
     * — расчётная референс-цена, на биржу не отправляемая. Write-once.
     */
    private BigDecimal plannedEntryPrice;

    /** Заявленный размер этой ноги в контрактах. Write-once. */
    private BigDecimal plannedSizeContracts;

    /** Плановый риск этой ноги — убыток на её стопе при постановке. Write-once. */
    private BigDecimal plannedRiskAmount;

    /** Валюта планового риска ноги. Write-once. */
    private String plannedRiskCurrency;

    /** Размер контракта на момент постановки ноги. Write-once. */
    private BigDecimal plannedContractValue;

    /** Уровень стопа, под который считался риск ноги. Write-once. */
    private BigDecimal plannedStopPrice;

    /**
     * <b>Наблюдаемый запас до ликвидации на момент постановки</b> —
     * измеритель, не операнд: в инвариант «шесть или ни одного» не входит
     * (docs/models/domain/core/Order.md §«Шесть чисел планового риска:
     * инвариант «шесть или ни одного»»). Write-once; <b>пуст, когда цена ликвидации не
     * наблюдалась</b> — у открывающего входа позиции ещё нет, и мерить
     * не от чего.
     */
    private BigDecimal liquidationDistanceRatio;

    /**
     * <b>Наблюдаемая ёмкость стакана на момент постановки</b> —
     * измеритель, не операнд. Write-once; пуст, когда свежих рыночных
     * данных в контексте не было. Пустота здесь — самостоятельное
     * значение «не измеряли», а не нарушение инварианта.
     */
    private BigDecimal bookDepthAtPlacement;

    /** Embedded attached protection, созданная вместе с parent order. */
    private List<AttachedAlgoOrder> attachedAlgoOrders;

    private static final Set<Status> LIVE_STATUSES =
            EnumSet.of(Status.CREATED, Status.PENDING, Status.ACTIVE, Status.PARTIALLY_COMPLETED);

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = Map.of(
            Status.CREATED, EnumSet.of(Status.PENDING, Status.CANCELED, Status.ERROR),
            Status.PENDING, EnumSet.of(Status.ACTIVE, Status.PARTIALLY_COMPLETED, Status.COMPLETED,
                    Status.CANCELED, Status.ERROR),
            Status.ACTIVE, EnumSet.of(Status.PARTIALLY_COMPLETED, Status.COMPLETED, Status.CANCELED, Status.ERROR),
            Status.PARTIALLY_COMPLETED, EnumSet.of(Status.COMPLETED, Status.CANCELED, Status.ERROR));

    /** Live: ещё существует на бирже / влияет на risk (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED). */
    @JsonIgnore
    public Boolean isLive() {
        return LIVE_STATUSES.contains(status);
    }

    /**
     * Живость заявки на площадке НЕ ИСКЛЮЧЕНА (docs/spec/order-lifecycle.json,
     * величина {@code orderMayBeLive}). Множество шире {@link #isLive()}
     * ровно на ногу в {@code ERROR}: пометка ошибки — наше safety-состояние,
     * и нога под ней может стоять на площадке живой.
     *
     * <p><b>Нежилой такую ногу делает только наблюдение</b>
     * ({@link #externalLive} ложь); пустое наблюдение нежилостью не
     * читается — иначе снятие риска и гейт терминала объявляли бы снятым то,
     * чего никто не видел. У прочих статусов наблюдение не читается вовсе.
     *
     * <p>Читатели — аварийные: снятие риска и доказанное отсутствие живого
     * риска сделки. Штатные исполнители читают {@link #isLive()}.
     */
    public Boolean mayBeLive() {
        return isTrue(isLive()) || (Status.ERROR.equals(status) && isNotFalse(externalLive));
    }

    /**
     * Нога терминальна локально: исполнена, отменена либо в ошибочном
     * состоянии (docs/spec/external-status-resolution.json, операнд
     * {@code localTerminal}). Пустой статус терминальным не читается:
     * терминальность снимает биржевую ступень у исчерпанного цикла добычи,
     * и пустота вела бы в благоприятную сторону.
     *
     * <p>Предикат изъят из сериализации: заявка уезжает телом команды к
     * коннектору, а свойством формы вычисленный ответ не является.
     */
    @JsonIgnore
    public Boolean isLocallyTerminal() {
        return nonNull(status) && isFalse(isLive());
    }

    /**
     * Записывает наблюдённую живость по итогу полного цикла добычи: запись
     * найдена живой — истина; найдена терминальной либо не найдена вовсе —
     * ложь (docs/lifecycles/Order.md §«Нога в {@code ERROR}: живость на
     * площадке читается наблюдением»). Отказ разбора статуса наблюдением
     * не является: его вызывающий записывает пустотой.
     *
     * @param found запись, добытая циклом; пусто — полный цикл её не нашёл
     */
    public void observeOnVenue(Order found) {
        this.externalLive = nonNull(found) && isTrue(found.isLive());
    }

    /**
     * Входная нога — заявка, объявившая, что НЕ только уменьшает позицию.
     *
     * <p><b>Ось направления риска — намерение, а не тип.</b> Тип заявки
     * различает только наличие встроенной защиты, и закрывающая нога выхода
     * несёт тип простой заявки (docs/models/domain/core/Order.md §Енумы).
     * Пустое намерение не читается ни входом, ни уменьшением: оба писателя
     * ноги его объявляют, и пустота есть несогласованное состояние, а не
     * третий вид ноги.
     */
    @JsonIgnore
    public Boolean isEntryLeg() {
        return isFalse(positionReducingOnly);
    }

    /** Нога, объявившая, что только уменьшает позицию; пустое намерение — не она (см. {@link #isEntryLeg()}). */
    @JsonIgnore
    public Boolean isReducingLeg() {
        return isTrue(positionReducingOnly);
    }

    /** Нога налита целиком: завершена с причиной налива. */
    @JsonIgnore
    public Boolean isFilled() {
        return Status.COMPLETED.equals(status) && CloseReason.FILLED.equals(closeReason);
    }

    /**
     * Налив ноги окончателен и непуст: она налита целиком либо снята после
     * частичного налива (docs/spec/deal-condition.json, величина
     * {@code entryOrderFinalized}).
     *
     * <p><b>Финализирует терминал, а не полнота налива.</b> Живая частично
     * налитая нога ещё меняет экспозицию; снятая — нет, и её налив есть
     * окончательный размер входа. Недобытый налив снятой ноги нулём не
     * подменяется и финализацией не читается.
     */
    public Boolean hasFinalFill() {
        return isTrue(isFilled())
                || (Status.CANCELED.equals(status) && nonNull(accumulatedFillSize)
                        && accumulatedFillSize.signum() > 0);
    }

    /** Есть хотя бы одна active-like (PENDING/ACTIVE) attached-защита. */
    public Boolean hasActiveAttachedProtection() {
        return isNotEmpty(attachedAlgoOrders)
                && attachedAlgoOrders.stream().anyMatch(protection -> isTrue(protection.isActiveLike()));
    }

    /**
     * Приём ноги площадкой не подтверждён: отправки не было либо её ответ
     * потерян. Биржевого идентификатора у такой ноги нет, и найти её можно
     * только по клиентскому (docs/lifecycles/Order.md).
     */
    @JsonIgnore
    public Boolean isNotSubmitted() {
        return Status.CREATED.equals(status);
    }

    /**
     * Неотправленная нога, которую полный цикл добычи не нашёл, до площадки
     * не дошла: снята локально, и её встроенная защита — намерение, ушедшее
     * бы вместе с ней, — снимается тем же ходом.
     *
     * <p><b>Причина — стоящее намерение, иначе {@code NOT_PLACED}</b>:
     * причина write-once, и снятие, заказанное до добычи, не перетирается.
     * Контролируемого исключения здесь нет: пропавшей сущностью нога,
     * которой на площадке не было, не является
     * (docs/rules/controlled-exchange-exceptions.md).
     *
     * <p><b>Ребро одно — из созданного</b> (docs/spec/order-lifecycle.json):
     * у отправленной ненайденность есть пропажа, а не несостоявшаяся
     * постановка, и отказ стои́т до снятия защиты.
     */
    public void toNotPlaced() {
        if (isFalse(isNotSubmitted())) {
            throw new IllegalStateException("Only an unsubmitted Order is withdrawn as not placed: " + status);
        }
        toCancel(CloseReason.NOT_PLACED);
        emptyIfNull(attachedAlgoOrders).stream()
                .filter(protection -> isTrue(protection.canTransitionTo(AttachedAlgoOrder.Status.CANCELED)))
                .forEach(protection -> protection.toCancel(AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED));
    }

    /**
     * Ребро из текущего статуса в целевой допустимо матрицей жизненного
     * цикла (docs/spec/order-lifecycle.json, величина
     * {@code orderTransitionAllowed}). Пустое «откуда» допускает только
     * созданный; из терминальных рёбер нет, петель матрица не содержит.
     *
     * <p><b>Охрана стои́т на модели, как у двух соседей по той же спеке</b>
     * — отдельной условной заявки и встроенной защиты: так её видит всякий
     * вызывающий, а запрещённое ребро (отмена завершённой заявки) не
     * затирает терминал молча.
     *
     * <p><b>Охват назван:</b> переводящие методы у модели есть только у
     * терминальных рёбер. Рёбра в отправленный, активный и частично
     * исполненный ставят исполнители отправки и добычи сеттером, и этот
     * предикат их не охраняет, пока они его не спрашивают.
     */
    public Boolean canTransitionTo(Status target) {
        Set<Status> allowed = isNull(status)
                ? EnumSet.of(Status.CREATED)
                : ALLOWED_TRANSITIONS.getOrDefault(status, EnumSet.noneOf(Status.class));
        return allowed.contains(target);
    }

    /** Полностью исполнен: COMPLETED + closeReason FILLED (write-once); ребро вне матрицы — отказ. */
    public void toComplete() {
        transitTo(Status.COMPLETED);
        applyCloseReason(CloseReason.FILLED);
    }

    /** Отменён: требует ненулевой reason; ребро вне матрицы — отказ. */
    public void toCancel(CloseReason reason) {
        requireReason(reason);
        transitTo(Status.CANCELED);
        applyCloseReason(reason);
    }

    /** Ошибочное состояние: требует ненулевой reason; ребро вне матрицы — отказ. */
    public void toError(CloseReason reason) {
        requireReason(reason);
        transitTo(Status.ERROR);
        applyCloseReason(reason);
    }

    /**
     * Перевод по матрице. Отказ стои́т ДО записи статуса и причины: не
     * состоявшееся ребро не оставляет модель наполовину переведённой.
     */
    private void transitTo(Status target) {
        if (isFalse(canTransitionTo(target))) {
            throw new IllegalStateException("Illegal Order transition " + status + " -> " + target);
        }
        this.status = target;
    }

    private void applyCloseReason(CloseReason reason) {
        if (isNull(closeReason)) {
            this.closeReason = reason;
        }
    }

    private void requireReason(CloseReason reason) {
        if (isNull(reason)) {
            throw new IllegalArgumentException("closeReason is required");
        }
    }

    /**
     * Сторона заявки.
     *
     * <p><b>Перечень доменный, а не источниковый, и это несущее
     * свойство.</b> Класс живёт в общей библиотеке, чей объявленный
     * критерий устойчивости — «вторая площадка проходит добавлением, а
     * не правкой существующих полей» (docs/architecture/services.md).
     * Поле, хранящее словарь одной площадки, нарушало бы его по
     * построению: у второй площадки словарь свой, и совпадение написания
     * ничем не гарантировано. Перевод в словарь площадки делает коннектор
     * на своей границе (docs/models/mapping/Order.md).
     *
     * <p>Перечень закрыт: третьей стороны у заявки не бывает. Уменьшение
     * позиции стороной не выражается — для него есть отдельное поле
     * {@code positionReducingOnly}.
     */
    public enum Side {

        /** Покупка: заявка увеличивает длинную экспозицию либо уменьшает короткую. */
        BUY,

        /** Продажа: заявка увеличивает короткую экспозицию либо уменьшает длинную. */
        SELL
    }

    /**
     * Бизнес-тип заявки — ось встроенной защиты, а не направления риска:
     * закрывающая нога несёт {@link #ENTRY}, её отличает намерение
     * {@code positionReducingOnly} (docs/models/domain/core/Order.md §Енумы).
     */
    public enum Type {

        /** Простая заявка без встроенной защиты: вход либо закрывающая нога. */
        ENTRY,

        /** Вход со встроенной защитой (attached stop-loss). */
        ENTRY_ATTACHED_STOP_LOSS
    }

    /** Доменный статус ordinary order. Значения и переходы — docs/lifecycles/Order.md. */
    public enum Status {

        /** Локальная сущность создана, приём площадкой не подтверждён: отправки не было либо ответ потерян. */
        CREATED,

        /** Отправлен на биржу, факт постановки не подтверждён. */
        PENDING,

        /** Активен на бирже (live). */
        ACTIVE,

        /** Частично исполнен. */
        PARTIALLY_COMPLETED,

        /** Полностью исполнен. */
        COMPLETED,

        /** Отменён. */
        CANCELED,

        /** Ошибка. */
        ERROR
    }

    /** Причина финализации ordinary order / перевода в ERROR. */
    public enum CloseReason {

        /** Полностью исполнен. */
        FILLED,

        /** Отменён стратегией. */
        CANCELED_BY_STRATEGY,

        /** Стратегия заменила другим ордером (REPLACE-ремодел; симметрично AlgoOrder). */
        REPLACED_BY_STRATEGY,

        /** Аварийный safety-flow / kill-switch. */
        KILL_SWITCH,

        /** Отправленный не найден после refresh/search/history цикла. */
        MISSING_AFTER_REFRESH,

        /**
         * Не дошёл до площадки: приём не подтверждён, и полный цикл добычи
         * его не нашёл. Штатный терминал, не ошибка интеграции.
         */
        NOT_PLACED,

        /** Неизвестный внешний статус. */
        UNKNOWN_EXTERNAL_STATUS,

        /** Fallback. */
        UNKNOWN
    }
}
