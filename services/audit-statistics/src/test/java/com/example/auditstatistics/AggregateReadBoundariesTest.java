package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.config.AggregateReadProperties;
import com.example.auditstatistics.config.AggregatesPersistenceConfig;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.AggregateGrain;
import com.example.auditstatistics.domain.model.AggregateQuery;
import com.example.auditstatistics.domain.model.JournalCompleteness;
import com.example.auditstatistics.domain.service.AggregateReadService;
import com.example.auditstatistics.domain.service.JournalCompletenessService;
import com.example.auditstatistics.domain.service.ReadQueryRejectedException;
import com.example.auditstatistics.persistence.service.DealAggregateDataService;
import com.example.auditstatistics.persistence.service.IncidentAggregateDataService;
import com.example.auditstatistics.persistence.service.StatisticsJournalCompletenessSource;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Что агрегатная выборка ОТВЕРГАЕТ и почему не сужает
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Поводов шесть, а класс отказа один, и различает их ТЕКСТ.</b>
 * Поэтому пробы смотрят не только на факт отказа: сняв охрану зерна, вопрос
 * без зерна всё равно упёрся бы в выбор запроса, а сняв охрану «окна нет» —
 * в охрану перевёрнутого окна, потому что пустые границы порядка не
 * образуют. Различает ветви повод, названный в тексте, и дом требует
 * именно этого: читателю они говорят разное.
 *
 * <p><b>Предел окна меряется СУТКАМИ, включающим их числом.</b> Обе стороны
 * сравнения проверяются: окно ровно в предел принимается, окно на сутки
 * шире отвергается. Без первой пробы сравнение можно было бы сделать
 * нестрогим, без второй — считать ширину исключающим числом, и обе ошибки
 * молча сдвигали бы предел на сутки.
 */
class AggregateReadBoundariesTest {

    private static final String TENANT = "tenant-1";
    private static final String GROUP = "audit-statistics.journal";
    private static final Integer MAX_WINDOW_DAYS = 7;
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final OffsetDateTime MOMENT =
            OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneOffset.UTC);

    private final DealAggregateDataService dealAggregateDataService = mock(DealAggregateDataService.class);
    private final IncidentAggregateDataService incidentAggregateDataService =
            mock(IncidentAggregateDataService.class);
    private final JournalCompletenessService completenessService = mock(JournalCompletenessService.class);
    private final StatisticsJournalCompletenessSource completenessSource =
            mock(StatisticsJournalCompletenessSource.class);
    private final AggregateReadProperties aggregateReadProperties = new AggregateReadProperties();
    private final ReceptionProperties receptionProperties = new ReceptionProperties();

    private AggregateReadService service;

    @BeforeEach
    void setUp() {
        aggregateReadProperties.setMaxWindowDays(MAX_WINDOW_DAYS);
        aggregateReadProperties.setPageSize(10);
        receptionProperties.setGroupId(GROUP);
        receptionProperties.setStateMaxAge(Duration.ofMinutes(5));
        service = new AggregateReadService(aggregateReadProperties, receptionProperties,
                dealAggregateDataService, incidentAggregateDataService,
                completenessService, completenessSource);
        when(dealAggregateDataService.findPage(any(), anyInt())).thenReturn(List.of());
        when(incidentAggregateDataService.findPage(any(), anyInt())).thenReturn(List.of());
        when(completenessService.completeness(any(), anyString(), any()))
                .thenReturn(new JournalCompleteness(MOMENT, Boolean.TRUE));
    }

    @Test
    @DisplayName("Зерно не названо — вопрос не принят, и повод назван первым")
    void aMissingGrainIsRejected() {
        assertThatThrownBy(() -> service.read(query().grain(null).build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("Зерно обязательно");
    }

    /**
     * Значение вне перечня доезжает сюда ПУСТЫМ: разбор на границе
     * маппинга пустоту и отдаёт, а бросать он не вправе — иначе тот же
     * отказ отвечал бы другим кодом.
     */
    @Test
    @DisplayName("Зерно вне перечня разбирается в пустоту и отвергается тем же поводом")
    void anUnknownGrainResolvesToAbsence() {
        assertThat(AggregateGrain.resolve("QUARTERLY")).isNull();
        assertThat(AggregateGrain.resolve("  ")).isNull();
        assertThat(AggregateGrain.resolve("DEAL")).isEqualTo(AggregateGrain.DEAL);
        assertThat(AggregateGrain.resolve("INCIDENT")).isEqualTo(AggregateGrain.INCIDENT);
    }

    @Test
    @DisplayName("Окна нет — вопрос не принят, а не прочитан целиком")
    void aMissingWindowIsRejected() {
        assertThatThrownBy(() -> service.read(query().from(null).build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("Окно по суткам зерна обязательно");

        verify(dealAggregateDataService, never()).findPage(any(), anyInt());
    }

    @Test
    @DisplayName("Окно перевёрнуто — отказ, а не пустая выдача")
    void anInvertedWindowIsRejected() {
        assertThatThrownBy(() -> service.read(query().from(FROM.plusDays(3)).to(FROM).build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("раньше левой");
    }

    @Test
    @DisplayName("Окно ровно в предел принимается: предел — наибольшее допустимое, а не первое запрещённое")
    void aWindowExactlyAtTheLimitIsAccepted() {
        assertThatCode(() -> service.read(query().to(FROM.plusDays(MAX_WINDOW_DAYS - 1)).build()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Окно на сутки шире предела отвергается: ширина считается включающим числом суток")
    void aWindowOneDayWiderIsRejected() {
        assertThatThrownBy(() -> service.read(query().to(FROM.plusDays(MAX_WINDOW_DAYS)).build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("Окно шире допустимого");
    }

    /**
     * Предел приходит из конфигурации, а не из константы кода: зашитый в
     * код, он потребовал бы сборки на всякую перекалибровку.
     */
    @Test
    @DisplayName("Предел ширины окна приходит из конфигурации")
    void theWindowLimitComesFromTheConfiguration() {
        aggregateReadProperties.setMaxWindowDays(2);

        assertThatThrownBy(() -> service.read(query().to(FROM.plusDays(2)).build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("2 суток");
    }

    @Test
    @DisplayName("Позиция названа наполовину — отказ, а не чтение с начала окна")
    void aPartialCursorIsRejected() {
        assertThatThrownBy(() -> service.read(query().cursorBucketDate(FROM).build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("Позиция задаётся сутками зерна и биржевым счётом вместе");

        assertThatThrownBy(() -> service.read(query().cursorExchangeAccountInternalId("acc-1").build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("Позиция задаётся сутками зерна и биржевым счётом вместе");
    }

    /**
     * Компонент ключа сделочного зерна, названный без обязательных двух,
     * — тоже половина позиции: без них он не указывает ни на что.
     */
    @Test
    @DisplayName("Компонент сделочного ключа без обязательных двух — тоже половина позиции")
    void aTailCursorComponentAloneIsRejected() {
        assertThatThrownBy(() -> service.read(query().cursorResultCurrency("USDT").build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("Позиция задаётся сутками зерна и биржевым счётом вместе");
    }

    @Test
    @DisplayName("Позиция несёт компоненты чужого зерна — отказ, а не молчаливое их игнорирование")
    void aForeignGrainCursorIsRejected() {
        assertThatThrownBy(() -> service.read(query().grain(AggregateGrain.INCIDENT)
                .cursorBucketDate(FROM)
                .cursorExchangeAccountInternalId("acc-1")
                .cursorStrategyInternalId("strategy-1")
                .build()))
                .isInstanceOf(ReadQueryRejectedException.class)
                .hasMessageContaining("компоненты не выбранного зерна");
    }

    /**
     * Обратной ветви у предиката быть не должно: у сделочного зерна все
     * четыре компонента свои, и отказ на них закрыл бы законное
     * продолжение страницы.
     */
    @Test
    @DisplayName("Те же компоненты при СДЕЛОЧНОМ зерне законны: ключ несёт все четыре")
    void theSameComponentsAreLegalForTheDealGrain() {
        assertThatCode(() -> service.read(query().grain(AggregateGrain.DEAL)
                .cursorBucketDate(FROM)
                .cursorExchangeAccountInternalId("acc-1")
                .cursorStrategyInternalId("strategy-1")
                .cursorResultCurrency("USDT")
                .build()))
                .doesNotThrowAnyException();
    }

    /**
     * Позиция с пустыми хвостовыми компонентами законна и означает строку
     * с пустым ключом: «сделка стратегии не имеет», «валюта не
     * резолвилась». Отвергнутая, она обрубила бы хвост суток.
     */
    @Test
    @DisplayName("Позиция с пустым определением и пустой валютой законна: пустота — значение")
    void aCursorWithAbsentTailComponentsIsAccepted() {
        assertThatCode(() -> service.read(query().cursorBucketDate(FROM)
                .cursorExchangeAccountInternalId("acc-1")
                .build()))
                .doesNotThrowAnyException();
    }

    /**
     * Транзакционная граница названа менеджером: подключений у процесса
     * три, и умолчания у выбора нет намеренно — неквалифицированное
     * внедрение роняет контекст.
     */
    @Test
    @DisplayName("Чтение идёт транзакцией менеджера БАЗЫ АГРЕГАТОВ, названного явно")
    void theTransactionManagerIsNamed() {
        Method read = Arrays.stream(AggregateReadService.class.getDeclaredMethods())
                .filter(method -> Objects.equals(method.getName(), "read"))
                .findFirst()
                .orElseThrow();
        Transactional transactional = read.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.transactionManager())
                .as("подключение читателя выбирается объявлением, а не умолчанием")
                .isEqualTo(AggregatesPersistenceConfig.AGGREGATES_TRANSACTION_MANAGER);
    }

    /** Зерно выбирает не только форму строки, но и таблицу, к которой идёт запрос. */
    @Test
    @DisplayName("Зерно выбирает запрос: сделочное читает свою таблицу, происшествий — свою")
    void theGrainChoosesTheQuery() {
        service.read(query().grain(AggregateGrain.DEAL).build());

        verify(dealAggregateDataService).findPage(any(), anyInt());
        verify(incidentAggregateDataService, never()).findPage(any(), anyInt());

        service.read(query().grain(AggregateGrain.INCIDENT).build());

        verify(incidentAggregateDataService).findPage(any(), anyInt());
    }

    private AggregateQuery.AggregateQueryBuilder query() {
        return AggregateQuery.builder()
                .tenantId(TENANT)
                .grain(AggregateGrain.DEAL)
                .from(FROM)
                .to(FROM.plusDays(1));
    }
}
