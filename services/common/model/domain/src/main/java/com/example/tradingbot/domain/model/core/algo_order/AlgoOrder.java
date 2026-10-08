package com.example.tradingbot.domain.model.core.algo_order;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Standalone algo-order, связанный с Deal: standalone SL/TP, OCO,
 * trailing stop, partial exit (reduce-only). Хранит локальный intent,
 * идентификаторы, доменный статус, сырой статус (диагностика), условие
 * срабатывания, рассчитанный размер, факты срабатывания, diagnostic
 * facts (связанные ordinary order ids). Не действие стратегии: связь
 * StrategyAction ↔ AlgoOrder — через DealActionState. См.
 * docs/models/domain/core/AlgoOrder.md, docs/lifecycles/AlgoOrder.md.
 *
 * <p><b>Нульарные {@code is}-предикаты изъяты из сериализации</b>
 * ({@code @JsonIgnore}): заявка едет телом команды к коннектору и ответом
 * его чтения, а вычисленный ответ свойством формы не является — ключом без
 * поля он ушёл бы к читателю на другой стороне провода.
 */
@Getter
@Setter
@NoArgsConstructor
public class AlgoOrder extends Auditable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Сделка. */
    private Long dealId;

    /** Транш, чью экспозицию защищает эта заявка. */
    private Long dealTrancheId;


    /** stable client id (OKX algoClOrdId). */
    private String internalId;

    /** биржевой id (OKX algoId). */
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

    /** Причина финализации / ERROR. */
    private CloseReason closeReason;

    /** Денормализованная проекция condition.type (обязательна, должна совпадать). */
    private ConditionType conditionType;

    /** Условие срабатывания (jsonb; только trigger/trailing). */
    private Condition condition;

    /** Рассчитанный materialized размер (для SWAP/FUTURES — контракты). */
    private BigDecimal size;

    /**
     * Направление (closing long → SELL, short → BUY). У нашей строки —
     * намерение; у прочитанной копии — эхо стороны площадки, переведённое
     * коннектором в словарь домена, и на строку оно не переносится: оно
     * операнд сверки ({@link #matchesEcho}).
     */
    private Direction direction;

    /**
     * Доменное намерение: только уменьшать позицию. У прочитанной копии —
     * эхо признака площадки; на строку не переносится, операнд сверки
     * ({@link #matchesEcho}; docs/integrations/okx/rules/reduce-only-invariant.md).
     */
    private Boolean positionReducingOnly;

    /** internalId предшественника в цепочке REPLACE (nullable; обратная ссылка выводится запросом). */
    private String replacesInternalId;

    /** Сырой статус биржи (OKX state) — диагностика, FSM напрямую не использует. */
    private String externalStatus;

    /**
     * <b>Наблюдённая живость на площадке</b> — что показала ПОСЛЕДНЯЯ
     * добыча: живой статус — истина; терминальный статус, в том числе
     * известное слово отказа, либо полный цикл без записи — ложь; статус не
     * разобран либо наблюдения не было — пусто, и пустота значаща («не
     * наблюдалась»).
     *
     * <p>Читается только у заявки в {@code ERROR}: у прочих живость несёт
     * сам статус, а пометка ошибки — наше safety-состояние, а не факт
     * площадки (docs/lifecycles/AlgoOrder.md §«Заявка в {@code ERROR}:
     * живость на площадке читается наблюдением»). Писатель — добыча заявки
     * (docs/components/RefreshAlgoOrderExecutor.md).
     */
    private Boolean externalLive;

    /** Код ошибки биржи (OKX failCode). */
    private String failCode;

    /** Фактический размер срабатывания (OKX actualSz) — не исходный size. */
    private BigDecimal externalSize;

    /** Фактическая цена срабатывания (OKX actualPx). */
    private BigDecimal externalPrice;

    /** Время срабатывания (OKX triggerTime). */
    private Instant externalTriggerTime;

    /** Связанные ordinary order ids (OKX ordId/ordIdList) — внешний факт, runtime на них не опирается. */
    private List<String> linkedOrderExternalIds;

    private static final Set<Status> LIVE_STATUSES =
            EnumSet.of(Status.CREATED, Status.PENDING, Status.ACTIVE, Status.PARTIALLY_COMPLETED);

    private static final Set<Status> EXCHANGE_LIVE_STATUSES =
            EnumSet.of(Status.PENDING, Status.ACTIVE, Status.PARTIALLY_COMPLETED);

    private static final Set<ConditionType> TAKE_PROFIT_TYPES =
            EnumSet.of(ConditionType.TAKE_PROFIT, ConditionType.PARTIAL_TAKE_PROFIT);

    private static final Set<ConditionType> TRAILING_TYPES =
            EnumSet.of(ConditionType.TRAILING_PERCENTS, ConditionType.TRAILING_VALUE);

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = Map.of(
            Status.CREATED, EnumSet.of(Status.PENDING, Status.CANCELED, Status.ERROR),
            Status.PENDING, EnumSet.of(Status.ACTIVE, Status.COMPLETED, Status.CANCELED, Status.ERROR),
            Status.ACTIVE, EnumSet.of(Status.PARTIALLY_COMPLETED, Status.COMPLETED, Status.CANCELED, Status.ERROR),
            Status.PARTIALLY_COMPLETED, EnumSet.of(Status.COMPLETED, Status.CANCELED, Status.ERROR));

    /** Live: ещё существует / влияет на risk (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED). */
    @JsonIgnore
    public Boolean isLive() {
        return LIVE_STATUSES.contains(status);
    }

    /**
     * Заявка стои́т на бирже. Множество у́же {@link #isLive()} ровно на
     * {@code CREATED}: у локально созданной заявки приём не подтверждён, и она
     * не покрывает ничего, — поэтому покрытие считается по этому
     * предикату, а не по живости (docs/spec/protection-coverage.json,
     * величина {@code isLive} носителя STANDALONE).
     */
    @JsonIgnore
    public Boolean isExchangeLive() {
        return EXCHANGE_LIVE_STATUSES.contains(status);
    }

    /**
     * Живость заявки на площадке НЕ ИСКЛЮЧЕНА (docs/lifecycles/AlgoOrder.md
     * §«Заявка в {@code ERROR}: живость на площадке читается наблюдением»;
     * исполнимая форма — docs/spec/algo-order-lifecycle.json, величина
     * {@code algoMayBeLive}; агрегат у гейта терминала сделки —
     * docs/spec/deal-lifecycle.json, величина
     * {@code trancheHasMayBeLiveStandaloneProtection}). Множество
     * шире {@link #isLive()} ровно на заявку в {@code ERROR}: пометка ошибки
     * — наше safety-состояние, и осиротевшая живая запись под ней сработала
     * бы по чужой позиции того же инструмента.
     *
     * <p><b>Нежилой такую заявку делает только наблюдение</b>
     * ({@link #externalLive} ложь); пустое наблюдение нежилостью не
     * читается. У прочих статусов наблюдение не читается вовсе.
     *
     * <p>Читатели — аварийные: снятие риска и доказанное отсутствие живого
     * риска сделки. Штатные исполнители и покрытие читают {@link #isLive()}
     * и {@link #isExchangeLive()}: у заявки в {@code ERROR} штатной работы
     * нет, и покрытием она не считается.
     */
    @JsonIgnore
    public Boolean mayBeLive() {
        return isTrue(isLive()) || (isTrue(isError()) && isNotFalse(externalLive));
    }

    /**
     * Заявка в ошибочном состоянии — нашем safety-состоянии, живости на
     * площадке не исключающем: её живость несёт наблюдение
     * {@code externalLive}, а не статус (docs/lifecycles/AlgoOrder.md).
     */
    @JsonIgnore
    public Boolean isError() {
        return Status.ERROR.equals(status);
    }

    /**
     * Записывает наблюдённую живость по итогу полного цикла добычи: запись
     * найдена живой — истина; найдена терминальной либо не найдена вовсе —
     * ложь (docs/lifecycles/AlgoOrder.md §«Заявка в {@code ERROR}: живость
     * на площадке читается наблюдением»). Отказ разбора статуса пишет
     * {@link #observeRefusedStatus}.
     *
     * @param found запись, добытая циклом; пусто — полный цикл её не нашёл
     */
    public void observeOnVenue(AlgoOrder found) {
        this.externalLive = nonNull(found) && isTrue(found.isLive());
    }

    /**
     * Записывает наблюдённую живость по отказу разбора статуса.
     *
     * <p><b>Известное слово отказа — наблюдение нежилости</b>, хотя
     * разбор его и отказывает: отказ постановки и частичный отказ — наш
     * выбор уводить заявку в проблемный терминал, а не незнание слова, и оба
     * состояния у площадки терминальны. Пустым наблюдение остаётся только у
     * слова, которого словарь площадки не знает, — и у отказа без названной
     * причины: пустота нежилостью не читается.
     *
     * @param reason причина отказа разбора, приехавшая с границы
     */
    public void observeRefusedStatus(ExternalStatusReason reason) {
        this.externalLive = isNull(reason) || ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS.equals(reason)
                ? null
                : Boolean.FALSE;
    }

    /**
     * Заявка терминальна локально: сработала, отменена либо в ошибочном
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
     * Несёт ДЕЙСТВУЮЩИЙ уровень остановки убытка
     * (docs/spec/protection-coverage.json, величина
     * {@code carriesActiveStopLevel}). Тейк не несёт никогда; трейлинг —
     * только после того, как уровень наблюдён: до первого наблюдения
     * worst-case выхода у него нет, и защитой по размеру он быть не
     * может.
     */
    public Boolean carriesActiveStopLevel() {
        if (TAKE_PROFIT_TYPES.contains(conditionType)) {
            return false;
        }
        if (TRAILING_TYPES.contains(conditionType)) {
            return nonNull(condition) && nonNull(condition.getTrailing())
                    && nonNull(condition.getTrailing().getExternalPrice());
        }
        return true;
    }

    /**
     * ДЕЙСТВУЮЩИЙ уровень остановки убытка этой защиты; {@code null} —
     * уровня она не несёт (тейк, ненаблюдённый трейлинг, пустая
     * триггерная цена).
     *
     * <p>У трейлинга уровень — НАБЛЮДЁННЫЙ биржей, у стопа — объявленная
     * триггерная цена. Разведение несущее: у трейлинга объявленного
     * уровня не бывает вовсе, и подстановка объявленного стопа дала бы
     * ему чужое число.
     */
    public BigDecimal stopLevel() {
        if (isFalse(carriesActiveStopLevel())) {
            return null;
        }
        if (TRAILING_TYPES.contains(conditionType)) {
            return condition.getTrailing().getExternalPrice();
        }
        if (isNull(condition) || isNull(condition.getTrigger()) || isNull(condition.getTrigger().getStopLoss())) {
            return null;
        }
        TriggerPrice stopLoss = condition.getTrigger().getStopLoss();
        return nonNull(stopLoss.getExternalValue()) ? stopLoss.getExternalValue() : stopLoss.getValue();
    }

    /**
     * Эхо площадки совпадает с нашей строкой по трём осям сверки
     * (docs/models/mapping/AlgoOrder.md §«Сверка эха»): признак «только
     * уменьшать», сторона и ценовая база КАЖДОЙ триггерной ноги — стопа и
     * тейка. У каждой оси есть правило, опирающееся на исполненность нашего
     * намерения до срабатывания; прочие поля записи не сверяются, и это
     * решение, а не пропуск.
     *
     * <p><b>Пустое эхо либо пустая декларация оси сверку не запускают:</b>
     * молчание источника — недобытый факт, а реакция на расхождение —
     * аварийный контур всего счёта. Отсюда и пустая копия совпадением
     * читается.
     *
     * <p>У трейлинга оси базы нет: у его постановки поля базы нет вовсе, и
     * сверять эхо не с чем (docs/models/domain/core/AlgoOrder.md).
     *
     * @param echo копия этой заявки, прочитанная у площадки
     */
    public Boolean matchesEcho(AlgoOrder echo) {
        if (isNull(echo)) {
            return true;
        }
        return agrees(positionReducingOnly, echo.getPositionReducingOnly())
                && agrees(direction, echo.getDirection())
                && agrees(declaredBase(stopLossLeg()), echoedBase(echo.stopLossLeg()))
                && agrees(declaredBase(takeProfitLeg()), echoedBase(echo.takeProfitLeg()));
    }

    /**
     * Сколько эта защита реально закрывает: остаток размера после
     * срабатывания (docs/spec/protection-coverage.json, величина
     * {@code coveredSize} носителя STANDALONE).
     */
    public BigDecimal coveredSize() {
        BigDecimal declared = isNull(size) ? BigDecimal.ZERO : size;
        return declared.subtract(isNull(externalSize) ? BigDecimal.ZERO : externalSize);
    }

    /** Отправлен на биржу; факт не подтверждён. */
    public void toPending() {
        transitTo(Status.PENDING);
    }

    /** Подтверждён активным фактом refresh (не по ACK). */
    public void toActive() {
        transitTo(Status.ACTIVE);
    }

    /**
     * Сработал частично: причины закрытия здесь нет намеренно — заявка
     * ещё жива и закрытой не считается ({@link #isLive()}).
     */
    public void toPartiallyComplete() {
        transitTo(Status.PARTIALLY_COMPLETED);
    }

    /** Сработал полностью: COMPLETED + closeReason TRIGGERED (write-once). */
    public void toComplete() {
        transitTo(Status.COMPLETED);
        applyCloseReason(CloseReason.TRIGGERED);
    }

    /**
     * Приём заявки площадкой не подтверждён: отправки не было либо её ответ
     * потерян. Биржевого идентификатора у такой заявки нет, и найти её можно
     * только по клиентскому (docs/lifecycles/AlgoOrder.md).
     */
    @JsonIgnore
    public Boolean isNotSubmitted() {
        return Status.CREATED.equals(status);
    }

    /**
     * Неотправленная заявка, которую полный цикл добычи не нашёл, до площадки
     * не дошла: снята локально, ребром из созданного. Причина — стоящее
     * намерение, иначе {@code NOT_PLACED} (write-once). Контролируемого
     * исключения нет: пропавшей сущностью заявка, которой на площадке не
     * было, не является (docs/rules/controlled-exchange-exceptions.md).
     *
     * <p><b>Ребро одно — из созданного</b> (docs/spec/algo-order-lifecycle.json,
     * величина {@code algoTransitionAllowed}): у отправленной ненайденность
     * есть пропажа, а не несостоявшаяся постановка, и матрица, допускающая
     * отмену из отправленного, этой тропы не различает — поэтому отказ стои́т
     * здесь, до перевода.
     */
    public void toNotPlaced() {
        if (isFalse(isNotSubmitted())) {
            throw new IllegalStateException("Only an unsubmitted AlgoOrder is withdrawn as not placed: " + status);
        }
        toCancel(CloseReason.NOT_PLACED);
    }

    /**
     * Ребро из текущего статуса в целевой допустимо матрицей жизненного
     * цикла (docs/spec/algo-order-lifecycle.json, величина
     * {@code algoTransitionAllowed}). Пустое «откуда» допускает только
     * созданный; из терминальных рёбер нет, петель матрица не содержит.
     *
     * <p>Предикат спрашивает исполнитель добычи: наблюдённый статус,
     * ребра в который из текущего матрица не содержит (откат живого статуса,
     * терминал), состояния не двигает, а не роняет проход броском.
     */
    public Boolean canTransitionTo(Status target) {
        Set<Status> allowed = isNull(status)
                ? EnumSet.of(Status.CREATED)
                : ALLOWED_TRANSITIONS.getOrDefault(status, EnumSet.noneOf(Status.class));
        return allowed.contains(target);
    }

    /** Отменён: требует ненулевой reason. */
    public void toCancel(CloseReason reason) {
        requireReason(reason);
        transitTo(Status.CANCELED);
        applyCloseReason(reason);
    }

    /** Ошибочное состояние: требует ненулевой reason. */
    public void toError(CloseReason reason) {
        requireReason(reason);
        transitTo(Status.ERROR);
        applyCloseReason(reason);
    }

    /** Денормализованный conditionType должен совпадать с condition.type (оба не null). */
    public void validateConditionProjection() {
        if (isNull(conditionType) || isNull(condition) || isNull(condition.getType())
                || isFalse(Objects.equals(conditionType, condition.getType()))) {
            throw new IllegalStateException("AlgoOrder conditionType must match condition.type");
        }
    }

    /**
     * Перевод по матрице. Отказ стои́т ДО записи статуса и причины: не
     * состоявшееся ребро не оставляет модель наполовину переведённой.
     */
    private void transitTo(Status target) {
        if (isFalse(canTransitionTo(target))) {
            throw new IllegalStateException("Illegal AlgoOrder transition " + status + " -> " + target);
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

    /** Нога стопа триггерной ветки; пусто — ветки либо ноги нет. */
    private TriggerPrice stopLossLeg() {
        return isNull(condition) || isNull(condition.getTrigger()) ? null : condition.getTrigger().getStopLoss();
    }

    /** Нога тейка триггерной ветки; пусто — ветки либо ноги нет. */
    private TriggerPrice takeProfitLeg() {
        return isNull(condition) || isNull(condition.getTrigger()) ? null : condition.getTrigger().getTakeProfit();
    }

    private static TriggerPriceType declaredBase(TriggerPrice leg) {
        return isNull(leg) ? null : leg.getType();
    }

    private static TriggerPriceType echoedBase(TriggerPrice leg) {
        return isNull(leg) ? null : leg.getExternalType();
    }

    /** Ось сверки расходится, только когда непусты обе стороны и они различны. */
    private static boolean agrees(Object declared, Object echoed) {
        return isNull(declared) || isNull(echoed) || Objects.equals(declared, echoed);
    }

    /** Направление algo-order. */
    public enum Direction {

        /** Покупка. */
        BUY,

        /** Продажа. */
        SELL
    }

    /** Доменный статус algo-order. Значения и переходы — docs/lifecycles/AlgoOrder.md. */
    public enum Status {

        /** Локальная сущность создана, приём площадкой не подтверждён: отправки не было либо ответ потерян. */
        CREATED,

        /** Отправлен на биржу, факт не подтверждён. */
        PENDING,

        /** Активен на бирже. */
        ACTIVE,

        /** Частично сработал (exchange-driven recovery-status, не целевой сценарий). */
        PARTIALLY_COMPLETED,

        /** Сработал полностью. */
        COMPLETED,

        /** Отменён. */
        CANCELED,

        /** Ошибка. */
        ERROR
    }

    /** Причина финализации algo-order / ERROR. */
    public enum CloseReason {

        /** Сработал (triggered). */
        TRIGGERED,

        /** Отменён стратегией. */
        CANCELED_BY_STRATEGY,

        /** Стратегия заменила другим algo-order (REPLACE-ремодел). */
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

        /** Постановка ордера на бирже не удалась. */
        ORDER_FAILED,

        /** Частичный отказ. */
        PARTIALLY_FAILED,

        /** Неизвестный внешний статус. */
        UNKNOWN_EXTERNAL_STATUS,

        /** Fallback. */
        UNKNOWN
    }

    /** Тип условия срабатывания algo-order. */
    public enum ConditionType {

        /** Стоп-лосс на полное закрытие. */
        STOP_LOSS,

        /** Тейк-профит на полное закрытие. */
        TAKE_PROFIT,

        /** OCO: стоп-лосс + тейк-профит на полное закрытие. */
        OCO_FULL,

        /** Трейлинг-стоп с callback в процентах. */
        TRAILING_PERCENTS,

        /** Трейлинг-стоп с callback в абсолютном значении. */
        TRAILING_VALUE,

        /** Частичный тейк-профит (reduce-only доля позиции). */
        PARTIAL_TAKE_PROFIT,

        /** Частичный стоп-лосс (reduce-only доля позиции). */
        PARTIAL_STOP_LOSS
    }

    /** Внутренний тип trigger-цены биржи. */
    public enum TriggerPriceType {

        /** Последняя цена сделки (last). */
        LAST,

        /** Индексная цена (index). */
        INDEX,

        /** Марк-цена (mark). */
        MARK
    }
}
