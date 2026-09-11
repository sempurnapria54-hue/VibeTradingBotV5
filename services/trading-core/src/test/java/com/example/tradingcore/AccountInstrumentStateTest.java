package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.mapping.AccountInstrumentStateMapper;
import com.example.tradingcore.persistence.model.AccountInstrumentStateEntity;
import com.example.tradingcore.persistence.repository.AccountInstrumentStateRepository;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.util.Constants;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ленивая материализация строки пары «счёт, инструмент» и предикаты её
 * состояния (docs/models/domain/core/Instrument.md §«Ступень и настройки
 * счёта на инструменте — своя таблица ядра»).
 */
class AccountInstrumentStateTest {

    private static final Long ACCOUNT = 3L;
    private static final Long INSTRUMENT = 7L;

    private final AccountInstrumentStateRepository repository = mock(AccountInstrumentStateRepository.class);
    private final AccountInstrumentStateMapper mapper = mock(AccountInstrumentStateMapper.class);

    private final AccountInstrumentStateDataService dataService =
            new AccountInstrumentStateDataService(repository, mapper, new ActorProvider());

    /**
     * Строка заводится СТАРТОВЫМИ значениями, и каждое названо: ступени нет
     * (рабочее состояние), режим маржи изолированный — единственный
     * допустимый контуром, плечо пустое — ручная настройка держателя.
     */
    @Test
    void materializationUsesTheNamedStartingValues() {
        givenStoredRow(Instrument.SafetyRung.ACTIVE, Instrument.MarginMode.ISOLATED, null);

        AccountInstrumentState state = dataService.getRequiredByPair(ACCOUNT, INSTRUMENT);

        verify(repository).insertIfAbsent(eq(ACCOUNT), eq(INSTRUMENT),
                eq(Instrument.SafetyRung.ACTIVE.name()), eq(Instrument.MarginMode.ISOLATED.name()),
                eq(Constants.Audit.SYSTEM_PRINCIPAL));
        assertThat(state.getLeverage()).isNull();
        assertThat(state.isMarginIsolated()).isTrue();
        assertThat(state.hasStandingSafetyRung()).isFalse();
    }

    /**
     * Автор строки — АКТОР ХОДА, а не литерал контура.
     *
     * <p>Проба о ПРОИСХОЖДЕНИИ значения: вставка идёт нативным запросом и
     * слушателей аудита не проходит, поэтому автора она кладёт своей рукой.
     * На ходе без предъявленного принципала поставщик и константа дают одно
     * и то же — подмену видно только там, где принципал предъявлен: строку
     * пары материализует и ручная тропа (docs/models/domain/other/Auditable.md
     * §«Область значений актора»).
     */
    @Test
    void theMaterializedRowCarriesTheActorOfTheMove() {
        givenStoredRow(Instrument.SafetyRung.ACTIVE, Instrument.MarginMode.ISOLATED, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "holder", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
        try {
            dataService.getRequiredByPair(ACCOUNT, INSTRUMENT);

            verify(repository).insertIfAbsent(eq(ACCOUNT), eq(INSTRUMENT), any(), any(), eq("holder"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Тот же автор — на ВТОРОЙ точке материализации.
     *
     * <p>Точек у безопасной вставки две — подъём ступени и чтение состояния
     * пары, — и покрытие одной из них ничего не говорит о второй: мутация,
     * подменившая поставщика литералом на непокрытой точке, осталась бы
     * зелёной. Подъём ступени приходит и с ручной тропы
     * (docs/rules/manual-halt.md), поэтому автор у него тот же вопрос.
     */
    @Test
    void theRungRaiseMaterializesTheRowWithTheActorOfTheMove() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "holder", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
        try {
            dataService.raiseRung(ACCOUNT, INSTRUMENT, Instrument.SafetyRung.ENTRY_BLOCKED);

            verify(repository).insertIfAbsent(eq(ACCOUNT), eq(INSTRUMENT), any(), any(), eq("holder"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Вставка идёт БЕЗОПАСНО ПО КЛЮЧУ и уже заведённую строку не трогает:
     * читатель получает значения победителя гонки, а не стартовые.
     */
    @Test
    void safeInsertLeavesAnExistingRowIntact() {
        givenStoredRow(Instrument.SafetyRung.ENTRY_BLOCKED, Instrument.MarginMode.CROSS, 5);

        AccountInstrumentState state = dataService.getRequiredByPair(ACCOUNT, INSTRUMENT);

        assertThat(state.getSafetyRung()).isEqualTo(Instrument.SafetyRung.ENTRY_BLOCKED);
        assertThat(state.getLeverage()).isEqualTo(5);
        assertThat(state.hasStandingSafetyRung()).isTrue();
        assertThat(state.isMarginIsolated()).isFalse();
    }

    /** Жёсткая ступень тоже несёт блок-сет: предикат различает ступень, а не её жёсткость. */
    @Test
    void hardRungAlsoStands() {
        AccountInstrumentState state = new AccountInstrumentState();
        state.setSafetyRung(Instrument.SafetyRung.TRADE_BLOCKED);

        assertThat(state.hasStandingSafetyRung()).isTrue();
    }

    /**
     * Строки нет сразу после безопасной вставки — рассогласование
     * хранилища, а не штатная пустота: молча вернуть пусто значило бы
     * отдать читателю решение по несуществующему состоянию.
     */
    @Test
    void missingRowRightAfterTheSafeInsertIsAnInconsistency() {
        when(repository.findByExchangeAccountIdAndInstrumentId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dataService.getRequiredByPair(ACCOUNT, INSTRUMENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("right after a safe insert");
    }

    private void givenStoredRow(Instrument.SafetyRung rung, Instrument.MarginMode marginMode, Integer leverage) {
        AccountInstrumentStateEntity entity = new AccountInstrumentStateEntity();
        entity.setId(1L);
        entity.setExchangeAccountId(ACCOUNT);
        entity.setInstrumentId(INSTRUMENT);
        entity.setSafetyRung(rung.name());
        entity.setMarginMode(marginMode.name());
        entity.setLeverage(leverage);
        AccountInstrumentState domain = new AccountInstrumentState(1L, ACCOUNT, INSTRUMENT, rung, marginMode,
                leverage);
        when(repository.findByExchangeAccountIdAndInstrumentId(ACCOUNT, INSTRUMENT))
                .thenReturn(Optional.of(entity));
        when(mapper.persistenceToDomain(entity)).thenReturn(domain);
    }
}
