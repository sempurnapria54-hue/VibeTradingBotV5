package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.InstrumentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по строке проекции инструмента. */
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByInternalId(String internalId);

    /**
     * Числовой ключ строки проекции по идентичности — <b>проекция одного
     * поля</b>, а не сущность целиком: резолв связи внутри базы ядра
     * ничего сверх ключа не читает
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
     * одного поля»).
     */
    @Query("select i.id from InstrumentEntity i where i.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);

    /**
     * Расчётная валюта инструмента площадки — ПРОЕКЦИЕЙ одного поля.
     * Строку целиком ради валюты не тянем
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Query("select i.externalSettlementCurrency from InstrumentEntity i "
            + "where i.exchangeCode = :exchangeCode and i.externalId = :externalId")
    Optional<String> findSettlementCurrency(@Param("exchangeCode") String exchangeCode,
                                            @Param("externalId") String externalId);

    /**
     * Инструменты площадки, торгуемые по проекции каталога, —
     * популяция отбора входа (docs/components/EntryScannerJob.md §Шаги).
     *
     * <p><b>Нерезолвенная расчётная валюта выводит инструмент из
     * выборки, и это предикат выборки, а не третий радиус гейта.</b>
     * Валюта — авторитет всех чисел риска по инструменту
     * (docs/models/domain/core/Instrument.md), и без неё считать сайзинг
     * не в чем.
     *
     * <p>Окно обязательно: каталог растёт вместе с контуром, и упор в
     * окно засчитывается неполнотой прохода
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Query("""
            select i from InstrumentEntity i
            where i.exchangeCode = :exchangeCode and i.status = :status
              and i.externalSettlementCurrency is not null
            order by i.id asc""")
    List<InstrumentEntity> findTradable(@Param("exchangeCode") String exchangeCode,
                                        @Param("status") String status,
                                        Pageable pageable);

    /**
     * Инструменты площадки ЦЕЛИКОМ, статусом не сужая, — популяция обхода
     * проактивной детекции (docs/components/AnomalyJob.md §«Контур
     * читается целиком, статус его не сужает»).
     *
     * <p><b>Сужение статусом даёт два дефекта сразу:</b> популяция
     * детектора «жёсткая ступень не проэнфорсена» становится
     * НЕДОСТИЖИМОЙ, а детектор «живой риск по инструменту вне контура»
     * ложно срабатывает на нашем собственном инструменте под ступенью —
     * замыкая петлю «мягкая ступень → через тик жёсткая биржевая со
     * сносом по рынку».
     *
     * <p>Окно обязательно, и упор в него засчитывается неполнотой прохода
     * — тем же ходом, что и неполученный срез.
     */
    @Query("""
            select i from InstrumentEntity i
            where i.exchangeCode = :exchangeCode
            order by i.id asc""")
    List<InstrumentEntity> findContour(@Param("exchangeCode") String exchangeCode, Pageable pageable);

    /**
     * Сырые типы инструментов проекции — ПРОЕКЦИЕЙ колонки: перечень
     * нужен синку ставок, а ставка есть атрибут группы, не инструмента,
     * и строки ради типа не тянутся
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Query("select distinct i.externalType from InstrumentEntity i where i.externalType is not null")
    List<String> findDistinctExternalTypes();

    /**
     * Идентичности названных инструментов — ПРОЕКЦИЕЙ пары «ключ,
     * идентичность» и ОДНИМ запросом на пачку.
     *
     * <p>Читатель — поверхность чтения: числовой ключ границу сервиса не
     * пересекает, и ответу нужна ровно идентичность
     * (.claude/rules/codestyle.md §«Идентичность наружу»). Запрос по
     * одному ключу на строку ответа дал бы чтение на каждую строку окна
     * (там же, §«Выборка данных»).
     */
    @Query("select i.id, i.internalId from InstrumentEntity i where i.id in :ids")
    List<Object[]> findInternalIdsByIdIn(@Param("ids") Collection<Long> ids);
}
