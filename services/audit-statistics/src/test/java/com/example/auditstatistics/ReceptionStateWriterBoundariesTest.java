package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.domain.service.ReceptionStateSyncService;
import com.example.auditstatistics.mapping.ReceptionStateMapper;
import com.example.auditstatistics.persistence.service.ReceptionStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Границы писателя-тика: что такт пишет и чего не пишет
 * (docs/models/domain/other/AuditRecord.md, таблица писателей;
 * docs/components/ReceptionStateJob.md §Границы).
 *
 * <p><b>Почему это отдельная проба.</b> Писателей у строки два, и они
 * делят её колонки. Тик, тронувший величины приёма, затёр бы флаг
 * остановки и момент последнего принятого события — то есть погасил бы
 * ровно те свидетельства, ради которых строка и заведена, причём в
 * разрешающую сторону: непрерывность стала бы утверждаема после
 * остановки.
 *
 * <p><b>Порядок ходов такта проверяется здесь же:</b> строка появившейся
 * темы заводится до того, как признак подписки ставится всем темам, иначе
 * появившаяся тема получила бы признак только со следующего такта.
 */
class ReceptionStateWriterBoundariesTest {

    private static final String GROUP = "audit-statistics.journal";
    private static final String CORE_TOPIC = "trading-core.facts";
    private static final String STRATEGIES_TOPIC = "strategies.facts";

    private final ReceptionStateDataService dataService = mock(ReceptionStateDataService.class);
    private final ReceptionStateSyncService syncService =
            new ReceptionStateSyncService(dataService, mock(ReceptionStateMapper.class));

    @Test
    @DisplayName("Такт заводит строку каждой темы, затем ставит признак подписки и снимает его у ушедших")
    void theTickOpensPairsBeforeApplyingTheSubscription() {
        List<String> subscription = List.of(CORE_TOPIC, STRATEGIES_TOPIC);
        OffsetDateTime moment = OffsetDateTime.now(ZoneOffset.UTC);

        syncService.syncSubscription(GROUP, subscription, moment);

        InOrder order = inOrder(dataService);
        order.verify(dataService).openPair(GROUP, CORE_TOPIC, moment);
        order.verify(dataService).openPair(GROUP, STRATEGIES_TOPIC, moment);
        order.verify(dataService).markSubscribed(GROUP, subscription, moment);
        order.verify(dataService).markUnsubscribed(GROUP, subscription, moment);
    }

    @Test
    @DisplayName("Такт не трогает ни одной величины приёма: у них другой писатель")
    void theTickNeverWritesReceptionValues() {
        syncService.syncSubscription(GROUP, List.of(CORE_TOPIC), OffsetDateTime.now(ZoneOffset.UTC));

        verify(dataService, never()).markAccepted(anyString(), anyString(), any(OffsetDateTime.class));
        verify(dataService, never()).markHalted(anyString(), anyString());
        verify(dataService, never()).markGap(anyString(), anyString(), any(OffsetDateTime.class));
        verify(dataService, never()).restartObservation(anyString(), anyString(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Такт — одна транзакция, и менеджер у неё назван: умолчания у выбора нет")
    void theTickRunsInASingleNamedTransaction() {
        Transactional declared = Arrays.stream(ReceptionStateSyncService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .map(method -> method.getAnnotation(Transactional.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("границы у такта нет вовсе"));

        assertThat(declared.propagation())
                .as("половина применённого такта показала бы состав, которого не было ни до, ни после")
                .isEqualTo(Propagation.REQUIRED);
        assertThat(declared.transactionManager())
                .as("подключений три, и неквалифицированная граница сменила бы поведение на появлении второго")
                .isEqualTo(JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER);
    }
}
