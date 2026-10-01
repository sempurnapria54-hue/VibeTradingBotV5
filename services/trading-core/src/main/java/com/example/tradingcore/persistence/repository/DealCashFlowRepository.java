package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.DealCashFlowEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по строке разбивки движений средств. */
public interface DealCashFlowRepository extends JpaRepository<DealCashFlowEntity, Long> {

    /**
     * Идентификаторы записей счёта из НАЗВАННЫХ, уже приземлённые, —
     * отсечка объёма прохода, а не механизм дедупа: дедуп держит ключ
     * вставки ниже. Проекция колонки, а не строки
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
     * одного поля»); мощность ответа
     * ограничена перечнем, который проход уже держит в памяти.
     */
    @Query("""
            select f.externalBillId from DealCashFlowEntity f
            where f.exchangeAccountId = :exchangeAccountId and f.externalBillId in :externalBillIds""")
    List<String> findLandedBillIds(@Param("exchangeAccountId") Long exchangeAccountId,
                                   @Param("externalBillIds") Collection<String> externalBillIds);

    /**
     * Безопасная вставка строки по ключу идемпотентности «счёт,
     * идентификатор записи» ({@code uk_deal_cash_flow_account_bill});
     * возвращает число вставленных строк — ноль означает, что запись уже
     * приземлена другим ходом (docs/rules/idempotency-via-unique.md).
     *
     * <p><b>Почему нативным запросом, а не «проверить и сохранить».</b>
     * Проверка существования и сохранение не атомарны: два писателя одной
     * записи счёта — проходы двух сделок одного счёта, читающие движения
     * аккаунт-широко, — оба прошли бы проверку, и второй падал бы нарушением
     * ключа, роняя проход вместе с добытыми строками.
     *
     * <p><b>Аудит проставляется здесь же:</b> нативная вставка слушателей
     * JPA не проходит, поэтому автор и момент приходят от границы
     * (docs/models/domain/other/Auditable.md §«Системные поля и точечная
     * запись»).
     *
     * <p><b>У каждого необязательного операнда — приведение к типу
     * колонки:</b> пустой параметр без него база отвергает, не определив
     * его тип.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into deal_cash_flows
                (exchange_account_id, deal_id, category, amount, ccy, external_fee, position_balance_change,
                 applied_rate, rate_status, applied_rate_candle_instrument, applied_rate_candle_timeframe,
                 applied_rate_candle_open_time, external_instrument_id, external_bill_id, external_type,
                 external_sub_type, external_order_id, created_at, created_by, modified_at, modified_by,
                 external_created_at, external_modified_at)
            values
                (:exchangeAccountId, cast(:dealId as bigint), :category, :amount, :ccy,
                 cast(:externalFee as numeric), cast(:positionBalanceChange as numeric),
                 cast(:appliedRate as numeric), :rateStatus, cast(:appliedRateCandleInstrument as varchar),
                 cast(:appliedRateCandleTimeframe as varchar), cast(:appliedRateCandleOpenTime as timestamptz),
                 cast(:externalInstrumentId as varchar), :externalBillId, :externalType,
                 cast(:externalSubType as varchar), cast(:externalOrderId as varchar),
                 :writtenAt, :writer, :writtenAt, :writer,
                 cast(:externalCreatedAt as timestamptz), cast(:externalModifiedAt as timestamptz))
            on conflict on constraint uk_deal_cash_flow_account_bill do nothing
            """)
    int insertIfAbsent(@Param("exchangeAccountId") Long exchangeAccountId,
                       @Param("dealId") Long dealId,
                       @Param("category") String category,
                       @Param("amount") BigDecimal amount,
                       @Param("ccy") String ccy,
                       @Param("externalFee") BigDecimal externalFee,
                       @Param("positionBalanceChange") BigDecimal positionBalanceChange,
                       @Param("appliedRate") BigDecimal appliedRate,
                       @Param("rateStatus") String rateStatus,
                       @Param("appliedRateCandleInstrument") String appliedRateCandleInstrument,
                       @Param("appliedRateCandleTimeframe") String appliedRateCandleTimeframe,
                       @Param("appliedRateCandleOpenTime") OffsetDateTime appliedRateCandleOpenTime,
                       @Param("externalInstrumentId") String externalInstrumentId,
                       @Param("externalBillId") String externalBillId,
                       @Param("externalType") String externalType,
                       @Param("externalSubType") String externalSubType,
                       @Param("externalOrderId") String externalOrderId,
                       @Param("writtenAt") OffsetDateTime writtenAt,
                       @Param("writer") String writer,
                       @Param("externalCreatedAt") OffsetDateTime externalCreatedAt,
                       @Param("externalModifiedAt") OffsetDateTime externalModifiedAt);

    /** Строки сделки — вход сверки разбивки и догона курса. */
    List<DealCashFlowEntity> findByDealId(Long dealId);

    /**
     * Строки сделки окном по убыванию времени события — вход сборки
     * контекста прохода.
     *
     * <p>Окно обязательно, и это единственная коллекция контекста, чья
     * мощность не задана конструкцией сделки: у долгой сделки с частым
     * начислением финансирования она растёт со временем жизни
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
     * одного поля»).
     */
    List<DealCashFlowEntity> findByDealIdOrderByExternalCreatedAtDesc(Long dealId, Pageable pageable);

    /** Строки сделки, севшие в принимающую корзину: вход перерезолва по текущему отображению. */
    List<DealCashFlowEntity> findByDealIdAndCategory(Long dealId, String category);

    /**
     * Принимающая корзина счёта непуста. Операнд дедупа журнального отчёта:
     * он про СОСТОЯНИЕ корзины, а не про строку, и без этого запроса
     * «состояние держится» было бы неотличимо от «возникло заново».
     */
    Boolean existsByExchangeAccountIdAndCategory(Long exchangeAccountId, String category);
}
