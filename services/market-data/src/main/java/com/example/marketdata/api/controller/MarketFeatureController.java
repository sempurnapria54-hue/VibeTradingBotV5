package com.example.marketdata.api.controller;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.marketdata.api.model.FeatureBindingApiRequest;
import com.example.marketdata.api.model.FeatureReadApiRequest;
import com.example.marketdata.api.model.IndicatorValueApiResponse;
import com.example.marketdata.api.model.MarketFeatureBundleApiResponse;
import com.example.marketdata.api.model.MarketOrderBookApiResponse;
import com.example.marketdata.api.model.MarketStructureApiResponse;
import com.example.marketdata.api.model.MarketTickerApiResponse;
import com.example.marketdata.domain.model.FeatureBinding;
import com.example.marketdata.domain.model.FeatureReadRequest;
import com.example.marketdata.domain.service.IndicatorService;
import com.example.marketdata.domain.service.MarketFeatureService;
import com.example.marketdata.domain.service.MarketPriceDataService;
import com.example.marketdata.domain.service.MarketStructureService;
import com.example.marketdata.mapping.MarketDataApiMapper;
import com.example.marketdata.persistence.service.ComputationConfigDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.marketdata.persistence.service.MarketSnapshotDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Фичи на момент решения: готовые производные, срезы и цены.
 *
 * <p><b>Срок свежести приезжает ОПЕРАНДОМ каждого чтения.</b>
 * Толерантность принадлежит читателю: одно и то же значение для одной
 * настройки свежее, для другой — уже нет
 * (docs/rules/market-data-freshness.md). Поэтому его не хранит ни строка
 * результата, ни идентичность вычисления.
 *
 * <p><b>Устаревшее и отсутствующее отвечают одинаково — {@code 204}.</b>
 * Обе пустоты означают «данным доверять нельзя» и ведут к одной реакции;
 * различать их читателю не нужно
 * (docs/spec/market-data-freshness.json).
 */
@RestController
@RequestMapping("/api/v1/market-data/instruments/{internalId}")
@RequiredArgsConstructor
public class MarketFeatureController {

    private final InstrumentDataService instrumentDataService;
    private final ComputationConfigDataService configDataService;
    private final IndicatorService indicatorService;
    private final MarketStructureService marketStructureService;
    private final MarketFeatureService marketFeatureService;
    private final MarketPriceDataService marketPriceDataService;
    private final MarketSnapshotDataService snapshotDataService;
    private final MarketDataApiMapper apiMapper;

    @Operation(summary = "Последнее свежее значение индикатора по идентичности вычисления")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Значение есть и свежо под названный срок"),
            @ApiResponse(responseCode = "204", description = "Значения нет либо оно старше названного срока"),
            @ApiResponse(responseCode = "400", description = "Инструмента либо идентичности с таким идентификатором нет")
    })
    @GetMapping("/indicator-values/latest")
    public ResponseEntity<IndicatorValueApiResponse> getLatestIndicatorValue(
            @PathVariable String internalId,
            @RequestParam String configInternalId,
            @RequestParam Duration tolerance) {
        Long instrumentId = instrumentDataService.getRequiredIdByInternalId(internalId);
        Long configId = configDataService.getRequiredIndicatorConfigByInternalId(configInternalId).getId();
        return indicatorService.getLatestValue(instrumentId, configId, tolerance)
                .map(apiMapper::domainToApi)
                .map(response -> {
                    response.setInstrumentInternalId(internalId);
                    response.setIndicatorConfigInternalId(configInternalId);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Последняя свежая структура рынка по идентичности вычисления")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Структура есть и свежа под названный срок"),
            @ApiResponse(responseCode = "204", description = "Структуры нет либо она старше названного срока"),
            @ApiResponse(responseCode = "400", description = "Инструмента либо идентичности с таким идентификатором нет")
    })
    @GetMapping("/market-structures/latest")
    public ResponseEntity<MarketStructureApiResponse> getLatestMarketStructure(
            @PathVariable String internalId,
            @RequestParam String configInternalId,
            @RequestParam Duration tolerance) {
        Long instrumentId = instrumentDataService.getRequiredIdByInternalId(internalId);
        Long configId = configDataService.getRequiredMarketStructureConfigByInternalId(configInternalId).getId();
        return marketStructureService.getLatestStructure(instrumentId, configId, tolerance)
                .map(apiMapper::domainToApi)
                .map(response -> {
                    response.setInstrumentInternalId(internalId);
                    response.setMarketStructureConfigInternalId(configInternalId);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Фичи на момент решения одним чтением.
     *
     * <p>{@code POST} у чтения — не оговорка: привязки и клаузы не
     * помещаются в строку запроса, а тело у {@code GET} контракту не
     * принадлежит. Состояния вызов не меняет.
     *
     * <p><b>Чтение одно, а не по операнду.</b> Потребителю нужны сразу все
     * входы его условий и калькуляторов; россыпь вызовов собрала бы
     * контекст из значений РАЗНЫХ моментов — пока идёт обход имён, тик
     * расчёта успевает записать новое значение.
     */
    @Operation(summary = "Фичи на момент решения: значения, предыдущие значения, структуры, цены, фаза")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Связка снята; пустое место в раскладке — вход недоступен"),
            @ApiResponse(responseCode = "400", description = "Инструмента либо идентичности с таким идентификатором нет")
    })
    @PostMapping("/features")
    public ResponseEntity<MarketFeatureBundleApiResponse> readFeatures(
            @PathVariable String internalId,
            @Valid @RequestBody FeatureReadApiRequest request) {
        Instrument instrument = instrumentDataService.getRequiredByInternalId(internalId);
        FeatureReadRequest readRequest = FeatureReadRequest.builder()
                .indicatorBindings(indicatorBindings(request.getIndicatorBindings()))
                .structureBindings(structureBindings(request.getStructureBindings()))
                .phaseRules(request.getPhaseRules())
                .priceRequired(request.getPriceRequired())
                .build();
        MarketFeatureBundleApiResponse response = apiMapper.domainToApi(
                marketFeatureService.readFeatures(instrument, readRequest));
        response.setInstrumentInternalId(internalId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Цены момента инструмента: last, mark, index")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Цены получены у площадки"),
            @ApiResponse(responseCode = "204", description = "Тикера на площадке нет"),
            @ApiResponse(responseCode = "502", description = "Площадка отказала в чтении")
    })
    @GetMapping("/prices")
    public ResponseEntity<MarketPriceData> getPrices(@PathVariable String internalId) {
        Instrument instrument = instrumentDataService.getRequiredByInternalId(internalId);
        MarketPriceData prices = marketPriceDataService.getMarketPriceData(
                instrument.getId(), instrument.getExternalId());
        return ResponseEntity.ofNullable(prices);
    }

    @Operation(summary = "Последний снятый срез книги заявок")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Срез есть"),
            @ApiResponse(responseCode = "204", description = "Срезов по инструменту ещё не снято")
    })
    @GetMapping("/order-book/latest")
    public ResponseEntity<MarketOrderBookApiResponse> getLatestOrderBook(@PathVariable String internalId) {
        Long instrumentId = instrumentDataService.getRequiredIdByInternalId(internalId);
        return snapshotDataService.findLatestOrderBook(instrumentId)
                .map(apiMapper::domainToApi)
                .map(response -> {
                    response.setInstrumentInternalId(internalId);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Последний снятый срез цен")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Срез есть"),
            @ApiResponse(responseCode = "204", description = "Срезов по инструменту ещё не снято")
    })
    @GetMapping("/ticker/latest")
    public ResponseEntity<MarketTickerApiResponse> getLatestTicker(@PathVariable String internalId) {
        Long instrumentId = instrumentDataService.getRequiredIdByInternalId(internalId);
        return snapshotDataService.findLatestTicker(instrumentId)
                .map(apiMapper::domainToApi)
                .map(response -> {
                    response.setInstrumentInternalId(internalId);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private List<FeatureBinding> indicatorBindings(List<FeatureBindingApiRequest> requests) {
        return emptyIfNull(requests).stream()
                .map(binding -> new FeatureBinding(binding.getKey(),
                        configDataService.getRequiredIndicatorConfigByInternalId(
                                binding.getConfigInternalId()).getId(),
                        binding.getTolerance()))
                .collect(toList());
    }

    private List<FeatureBinding> structureBindings(List<FeatureBindingApiRequest> requests) {
        return emptyIfNull(requests).stream()
                .map(binding -> new FeatureBinding(binding.getKey(),
                        configDataService.getRequiredMarketStructureConfigByInternalId(
                                binding.getConfigInternalId()).getId(),
                        binding.getTolerance()))
                .collect(toList());
    }
}
