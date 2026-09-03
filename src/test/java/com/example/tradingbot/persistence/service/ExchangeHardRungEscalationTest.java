package com.example.tradingbot.persistence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает эскалацию биржевой ступени с её домом
 * (docs/rules/exchange-hold.md §«Границы и эскалация»).
 *
 * <p>Несущее для этого теста — <b>что жёсткая ступень встаёт из ЛЮБОГО
 * статуса</b>: «`HOLD → TRADE_BLOCKED` разрешён и реакцию не пропускает:
 * мягкий холд анкером идемпотентности не является». Гард только из
 * {@code ACTIVE} проглатывал бы жёсткую находку на бирже, уже стоящей под
 * мягкой ступенью: kill-switch не гонялся бы, отчёт не заводился, и в
 * данных не осталось бы ни строки о том, что непокрытый риск нашли.
 * Мягкую ступень биржа получает штатно — её ставит и слепота
 * safety-сети, — поэтому пара «HOLD стои́т + жёсткая находка» достижима
 * тиком детекции.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeHardRungEscalationTest {

    private static final Long EXCHANGE_ID = 1L;

    @Mock
    private ExchangeRepository repository;

    @Mock
    private com.example.tradingbot.mapping.ExchangeMapper mapper;

    @InjectMocks
    private ExchangeDataService dataService;

    @Test
    @DisplayName("Жёсткая ступень запрашивается переходом из любого статуса, кроме уже стоящего")
    void hardRungEscalatesFromAnyStatus() {
        when(repository.updateStatusUnlessAlready(EXCHANGE_ID, Exchange.Status.TRADE_BLOCKED.name()))
                .thenReturn(1);

        assertEquals(Boolean.TRUE, dataService.blockTrade(EXCHANGE_ID));

        verify(repository).updateStatusUnlessAlready(EXCHANGE_ID, Exchange.Status.TRADE_BLOCKED.name());
        verify(repository, never()).updateStatus(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Уже стоящая жёсткая ступень перехода не даёт — анкер идемпотентности сохранён")
    void standingHardRungIsNotReapplied() {
        when(repository.updateStatusUnlessAlready(EXCHANGE_ID, Exchange.Status.TRADE_BLOCKED.name()))
                .thenReturn(0);

        assertEquals(Boolean.FALSE, dataService.blockTrade(EXCHANGE_ID));
    }
}
