package com.example.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.audit.config.JournalPersistenceConfig;
import com.example.audit.domain.service.JournalCleanupService;
import com.example.audit.persistence.service.AuditRecordDataService;
import com.example.audit.persistence.service.ReceptionStateDataService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Границы писателя-чистки: что проход пишет, чего не пишет и какой
 * транзакцией (docs/models/domain/other/AuditRecord.md, таблица
 * писателей; docs/components/JournalCleanupJob.md §Границы).
 *
 * <p><b>Почему это отдельная проба.</b> Писателей у строки состояния приёма
 * двое, и делят они её колонки: величины приёма пишет слушатель, состав и
 * живость — тик. Чистка в их число не входит вовсе — и момент разрыва она
 * тоже не снимает: разрыв, вынесенный удалением за нижнюю границу,
 * перестаёт давать дыру сравнением при чтении
 * (docs/rules/durable-consumer-reception.md §«Писатели величин — по роли, а
 * не по имени класса»). Проход, получивший доступ к строке состояния,
 * вернул бы третьего писателя, чья обязанность «снять, когда двинул
 * границу» не мерится ничем.
 *
 * <p><b>Транзакционная граница проверяется здесь же:</b> порция удаления —
 * своя транзакция (обрыв между порциями ничего не стои́т, обрыв внутри
 * одной большой откатывал бы уже сделанное).
 */
class JournalCleanupBoundariesTest {

    private static final OffsetDateTime THRESHOLD =
            OffsetDateTime.of(2026, 9, 8, 10, 0, 0, 0, ZoneOffset.UTC);

    private final AuditRecordDataService auditRecordDataService = mock(AuditRecordDataService.class);
    private final JournalCleanupService service = new JournalCleanupService(auditRecordDataService);

    @Test
    @DisplayName("Строк состояния приёма чистка не пишет: пути к их границе у неё нет")
    void theCleanupHasNoPathToTheReceptionState() {
        List<Class<?>> collaborators = Arrays.stream(JournalCleanupService.class.getDeclaredFields())
                .<Class<?>>map(Field::getType)
                .toList();

        assertThat(collaborators)
                .as("момент разрыва читается против границы, и снимать его некому — "
                        + "в том числе чистке, которая границу двигает")
                .doesNotContain(ReceptionStateDataService.class);
    }

    @Test
    @DisplayName("Удаление идёт порциями: размер порции доезжает до запроса, а не теряется в границе")
    void theBatchSizeReachesTheQuery() {
        service.deleteBatchRecordedBefore(THRESHOLD, 100);

        verify(auditRecordDataService).deleteBatchRecordedBefore(THRESHOLD, 100);
    }

    @Test
    @DisplayName("Порция удаления идёт своей транзакцией с НАЗВАННЫМ менеджером")
    void theDeletionRunsInANamedTransaction() {
        assertThat(transactionalMethods())
                .as("подключений три, и неквалифицированная граница сменила бы поведение на появлении второго")
                .hasSize(1)
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class).transactionManager())
                        .isEqualTo(JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER));
    }

    private List<Method> transactionalMethods() {
        return Arrays.stream(JournalCleanupService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .toList();
    }
}
