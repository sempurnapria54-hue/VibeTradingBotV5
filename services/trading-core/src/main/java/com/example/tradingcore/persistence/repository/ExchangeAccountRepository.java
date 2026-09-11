package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.ExchangeAccountEntity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по строке проекции биржевого счёта. */
public interface ExchangeAccountRepository extends JpaRepository<ExchangeAccountEntity, Long> {

    Optional<ExchangeAccountEntity> findByInternalId(String internalId);

    /**
     * Числовой ключ строки проекции по идентичности — проекция одного
     * поля (.claude/rules/codestyle.md §«Выборка данных: не тянем
     * сущность ради одного поля»).
     */
    @Query("select a.id from ExchangeAccountEntity a where a.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);

    /**
     * Тенант-владелец счёта по его идентичности — проекция одного поля:
     * проверке ссылок определения нужен ответ «есть ли и чей», а не
     * строка целиком.
     */
    @Query("select a.tenantInternalId from ExchangeAccountEntity a where a.internalId = :internalId")
    Optional<String> findTenantInternalIdByInternalId(@Param("internalId") String internalId);

    /**
     * Идентичность счёта по его числовому ключу — проекция одного поля.
     *
     * <p>Читатель — писатель события сделки: наружу едет
     * {@code internalId}, а на руках у него только числовая ссылка строки
     * сделки (.claude/rules/codestyle.md §«Выборка данных: не тянем
     * сущность ради одного поля»).
     */
    @Query("select a.internalId from ExchangeAccountEntity a where a.id = :id")
    Optional<String> findInternalIdById(@Param("id") Long id);

    /**
     * Тенант-владелец счёта по числовому ключу — проекция одного поля.
     * Читатель тот же: тенант едет конвертом события и служит ключом
     * партиции.
     */
    @Query("select a.tenantInternalId from ExchangeAccountEntity a where a.id = :id")
    Optional<String> findTenantInternalIdById(@Param("id") Long id);

    /**
     * Идентичности тенантов, у которых есть счёт.
     *
     * <p>Проекция поля, а не выборка строк: тик заводит место под числа
     * риск-аппетита по тенантам счетов, и сами счета ему для этого не
     * нужны (.claude/rules/codestyle.md §«Выборка данных»).
     *
     * <p>Запрос объявлен, а не выведен из имени: имя метода выбирает
     * строки, а не колонку, — производная форма вернула бы сущности.
     */
    @Query("select distinct a.tenantInternalId from ExchangeAccountEntity a")
    List<String> findDistinctTenantInternalIds();

    /**
     * <b>Write-once первого наблюдения базы риска.</b> Охрана стои́т в
     * самом запросе: строка счёта существует раньше значения, поэтому
     * запретом на запись её не выразить, а охрана, оставленная
     * вызывающему, держится ровно до второго вызывающего
     * (docs/rules/risk-policy.md).
     *
     * <p>База и её валюта пишутся ОДНИМ ходом: порознь они дали бы
     * состояние «база есть, валюты нет», в котором число ни с чем не
     * сравнимо.
     */
    @Modifying
    @Query("""
            update ExchangeAccountEntity a set a.riskBase = :riskBase, a.riskBaseCurrency = :currency
            where a.id = :id and a.riskBase is null""")
    int applyRiskBase(@Param("id") Long id,
                      @Param("riskBase") BigDecimal riskBase,
                      @Param("currency") String currency);

    /**
     * Счета, доступные торговле, — по реестровому статусу проекции.
     * Ступень счёта отбор не сужает: она о том, можно ли НАБИРАТЬ риск, а
     * ставка нужна и свёрнутому счёту — сопровождение живых сделок
     * продолжается (docs/rules/exchange-hold.md).
     */
    List<ExchangeAccountEntity> findByStatus(String status);

    /**
     * Счета, по которым разрешено ЗАВОДИТЬ новый риск, — популяция отбора
     * входа. Здесь ступень отбор сужает, в отличие от соседней выборки:
     * обе ступени лестницы гасят новые входы, и мягкая делает это ровно
     * выпадением счёта из этой выборки
     * (docs/rules/exchange-hold.md §«Ступень 1 — мягкий холд»).
     */
    List<ExchangeAccountEntity> findByStatusAndSafetyRung(String status, String safetyRung);

    /**
     * Серия убытков растёт на единицу — атомарным инкрементом, а не
     * чтением с записью: строка счёта общая для всех его сделок, и
     * потерянный инкремент отодвигал бы остановку молча
     * (docs/rules/loss-streak-halt.md).
     */
    @Modifying
    @Query("""
            update ExchangeAccountEntity a set a.consecutiveLossCount = a.consecutiveLossCount + 1
            where a.id = :id""")
    int incrementConsecutiveLossCount(@Param("id") Long id);

    /** Серия обнуляется ценово-прибыльной сделкой; иных операндов обнуления нет. */
    @Modifying
    @Query("""
            update ExchangeAccountEntity a set a.consecutiveLossCount = 0
            where a.id = :id""")
    int resetConsecutiveLossCount(@Param("id") Long id);

    /**
     * Счёт наблюдений safety-сети: ненаблюдённый проход увеличивает счёт
     * слепоты на единицу (docs/components/AnomalyJob.md §«Гейт полноты
     * среза»).
     *
     * <p>Атомарным инкрементом, а не чтением с записью: строка счёта общая
     * для всех его проходов, и потерянный инкремент отодвигал бы предел
     * слепоты молча.
     */
    @Modifying
    @Query("""
            update ExchangeAccountEntity a set a.blindPassCount = a.blindPassCount + 1
            where a.id = :id""")
    int incrementBlindPassCount(@Param("id") Long id);

    /** Наблюдённый проход сбрасывает счёт слепоты в ноль. */
    @Modifying
    @Query("""
            update ExchangeAccountEntity a set a.blindPassCount = 0
            where a.id = :id""")
    int resetBlindPassCount(@Param("id") Long id);

    /** Текущий счёт слепоты ПРОЕКЦИЕЙ поля — операнд предела, не строка счёта. */
    @Query("select a.blindPassCount from ExchangeAccountEntity a where a.id = :id")
    Optional<Integer> findBlindPassCount(@Param("id") Long id);

    /**
     * Подъём ступени счёта, <b>гардированный стоящей ступенью</b>: строка
     * переставляется, только если счёт стои́т в одной из перечисленных
     * (более низких) ступеней. Число применённых строк — анкер
     * идемпотентности реакции
     * (docs/components/SafetyHoldCoordinator.md §Последовательность).
     *
     * <p>Гард в запросе по тому же доводу, что у лестницы инструмента:
     * читать стоящую ступень и писать новую двумя ходами не атомарно, и
     * снятие риска запустилось бы дважды.
     */
    @Modifying
    @Query("""
            update ExchangeAccountEntity a set a.safetyRung = :requested
            where a.id = :id and a.safetyRung in :lowerRungs""")
    int raiseRung(@Param("id") Long id,
                  @Param("requested") String requested,
                  @Param("lowerRungs") Collection<String> lowerRungs);

    /**
     * <b>Снятие ступени: переход ВНИЗ, гардированный НАЗВАННОЙ ступенью.</b>
     * Строка переставляется, только если стои́т ровно та ступень, которую
     * вызов снимает; возвращает число применённых строк.
     *
     * <p>Гард именем, а не рангом, и это несущее: он делает повтор
     * ХОЛОСТЫМ вместо шага по лестнице дальше. Иначе первый вызов снял бы
     * сворачивание, второй — мягкую ступень, и двойное нажатие вернуло бы
     * торговлю (docs/rules/manual-halt.md §«Ступень называется явно и у
     * снятия тоже»).
     */
    @Modifying
    @Query("""
            update ExchangeAccountEntity a set a.safetyRung = :target
            where a.id = :id and a.safetyRung = :standing""")
    int clearRung(@Param("id") Long id,
                  @Param("standing") String standing,
                  @Param("target") String target);
}
