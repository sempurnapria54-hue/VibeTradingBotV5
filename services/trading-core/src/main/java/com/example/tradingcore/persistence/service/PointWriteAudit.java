package com.example.tradingcore.persistence.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * Автор и момент <b>точечной</b> записи — запроса обновления, идущего мимо
 * сущности. Такой запрос слушателей аудита не проходит, и без явной
 * простановки {@code modifiedAt}/{@code modifiedBy} у строки оставались бы
 * моментом и автором прежнего сохранения сущностью: изменение, сделанное
 * запросом, не было бы записано ни в одной колонке
 * (docs/models/domain/other/Auditable.md §«Системные поля и точечная
 * запись»).
 *
 * <p><b>Спрашивает ТЕХ ЖЕ поставщиков, что и слушатель</b>
 * ({@code JpaAuditConfig}): вторая копия правила «принципал либо контур» или
 * второй источник часов разошлись бы с первыми, и у одной строки автор и
 * момент писались бы по двум правилам в зависимости от того, какой ход её
 * тронул.
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
