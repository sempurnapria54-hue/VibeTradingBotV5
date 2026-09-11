package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.domain.service.AuditReceptionService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Границы транзакций приёма — таблица дома, проверенная механически
 * (docs/components/AuditEventListener.md §«Транзакционные границы»).
 *
 * <p><b>Почему это стои́т проверять.</b> Флаг остановки, положенный той же
 * транзакцией, что и обработка, откатился бы вместе с ней — то есть не
 * появился бы ровно в том случае, ради которого заведён
 * (docs/models/domain/other/AuditRecord.md §«Отдельная транзакция у флага
 * остановки — не деталь реализации, а условие существования флага»).
 * Снаружи такой дефект неотличим от рабочего кода: тесты приёма зелены,
 * контекст поднимается, а свидетельство об остановке исчезает молча.
 *
 * <p><b>Что тест НЕ мерит.</b> Он не поднимает базу и не наблюдает
 * фиксацию: предмет — объявленные границы, а их исполнение — предмет
 * менеджера транзакций, у которого своя проверка.
 */
class ReceptionTransactionBoundariesTest {

    /** Свидетельства о ходе приёма: каждое ложится СВОЕЙ транзакцией. */
    private static final List<String> OWN_TRANSACTION = List.of("noteHalt", "noteGap", "restartObservation");

    @Test
    @DisplayName("Приём идёт одной транзакцией: следствие принятого сообщения ложится вместе")
    void acceptanceRunsInASingleTransaction() {
        Transactional declared = declaredOn("accept");

        assertThat(declared.propagation())
                .as("строка журнала, момент последнего принятого и снятие флага обязаны откатываться вместе")
                .isEqualTo(Propagation.REQUIRED);
    }

    @ParameterizedTest(name = "{0} ложится отдельной транзакцией")
    @ValueSource(strings = {"noteHalt", "noteGap", "restartObservation"})
    @DisplayName("Свидетельство о ходе приёма ложится отдельной транзакцией")
    void everyWitnessOfTheReceptionRunsInItsOwnTransaction(String method) {
        assertThat(declaredOn(method).propagation())
                .as("свидетельство, положенное транзакцией обработки, ушло бы вместе с её откатом")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("Менеджер транзакций назван у каждого хода: умолчания у выбора нет")
    void everyBoundaryNamesItsTransactionManager() {
        Arrays.stream(AuditReceptionService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .forEach(method -> assertThat(method.getAnnotation(Transactional.class).transactionManager())
                        .as("метод %s обязан называть менеджер: второй приедет с читателями агрегатов",
                                method.getName())
                        .isEqualTo(JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER));
        assertThat(Arrays.stream(AuditReceptionService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .count())
                .as("границ обязано быть четыре: приём и три свидетельства")
                .isEqualTo(OWN_TRANSACTION.size() + 1);
    }

    private Transactional declaredOn(String name) {
        Method method = Arrays.stream(AuditReceptionService.class.getDeclaredMethods())
                .filter(candidate -> Objects.equals(candidate.getName(), name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("метода нет: " + name));
        return method.getAnnotation(Transactional.class);
    }
}
