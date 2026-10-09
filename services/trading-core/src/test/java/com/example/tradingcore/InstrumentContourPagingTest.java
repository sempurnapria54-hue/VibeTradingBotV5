package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.mapping.InstrumentExternalRulesJsonConverter;
import com.example.tradingcore.mapping.InstrumentMapperImpl;
import com.example.tradingcore.persistence.model.InstrumentEntity;
import com.example.tradingcore.persistence.repository.InstrumentRepository;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/**
 * Постраничный обход контура у границы проекции каталога
 * (docs/components/AnomalyJob.md, обход контура).
 *
 * <p><b>Что здесь проверяется по существу.</b> Размер страницы — единица
 * чтения, а не предел выборки: обход, остановленный первой полной страницей,
 * отрезал бы хвост каталога, и детекция объявила бы чужими строки среза по
 * нашим же инструментам. Следующая страница берётся КЛЮЧОМ последней
 * прочитанной строки, а не смещением, — иначе удаление строки между
 * страницами молча выбросило бы соседнюю из обхода. Остановка — на первой
 * неполной либо пустой странице: каталог, кратный странице, не должен ни
 * зациклить обход, ни потерять последнюю страницу.
 */
class InstrumentContourPagingTest {

    private static final String EXCHANGE_CODE = "OKX";
    private static final Integer PAGE_SIZE = 2;

    private final InstrumentRepository repository = mock(InstrumentRepository.class);
    private final InstrumentDataService dataService = new InstrumentDataService(repository,
            new InstrumentMapperImpl(), new InstrumentExternalRulesJsonConverter(new ObjectMapper()));

    /**
     * Каталог в три строки при странице в две: обе страницы отданы
     * потребителю, вторая запрошена ключом последней строки первой, и
     * неполная вторая страница обход заканчивает — третьего чтения нет.
     */
    @Test
    void aCatalogueWiderThanAPageIsWalkedWholeByKey() {
        when(repository.findContourAfter(EXCHANGE_CODE, Long.MIN_VALUE, PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(rows(1L, 2L));
        when(repository.findContourAfter(EXCHANGE_CODE, 2L, PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(rows(3L));
        List<List<Long>> pages = new ArrayList<>();

        dataService.forEachContourPage(EXCHANGE_CODE, PAGE_SIZE, page -> pages.add(idsOf(page)));

        assertThat(pages).containsExactly(List.of(1L, 2L), List.of(3L));
        verify(repository, times(2)).findContourAfter(eq(EXCHANGE_CODE), any(), any());
    }

    /**
     * Каталог, кратный странице: последняя полная страница не принимается за
     * конец — следующее чтение пусто, и обход на нём кончается, не отдав
     * потребителю пустой страницы.
     */
    @Test
    void aCatalogueThatIsAMultipleOfThePageEndsOnAnEmptyPage() {
        when(repository.findContourAfter(EXCHANGE_CODE, Long.MIN_VALUE, PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(rows(1L, 2L));
        when(repository.findContourAfter(EXCHANGE_CODE, 2L, PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(rows(3L, 4L));
        when(repository.findContourAfter(EXCHANGE_CODE, 4L, PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new ArrayList<>());
        List<List<Long>> pages = new ArrayList<>();

        dataService.forEachContourPage(EXCHANGE_CODE, PAGE_SIZE, page -> pages.add(idsOf(page)));

        assertThat(pages).containsExactly(List.of(1L, 2L), List.of(3L, 4L));
        verify(repository, times(3)).findContourAfter(eq(EXCHANGE_CODE), any(), any());
    }

    /** Пустая площадка: потребитель не зовётся вовсе. */
    @Test
    void anEmptyCatalogueCallsTheConsumerNever() {
        when(repository.findContourAfter(EXCHANGE_CODE, Long.MIN_VALUE, PageRequest.of(0, PAGE_SIZE)))
                .thenReturn(new ArrayList<>());
        List<List<Long>> pages = new ArrayList<>();

        dataService.forEachContourPage(EXCHANGE_CODE, PAGE_SIZE, page -> pages.add(idsOf(page)));

        assertThat(pages).isEmpty();
    }

    private static List<InstrumentEntity> rows(Long... ids) {
        List<InstrumentEntity> rows = new ArrayList<>();
        for (Long id : ids) {
            InstrumentEntity entity = new InstrumentEntity();
            entity.setId(id);
            entity.setExchangeCode(EXCHANGE_CODE);
            entity.setExternalId("INST-" + id);
            rows.add(entity);
        }
        return rows;
    }

    private static List<Long> idsOf(List<Instrument> page) {
        return page.stream()
                .map(Instrument::getId)
                .collect(Collectors.toList());
    }
}
