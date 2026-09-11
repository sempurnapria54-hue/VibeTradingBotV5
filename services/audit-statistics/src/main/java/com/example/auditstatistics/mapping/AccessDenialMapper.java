package com.example.auditstatistics.mapping;

import com.example.auditstatistics.domain.model.AccessDenial;
import com.example.auditstatistics.persistence.model.journal.AccessDenialEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Переход строки отвергнутого вызова domain ↔ persistence
 * (.claude/rules/codestyle.md §Маппинг).
 *
 * <p><b>Маппер четвёртый у модуля, и направление у него обратное
 * соседним:</b> три предыдущих обслуживают чтение, этот — запись. Класс
 * отказа конвертируется по имени автоматически: в домене он перечень, в
 * персистентности — строка.
 *
 * <p><b>Api-стороны у сущности нет</b>, и цепочка слоёв здесь не
 * срезается: наружу строка не отдаётся вовсе — её единственный
 * потребитель разбирает базу руками
 * (docs/models/domain/other/AccessDenial.md).
 */
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AccessDenialMapper {

    /**
     * Доменная строка → сущность.
     *
     * <p><b>Поля аудита переносятся по имени и приезжают пустыми — и это
     * законно, а не упущение.</b> Значения им даёт персистентность в
     * момент вставки (.claude/rules/codestyle.md §«Auditable по слоям»),
     * то есть ПОСЛЕ маппинга; явный {@code ignore} дал бы ровно тот же
     * результат и был бы избыточным {@code @Mapping} (§Маппинг). Писатель
     * их не заполняет намеренно: актора резолвит {@code JpaAuditConfig} по
     * предъявленному принципалу, а не вызывающий код
     * (docs/models/domain/other/Auditable.md §«Носитель дискриминатора —
     * контекст хода, а не поле модели»).
     */
    AccessDenialEntity domainToPersistence(AccessDenial denial);
}
