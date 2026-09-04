package com.example.tradingbot.domain.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.persistence.service.AccessDenialDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Журнальная строка отвергнутого вызова: инвариант согласованности и поведение
 * писателя (docs/models/domain/other/AccessDenial.md).
 *
 * <p>Модель собирается настоящими полями, предикат считается сам — подменять
 * его нечем и незачем (`.claude/rules/codestyle.md` §«Тесты доменных
 * моделей»). Мокается только коллаборатор — граница persistence.
 */
@ExtendWith(MockitoExtension.class)
class AccessDenialRowTest {

    private static final String SURFACE = "POST /api/exchanges/x/trade-unblock";

    @Mock
    private AccessDenialDataService dataService;

    @InjectMocks
    private AccessDenialService service;

    @Test
    @DisplayName("Пустота принципала и PRINCIPAL_ABSENT — одно состояние, выраженное дважды")
    void consistencyHoldsWhenAbsentPrincipalHasNoName() {
        assertThat(denial(AccessDenial.Outcome.PRINCIPAL_ABSENT, null).isConsistent()).isTrue();
        assertThat(denial(AccessDenial.Outcome.OPERATION_FORBIDDEN, "holder").isConsistent()).isTrue();
    }

    @Test
    @DisplayName("Расхождение класса отказа и принципала — дефект писателя, а не законное состояние")
    void inconsistentCombinationsAreRejected() {
        // «Принципала нет», но имя записано — заявленное неудостоверённое имя
        // как факт: ровно то, что модель запрещает.
        assertThat(denial(AccessDenial.Outcome.PRINCIPAL_ABSENT, "holder").isConsistent()).isFalse();
        // «Операция не разрешена» без имени: непонятно, кому не разрешена.
        assertThat(denial(AccessDenial.Outcome.OPERATION_FORBIDDEN, null).isConsistent()).isFalse();
        assertThat(denial(AccessDenial.Outcome.OPERATION_FORBIDDEN, "  ").isConsistent()).isFalse();
    }

    @Test
    @DisplayName("Писатель заводит строку с идентификатором и переданными полями")
    void writerPersistsRowWithIdentity() {
        when(dataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(SURFACE, AccessDenial.Outcome.PRINCIPAL_ABSENT, null);

        ArgumentCaptor<AccessDenial> captor = ArgumentCaptor.forClass(AccessDenial.class);
        verify(dataService).save(captor.capture());
        AccessDenial saved = captor.getValue();
        assertThat(saved.getInternalId()).isNotBlank();
        assertThat(saved.getSurface()).isEqualTo(SURFACE);
        assertThat(saved.getOutcome()).isEqualTo(AccessDenial.Outcome.PRINCIPAL_ABSENT);
        assertThat(saved.getPrincipal()).isNull();
    }

    /**
     * Несущее: сбой записи <b>не превращает отказ в доступ</b>. Если бы
     * исключение поднялось из писателя, точка входа отказа не дошла бы до
     * сборки ответа, и вызывающий получил бы 500 вместо отказа — то есть
     * поведение контура зависело бы от доступности журнала.
     */
    @Test
    @DisplayName("Отказ записи не превращается в доступ и не роняет тропу отказа")
    void persistenceFailureDoesNotBreakDenialPath() {
        doThrow(new IllegalStateException("журнал недоступен")).when(dataService).save(any());

        assertThatCode(() -> service.record(SURFACE, AccessDenial.Outcome.PRINCIPAL_ABSENT, null))
                .as("сбой записи строки обязан остаться внутри писателя")
                .doesNotThrowAnyException();
    }

    /**
     * Поверхность приходит от вызывающего, <b>который себя не предъявил</b>:
     * длина под его контролем. Без усечения такой вызов ронял бы запись на
     * ограничении колонки — то есть отказ доступа стирал бы собственный след,
     * и чем длиннее путь, тем надёжнее.
     */
    @Test
    @DisplayName("Поверхность усекается по потолку колонки — след не теряется на длинном пути")
    void oversizedSurfaceIsTruncatedInsteadOfLosingTheRow() {
        when(dataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String oversized = "GET /api/" + "x".repeat(1000);

        service.record(oversized, AccessDenial.Outcome.PRINCIPAL_ABSENT, null);

        ArgumentCaptor<AccessDenial> captor = ArgumentCaptor.forClass(AccessDenial.class);
        verify(dataService).save(captor.capture());
        assertThat(captor.getValue().getSurface())
                .as("значение обязано влезать в колонку, иначе строка не заведётся вовсе")
                .hasSizeLessThanOrEqualTo(256)
                .startsWith("GET /api/");
    }

    private AccessDenial denial(AccessDenial.Outcome outcome, String principal) {
        AccessDenial denial = new AccessDenial();
        denial.setSurface(SURFACE);
        denial.setOutcome(outcome);
        denial.setPrincipal(principal);
        return denial;
    }
}
