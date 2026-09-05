package com.example.marketdata.api.controller;

import static java.util.Objects.isNull;

import com.example.marketdata.api.model.CandleGroupApiResponse;
import com.example.marketdata.api.model.CandleRequirementApiRequest;
import com.example.marketdata.api.model.IndicatorConfigApiRequest;
import com.example.marketdata.api.model.IndicatorConfigApiResponse;
import com.example.marketdata.api.model.MarketStructureConfigApiRequest;
import com.example.marketdata.api.model.MarketStructureConfigApiResponse;
import com.example.marketdata.domain.model.IndicatorConfig;
import com.example.marketdata.domain.model.MarketStructureConfig;
import com.example.marketdata.domain.service.MarketDataDemandService;
import com.example.marketdata.mapping.ComputationParamsJsonConverter;
import com.example.marketdata.mapping.MarketDataApiMapper;
import com.example.marketdata.persistence.service.ComputationConfigDataService;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Поверхность требований: чем потребитель объявляет, что ему нужно.
 *
 * <p><b>Требование — синхронная команда, а не событие и не
 * конфигурация</b> (docs/architecture/market-data-collection.md §«Как
 * потребность доходит до сбора»). Тропа выбрана не по вкусу: она
 * единственная, что уже существует в контракте — {@code trading-core},
 * {@code strategies} и {@code bff} ходят сюда синхронно, а потребителем
 * событий стратегии market-data не значится.
 *
 * <p><b>Все три команды идемпотентны по СОДЕРЖАНИЮ.</b> Повтор того же
 * требования возвращает ту же единицу сбора либо ту же идентичность —
 * отсюда {@code 200}, а не {@code 201}: второго объекта не создаётся
 * (docs/rules/idempotency-via-unique.md).
 *
 * <p><b>Отзыва требования нет намеренно.</b> Собранное остаётся:
 * восполнимое чистится по глубине, а не по тому, кому оно перестало быть
 * нужным, — иначе снятие одной стратегии удаляло бы историю, на которой
 * стои́т бэктест другой (docs/rules/market-data-retention.md).
 */
@RestController
@RequestMapping("/api/v1/market-data/requirements")
@RequiredArgsConstructor
public class MarketDataDemandController {

    private final MarketDataDemandService demandService;
    private final ComputationConfigDataService configDataService;
    private final ComputationParamsJsonConverter paramsConverter;
    private final MarketDataApiMapper apiMapper;

    @Operation(summary = "Требование свечей: инструмент, таймфрейм и глубина истории")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Единица сбора заведена либо горизонт расширен"),
            @ApiResponse(responseCode = "400", description = "Инструмента с таким идентификатором нет")
    })
    @PostMapping("/candles")
    public CandleGroupApiResponse requireCandles(@Valid @RequestBody CandleRequirementApiRequest request) {
        CandleGroup group = demandService.requireCandles(request.getInstrumentInternalId(),
                request.getTimeframe(), request.getDepthBars());
        return apiMapper.domainToApi(group);
    }

    @Operation(summary = "Требование индикатора: идентичность вычисления")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Идентичность заведена либо уже существовала"),
            @ApiResponse(responseCode = "400", description = "Параметры не разбираются под заявленный тип")
    })
    @PostMapping("/indicators")
    public IndicatorConfigApiResponse requireIndicator(@Valid @RequestBody IndicatorConfigApiRequest request) {
        IndicatorConfig config = new IndicatorConfig();
        config.setIndicatorType(request.getIndicatorType());
        config.setTimeframe(request.getTimeframe());
        config.setParams(paramsConverter.toIndicatorParams(request.getParams(), request.getIndicatorType()));
        return apiMapper.domainToApi(demandService.requireIndicator(config));
    }

    @Operation(summary = "Требование структуры рынка: идентичность вычисления вместе с идентичностями входов")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Идентичность заведена либо уже существовала"),
            @ApiResponse(responseCode = "400", description = "Названного входа нет либо параметры не разбираются")
    })
    @PostMapping("/market-structures")
    public MarketStructureConfigApiResponse requireMarketStructure(
            @Valid @RequestBody MarketStructureConfigApiRequest request) {
        MarketStructureConfig config = new MarketStructureConfig();
        config.setTimeframe(request.getTimeframe());
        config.setParams(paramsConverter.toMarketStructureParams(request.getParams()));
        config.setEfficiencyRatioConfigId(resolveInputId(request.getEfficiencyRatioConfigInternalId()));
        config.setAtrConfigId(resolveInputId(request.getAtrConfigInternalId()));
        return apiMapper.domainToApi(demandService.requireMarketStructure(config));
    }

    /**
     * Идентичность входа по её межсервисному идентификатору; пустая
     * ссылка означает «вход не объявлен», и это законно — резолвер
     * работает и без опциональных входов.
     */
    private Long resolveInputId(String configInternalId) {
        if (isNull(configInternalId)) {
            return null;
        }
        return configDataService.getRequiredIndicatorConfigByInternalId(configInternalId).getId();
    }
}
