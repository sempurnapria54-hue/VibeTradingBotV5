package com.example.auditstatistics.domain.service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.config.JournalReadProperties;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.domain.model.JournalCursor;
import com.example.auditstatistics.domain.model.JournalPage;
import com.example.auditstatistics.domain.model.JournalQuery;
import com.example.auditstatistics.persistence.service.AuditRecordDataService;
import com.example.auditstatistics.persistence.service.OwnerJournalCompletenessSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Журнальная выборка чтения: окно тенанта строками и объявленная полнота
 * рядом с ними
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 *
 * <p><b>Ограничения отбора живут у владельца предмета, а не здесь.</b>
 * Этот класс их <b>исполняет</b>: окно обязательно и ограничено сверху,
 * страница берётся курсором, четыре отбора по радиусам конъюнктивны и
 * необязательны. Состав ограничений не пересказывается
 * (.claude/rules/policy-home.md).
 *
 * <p><b>Отвергается, а не сужается.</b> Запрос без окна и запрос с окном
 * шире предела получают отказ: молчаливое сужение отдало бы неполный ряд
 * под видом запрошенного (docs/concept.md, П1).
 *
 * <p><b>Менеджер транзакций назван, а не подразумевается:</b> подключений
 * у процесса три, и умолчания у выбора нет намеренно
 * ({@link JournalPersistenceConfig}). Границей транзакции при этом
 * согласованность строк и полноты <b>не обещается</b>: под уровнем
 * изоляции по умолчанию каждый оператор видит свой снимок. Обещать её и
 * не нужно — обе величины полноты монотонны, и прочитанные позже строк
 * они обещают <b>не больше</b>, чем строки несут.
 */
@Service
@RequiredArgsConstructor
public class JournalReadService {

    private final JournalReadProperties journalReadProperties;
    private final ReceptionProperties receptionProperties;
    private final AuditRecordDataService auditRecordDataService;
    private final JournalCompletenessService journalCompletenessService;
    private final OwnerJournalCompletenessSource journalCompletenessSource;

    /**
     * Прочитать страницу окна вместе с тем, что журнал объявляет о своей
     * полноте.
     *
     * <p><b>Строки и полнота едут одной выдачей</b>, потому что число,
     * показанное без своей достоверности, читается как «всё в порядке»:
     * обязательство объявлено домом и распространено на всякую выдачу
     * чисел этого сервиса.
     *
     * <p><b>Операнды полноты берутся источником СВОЕГО подключения</b>
     * ({@link OwnerJournalCompletenessSource}): база журнала у этой выборки
     * своя, и чужого подключения ей не требуется вовсе
     * (docs/architecture/data-ownership.md §Раскладка).
     */
    @Transactional(readOnly = true,
            transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER)
    public JournalPage read(JournalQuery query) {
        rejectUnlessAcceptable(query);
        OffsetDateTime moment = OffsetDateTime.now(ZoneOffset.UTC);
        Integer pageSize = journalReadProperties.getPageSize();
        List<AuditRecord> found = auditRecordDataService.findPage(query, pageSize + 1);
        return JournalPage.builder()
                .records(page(found, pageSize))
                .nextCursor(nextCursor(found, pageSize))
                .completeness(journalCompletenessService.completeness(journalCompletenessSource,
                        receptionProperties.getGroupId(),
                        moment.minus(receptionProperties.getStateMaxAge())))
                .build();
    }

    /**
     * Три отвержения вопроса, и все три — здесь
     * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
     *
     * <p>Guard-clause вместо вложенных условий: тело метода остаётся
     * неглубоким (.claude/rules/codestyle.md §«Вложенность и
     * rich-модели»), а предикаты живут на самой доменной форме вопроса.
     */
    private void rejectUnlessAcceptable(JournalQuery query) {
        if (isFalse(query.hasWindow())) {
            throw new ReadQueryRejectedException(
                    "Окно по моменту происшествия обязательно: названы обе границы либо запрос не принимается");
        }
        if (isFalse(query.isWindowOrdered())) {
            throw new ReadQueryRejectedException(
                    "Правая граница окна не может быть раньше левой");
        }
        if (isTrue(query.isWindowWiderThan(journalReadProperties.getMaxWindow()))) {
            throw new ReadQueryRejectedException(
                    "Окно шире допустимого: " + journalReadProperties.getMaxWindow()
                            + ". Оно не сужается молча — читаются страницы более узких окон");
        }
        if (isTrue(query.hasPartialCursor())) {
            throw new ReadQueryRejectedException(
                    "Курсор задаётся парой «момент происшествия, идентичность события»: "
                            + "одна её половина позицию не определяет");
        }
    }

    /**
     * Страница — прочитанное без лишней строки, которой узнавалось, есть
     * ли продолжение.
     */
    private List<AuditRecord> page(List<AuditRecord> found, Integer pageSize) {
        if (found.size() > pageSize) {
            return found.subList(0, pageSize);
        }
        return found;
    }

    /**
     * Позиция продолжения — пара последней ОТДАВАЕМОЙ строки; пусто, если
     * окно дочитано.
     *
     * <p><b>Продолжение узнаётся лишней прочитанной строкой, а не
     * равенством размера странице.</b> Полная страница сама по себе не
     * означает, что за ней что-то есть: окно, чей остаток равен размеру
     * страницы ровно, обещало бы читателю ещё один — пустой — запрос.
     */
    private JournalCursor nextCursor(List<AuditRecord> found, Integer pageSize) {
        if (isEmpty(found) || found.size() <= pageSize) {
            return null;
        }
        AuditRecord last = found.get(pageSize - 1);
        return new JournalCursor(last.getOccurredAt(), last.getEventId());
    }
}
