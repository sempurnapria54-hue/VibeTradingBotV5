package com.example.marketdata.persistence.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * Автор и момент <b>точечной</b> записи — нативной вставки, идущей мимо
 * сущности. Такой запрос слушателей аудита не проходит, и без явной
 * простановки у строки не было бы ни автора, ни момента
 * (docs/models/domain/other/Auditable.md §«Системные поля и точечная
 * запись»).
 *
 * <p><b>Спрашивает ТЕХ ЖЕ поставщиков, что и слушатель</b>
 * ({@code JpaAuditConfig}): вторая копия правила «принципал либо контур» или
 * второй источник часов разошлись бы с первыми, и строка одного ряда
 * получала бы автора по двум правилам в зависимости от того, какой тропой
 * её записали.
 *
 * <p><b>Та же форма живёт у {@code trading-core}</b>, и это копия, а не
 * второй дом: тело ссылается только на бины каркаса, поэтому место ей — в
 * общем артефакте; переезд за пределами этой правки.
 */
@Component
@RequiredArgsConstructor
public class PointWriteAudit {

    private final AuditorAware<String> auditorAware;
    private final DateTimeProvider auditingDateTimeProvider;

    /** Автор записи — тот же, что поставил бы слушатель аудита. */
    public String writer() {
        return auditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException("Auditor is not resolved for a point write"));
    }

    /** Момент записи — по тем же часам, что у слушателя аудита. */
    public OffsetDateTime moment() {
        return auditingDateTimeProvider.getNow()
                .map(OffsetDateTime::from)
                .orElseThrow(() -> new IllegalStateException("Audit clock returned no moment for a point write"));
    }
}
