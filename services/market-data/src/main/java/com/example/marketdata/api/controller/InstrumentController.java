package com.example.marketdata.api.controller;

import com.example.marketdata.api.model.CandleApiResponse;
import com.example.marketdata.api.model.CandleGroupApiResponse;
import com.example.marketdata.api.model.InstrumentApiResponse;
import com.example.marketdata.mapping.MarketDataApiMapper;
import com.example.marketdata.persistence.service.CandleDataService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.marketdata.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Каталог инструментов и собранная по ним история.
 *
 * <p><b>Инструменты наружу адресуются {@code internalId}</b>
 * (.claude/rules/codestyle.md §«Идентичность наружу»).
 *
 * <p><b>Ставки комиссии в справочных правилах нет.</b> Она атрибут
 * комиссионного уровня СЧЁТА и читается с ключами счёта
 * (docs/models/domain/other/TradeFeeRate.md), а market-data ходит к
 * площадке только публичными чтениями; навес несёт ключ комиссионной
 * группы, ставку по нему резолвит тот, у кого счёт есть.
 */
@RestController
@RequestMapping("/api/v1/market-data/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    /** Статусы, при которых инструмент считается действующим листингом. */
    private static final Set<Instrument.Status> LISTED_STATUSES = Set.of(
            Instrument.Status.SYNC,
            Instrument.Status.CANDLES_LOADING,
            Instrument.Status.ACTIVE);

    private final InstrumentDataService instrumentDataService;
    private final InstrumentExternalRulesDataService rulesDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final CandleDataService candleDataService;
    private final MarketDataApiMapper apiMapper;

    @Operation(summary = "Действующий листинг каталога")
    @GetMapping
    public List<InstrumentApiResponse> getInstruments() {
        return apiMapper.domainToApiInstruments(instrumentDataService.findByStatusIn(LISTED_STATUSES));
    }

    @Operation(summary = "Инструмент каталога")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Инструмент найден"),
            @ApiResponse(responseCode = "400", description = "Инструмента с таким идентификатором нет")
    })
    @GetMapping("/{internalId}")
    public InstrumentApiResponse getInstrument(@PathVariable String internalId) {
        return apiMapper.domainToApi(instrumentDataService.getRequiredByInternalId(internalId));
    }

    /**
     * Справочные правила инструмента.
     *
     * <p>{@code 204}, а не пустое тело со {@code 200}: навес может быть
     * ещё не материализован, и «правил нет» отличается от «правила
     * пусты».
     */
    @Operation(summary = "Справочные правила инструмента")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Навес правил материализован"),
            @ApiResponse(responseCode = "204", description = "Навес правил ещё не собран"),
            @ApiResponse(responseCode = "400", description = "Инструмента с таким идентификатором нет")
    })
    @GetMapping("/{internalId}/rules")
    public ResponseEntity<InstrumentExternalRules> getInstrumentRules(@PathVariable String internalId) {
        Long instrumentId = instrumentDataService.getRequiredIdByInternalId(internalId);
        return rulesDataService.findByInstrumentId(instrumentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Единицы сбора свечей инструмента")
    @GetMapping("/{internalId}/candle-groups")
    public List<CandleGroupApiResponse> getCandleGroups(@PathVariable String internalId) {
        Long instrumentId = instrumentDataService.getRequiredIdByInternalId(internalId);
        return apiMapper.domainToApiGroups(candleGroupDataService.findByInstrumentId(instrumentId));
    }

    /**
     * История свечей окном — пакетное чтение для бэктеста.
     *
     * <p><b>Окно обязательно.</b> Безлимитного чтения истории нет:
     * минутные свечи за годы кладут и базу, и читателя
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Operation(summary = "История свечей инструмента окном")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Окно истории"),
            @ApiResponse(responseCode = "400", description = "Инструмента либо единицы сбора с таким таймфреймом нет")
    })
    @GetMapping("/{internalId}/candles")
    public List<CandleApiResponse> getCandles(@PathVariable String internalId,
                                              @RequestParam TimeFrame timeframe,
                                              @RequestParam Long fromMillis,
                                              @RequestParam Integer limit) {
        Long instrumentId = instrumentDataService.getRequiredIdByInternalId(internalId);
        CandleGroup group = candleGroupDataService.findByInstrumentIdAndTimeframe(instrumentId, timeframe)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Candle group not found: " + internalId + " " + timeframe));
        return apiMapper.domainToApiCandles(
                candleDataService.findHistoryFrom(group.getId(), fromMillis, limit));
    }
}
