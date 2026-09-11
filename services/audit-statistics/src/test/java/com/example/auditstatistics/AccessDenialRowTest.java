package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.domain.model.AccessDenial;
import com.example.auditstatistics.domain.service.AccessDenialService;
import com.example.auditstatistics.persistence.service.AccessDenialDataService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Журнальная строка отвергнутого вызова: инвариант согласованности,
 * поведение писателя и граница его записи
 * (docs/models/domain/other/AccessDenial.md).
 *
 * <p>Модель собирается настоящими полями, предикат считается сам —
 * подменять его нечем и незачем (.claude/rules/codestyle.md §«Тесты
 * доменных моделей»). Мокается только коллаборатор — граница
 * persistence, у которой своя проверка.
 *
 * <p><b>Чего тест НЕ мерит.</b> Он не поднимает базу и не наблюдает
 * фиксацию: предмет — объявленная граница и поведение писателя, а её
 * исполнение принадлежит менеджеру транзакций.
 */
@ExtendWith(MockitoExtension.class)
class AccessDenialRowTest {

    private static final String SURFACE = "GET /api/v1/audit-statistics/journal/records";

    @Mock
    private AccessDenialDataService dataService;

    @InjectMocks
    private AccessDenialService service;

    @Test
    @DisplayName("Пустота принципала и PRINCIPAL_ABSENT — одно состояние, выраженное дважды")
    void consistencyHoldsWhenAnAbsentPrincipalHasNoName() {
        assertThat(denial(AccessDenial.Outcome.PRINCIPAL_ABSENT, null).isConsistent()).isTrue();
        assertThat(denial(AccessDenial.Outcome.OPERATION_FORBIDDEN, "holder").isConsistent()).isTrue();
    }

    @Test
    @DisplayName("Расхождение класса отказа и принципала — дефект писателя, а не законное состояние")
    void inconsistentCombinationsAreRejected() {
        // «Принципала нет», но имя записано — заявленное неудостоверённое
        // имя как факт: ровно то, что модель запрещает.
        assertThat(denial(AccessDenial.Outcome.PRINCIPAL_ABSENT, "holder").isConsistent()).isFalse();
        // «Операция не разрешена» без имени: непонятно, кому не разрешена.
        assertThat(denial(AccessDenial.Outcome.OPERATION_FORBIDDEN, null).isConsistent()).isFalse();
        assertThat(denial(AccessDenial.Outcome.OPERATION_FORBIDDEN, "  ").isConsistent()).isFalse();
    }

    @Test
    @DisplayName("Писатель заводит строку с внешней идентичностью и переданными полями")
    void theWriterPersistsARowCarryingItsOwnIdentity() {
        service.record(SURFACE, AccessDenial.Outcome.PRINCIPAL_ABSENT, null);

        AccessDenial saved = captured();
        assertThat(saved.getInternalId())
                .as("внешняя идентичность обязана быть у каждой строки: наружу отдаётся она, а не ключ базы")
                .isNotBlank();
        assertThat(saved.getSurface()).isEqualTo(SURFACE);
        assertThat(saved.getOutcome()).isEqualTo(AccessDenial.Outcome.PRINCIPAL_ABSENT);
        assertThat(saved.getPrincipal()).isNull();
    }

    /**
     * Дедупа у происшествия нет по природе факта: схлопывание попыток
     * занижало бы частоту — единственную величину, ради которой строка и
     * заводится.
     */
    @Test
    @DisplayName("Две попытки — две строки с разной идентичностью, а не одна")
    void twoAttemptsYieldTwoDistinctRows() {
        service.record(SURFACE, AccessDenial.Outcome.PRINCIPAL_ABSENT, null);
        service.record(SURFACE, AccessDenial.Outcome.PRINCIPAL_ABSENT, null);

        ArgumentCaptor<AccessDenial> captor = ArgumentCaptor.forClass(AccessDenial.class);
        verify(dataService, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccessDenial::getInternalId)
                .as("одинаковая идентичность двух попыток означала бы дедуп, которого у происшествия нет")
                .doesNotHaveDuplicates();
    }

    /**
     * Поля аудита писатель не заполняет: актора резолвит персистентность
     * по предъявленному принципалу
     * (docs/models/domain/other/Auditable.md §«Носитель дискриминатора —
     * контекст хода, а не поле модели»). Проставленный вызывающим, он
     * отвечал бы на вопрос «кто исполнил запись», а не «кто породил ход».
     */
    @Test
    @DisplayName("Актора писатель не проставляет — его резолвит персистентность")
    void theWriterLeavesTheActorToPersistence() {
        service.record(SURFACE, AccessDenial.Outcome.PRINCIPAL_ABSENT, null);

        assertThat(captured().getCreatedBy())
                .as("значение, поставленное здесь, разошлось бы с резолвером по предъявленному принципалу")
                .isNull();
    }

    /**
     * Несущее: сбой записи <b>не превращает отказ в доступ</b>. Поднимись
     * исключение из писателя — точка входа отказа не дошла бы до сборки
     * ответа, и вызывающий получил бы 500 вместо отказа: поведение контура
     * зависело бы от доступности журнала.
     */
    @Test
    @DisplayName("Отказ записи не превращается в доступ и не роняет тропу отказа")
    void aPersistenceFailureDoesNotBreakTheDenialPath() {
        doThrow(new IllegalStateException("база журнала недоступна")).when(dataService).save(any());

        assertThatCode(() -> service.record(SURFACE, AccessDenial.Outcome.PRINCIPAL_ABSENT, null))
                .as("сбой записи строки обязан остаться внутри писателя")
                .doesNotThrowAnyException();
    }

    /**
     * Значение поверхности приходит от вызывающего, <b>который себя не
     * предъявил</b>: длина под его контролем. Без усечения такой вызов
     * ронял бы вставку на ограничении колонки — то есть отказ доступа
     * стирал бы собственный след, и тем надёжнее, чем длиннее путь.
     */
    @Test
    @DisplayName("Поверхность усекается по потолку колонки — след не теряется на длинном пути")
    void anOversizedSurfaceIsTruncatedInsteadOfLosingTheRow() {
        service.record("GET /api/" + "x".repeat(1000), AccessDenial.Outcome.PRINCIPAL_ABSENT, null);

        assertThat(captured().getSurface())
                .as("значение обязано влезать в колонку, иначе строка не заведётся вовсе")
                .hasSizeLessThanOrEqualTo(256)
                .startsWith("GET /api/");
    }

    /**
     * Своя транзакция у записи следа.
     *
     * <p>Строка заводится в фильтр-цепочке — вне какой-либо прикладной
     * транзакции; подхватив чужую, она ушла бы вместе с её откатом. След
     * отказа обязан пережить всё, что происходит с отвергнутым запросом
     * дальше.
     */
    @Test
    @DisplayName("След отказа ложится своей транзакцией и называет её менеджер")
    void theTrailIsWrittenInItsOwnNamedTransaction() {
        Transactional declared = declaredOnSave();

        assertThat(declared)
                .as("граница записи следа обязана быть объявлена, а не подразумеваться")
                .isNotNull();
        assertThat(declared.propagation())
                .as("подхваченная чужая транзакция унесла бы след отказа своим откатом")
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(declared.transactionManager())
                .as("умолчания у выбора менеджера нет: отображений схемы на классы три")
                .isEqualTo(JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER);
    }

    private Transactional declaredOnSave() {
        Method method = Arrays.stream(AccessDenialDataService.class.getDeclaredMethods())
                .filter(candidate -> Objects.equals(candidate.getName(), "save"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("метода save у границы записи нет"));
        return method.getAnnotation(Transactional.class);
    }

    private AccessDenial captured() {
        ArgumentCaptor<AccessDenial> captor = ArgumentCaptor.forClass(AccessDenial.class);
        verify(dataService).save(captor.capture());
        return captor.getValue();
    }

    private AccessDenial denial(AccessDenial.Outcome outcome, String principal) {
        AccessDenial denial = new AccessDenial();
        denial.setSurface(SURFACE);
        denial.setOutcome(outcome);
        denial.setPrincipal(principal);
        return denial;
    }
}
