package com.example.auditstatistics.mapping;

import com.example.auditstatistics.domain.model.ReceptionPairMoments;
import com.example.auditstatistics.persistence.repository.journal.ReceptionPairMomentRow;
import java.util.List;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Переход строки состояния приёма persistence → domain
 * (.claude/rules/codestyle.md §Маппинг).
 *
 * <p><b>Маппер третий у модуля, и сущность у него своя</b> — строка
 * состояния приёма. Api-стороны у неё нет и не будет: наружу состояние
 * приёма уезжает не строкой, а <b>полнотой</b> рядом с выдачей журнальной
 * выборки (docs/models/domain/other/AuditRecord.md §«Как журнал
 * читается») и рядами экспорта. Поэтому здесь один метод, а не цепочка.
 *
 * <p><b>Записи маппер не обслуживает:</b> все ходы записи по строке —
 * нативные обновления по ключу пары, и переносить между слоями там
 * нечего.
 */
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ReceptionStateMapper {

    /** Строка выборки моментов → доменная форма с предикатом основания возраста. */
    ReceptionPairMoments persistenceToDomain(ReceptionPairMomentRow row);

    /** Та же выборка целиком: порядок строк выдачи сохраняется. */
    List<ReceptionPairMoments> persistenceToDomain(List<ReceptionPairMomentRow> rows);
}
