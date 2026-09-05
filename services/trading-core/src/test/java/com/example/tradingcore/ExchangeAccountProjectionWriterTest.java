package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.mapping.ExchangeAccountMapper;
import com.example.tradingcore.mapping.ExchangeAccountMapperImpl;
import com.example.tradingcore.persistence.model.ExchangeAccountEntity;
import com.example.tradingcore.persistence.repository.ExchangeAccountRepository;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Тик синка пишет ТОЛЬКО проекционные колонки строки счёта.
 *
 * <p>Строку счёта пишут две тропы: реестровую часть — синк, торговое
 * состояние — торговый код ядра
 * (docs/models/domain/core/ExchangeAccount.md §Персистентность). В ответе
 * реестра торговых полей нет и быть не может, поэтому перенос «всех
 * полей» затирал бы базу риска, счётчики и ступень пустотой — то есть
 * ронял бы охрану, ради которой они и заведены.
 *
 * <p>Второе условие теста — <b>стартовые значения ставит заведение
 * строки</b>, а не {@code DEFAULT} колонки: вставляет приложение, и база
 * риска у новой строки остаётся ПУСТОЙ, потому что пустота есть отказ, а
 * не ноль.
 */
class ExchangeAccountProjectionWriterTest {

    private static final OffsetDateTime PROJECTED_AT = OffsetDateTime.of(
            2026, 9, 5, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String INTERNAL_ID = "ea-1";

    private final ExchangeAccountRepository repository = mock(ExchangeAccountRepository.class);
    private final ExchangeAccountMapper mapper = new ExchangeAccountMapperImpl();
    private final ExchangeAccountDataService dataService =
            new ExchangeAccountDataService(repository, mapper);

    @Test
    void tradingStateSurvivesTheProjectionUpdate() {
        ExchangeAccountEntity stored = tradingRow();
        when(repository.findByInternalId(INTERNAL_ID)).thenReturn(Optional.of(stored));

        dataService.upsertProjection(registryView("demo-2", ExchangeAccount.Status.CLOSED), PROJECTED_AT);

        ExchangeAccountEntity saved = captureSaved();
        assertThat(saved.getLabel()).isEqualTo("demo-2");
        assertThat(saved.getStatus()).isEqualTo(ExchangeAccount.Status.CLOSED.name());
        assertThat(saved.getProjectedAt()).isEqualTo(PROJECTED_AT);
        assertThat(saved.getRiskBase()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(saved.getRiskBaseCurrency()).isEqualTo("USDT");
        assertThat(saved.getConsecutiveLossCount()).isEqualTo(3);
        assertThat(saved.getBlindPassCount()).isEqualTo(2);
        assertThat(saved.getSafetyRung()).isEqualTo(ExchangeAccount.SafetyRung.HOLD.name());
    }

    @Test
    void newRowStartsWithEmptyRiskBaseAndZeroCounters() {
        when(repository.findByInternalId(INTERNAL_ID)).thenReturn(Optional.empty());

        dataService.upsertProjection(registryView("demo-1", ExchangeAccount.Status.ACTIVE), PROJECTED_AT);

        ExchangeAccountEntity saved = captureSaved();
        assertThat(saved.getInternalId()).isEqualTo(INTERNAL_ID);
        assertThat(saved.getTenantInternalId()).isEqualTo("tn-1");
        assertThat(saved.getContour()).isEqualTo(ExchangeAccount.Contour.DEMO.name());
        assertThat(saved.getSafetyRung()).isEqualTo(ExchangeAccount.SafetyRung.ACTIVE.name());
        assertThat(saved.getConsecutiveLossCount()).isZero();
        assertThat(saved.getBlindPassCount()).isZero();
        assertThat(saved.getRiskBase()).isNull();
        assertThat(saved.getRiskBaseCurrency()).isNull();
    }

    private ExchangeAccountEntity captureSaved() {
        ArgumentCaptor<ExchangeAccountEntity> captor = ArgumentCaptor.forClass(ExchangeAccountEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private ExchangeAccountEntity tradingRow() {
        ExchangeAccountEntity entity = new ExchangeAccountEntity();
        entity.setInternalId(INTERNAL_ID);
        entity.setTenantInternalId("tn-1");
        entity.setExchangeCode("OKX");
        entity.setLabel("demo-1");
        entity.setContour(ExchangeAccount.Contour.DEMO.name());
        entity.setStatus(ExchangeAccount.Status.ACTIVE.name());
        entity.setProjectedAt(PROJECTED_AT.minusHours(1));
        entity.setRiskBase(BigDecimal.TEN);
        entity.setRiskBaseCurrency("USDT");
        entity.setConsecutiveLossCount(3);
        entity.setBlindPassCount(2);
        entity.setSafetyRung(ExchangeAccount.SafetyRung.HOLD.name());
        return entity;
    }

    private ExchangeAccount registryView(String label, ExchangeAccount.Status status) {
        ExchangeAccount account = new ExchangeAccount();
        account.setInternalId(INTERNAL_ID);
        account.setTenantId("tn-1");
        account.setExchangeCode("OKX");
        account.setLabel(label);
        account.setContour(ExchangeAccount.Contour.DEMO);
        account.setStatus(status);
        return account;
    }
}
