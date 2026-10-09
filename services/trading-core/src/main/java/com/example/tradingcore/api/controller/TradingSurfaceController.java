package com.example.tradingcore.api.controller;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.api.model.AccountInstrumentStateApiRequest;
import com.example.tradingcore.api.model.AccountInstrumentStateApiResponse;
import com.example.tradingcore.api.model.DealApiResponse;
import com.example.tradingcore.api.model.PairCheckApiRequest;
import com.example.tradingcore.api.model.PairCheckApiResponse;
import com.example.tradingcore.api.model.RiskAppetiteApiResponse;
import com.example.tradingcore.api.model.SafetyStateApiResponse;
import com.example.tradingcore.domain.model.PairCheck;
import com.example.tradingcore.domain.service.TradingSurfaceService;
import com.example.tradingcore.mapping.TradingSurfaceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Чтения торгового состояния и принятых чисел риск-аппетита, назначение
 * плеча пары.
 *
 * <p><b>Путь-параметр — {@code internalId}, не ключ БД</b>
 * (.claude/rules/codestyle.md §«Идентичность наружу»): числовой ключ
 * границу сервиса не пересекает.
 *
 * <p><b>Перевод api ↔ domain делает контроллер маппером.</b> Сервис
 * поверхности отдаёт доменные модели, api-моделей не видит вовсе — это
 * граница слоёв (.claude/rules/codestyle.md §Слои).
 *
 * <p><b>Читатели названы контрактом:</b> периметр в контексте тенанта и
 * держатель, которому иначе не видно, что сделали ручные операции
 * (docs/architecture/contracts.md §«Синхронные вызовы»).
 */
@Validated
@RestController
@RequestMapping("/api/v1/trading-core")
@RequiredArgsConstructor
public class TradingSurfaceController {

    private final TradingSurfaceService tradingSurfaceService;
    private final TradingSurfaceMapper mapper;

    /**
     * Сделки счёта окном.
     *
     * <p>Идентичности инструментов резолвятся ОДНОЙ раскладкой на всю
     * выборку: чтение на каждую строку окна дало бы запрос в цикле
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
     * одного поля»). Счёт у выборки один
     * и уже прочитан.
     */
    @Operation(summary = "Сделки биржевого счёта недавним окном, от новых")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Выборка отдана"),
            @ApiResponse(responseCode = "400", description = "Счёт с такой идентичностью не найден")})
    @GetMapping("/deals")
    public List<DealApiResponse> findDeals(@RequestParam @NotBlank String exchangeAccountInternalId) {
        ExchangeAccount account = tradingSurfaceService.getAccount(exchangeAccountInternalId);
        List<Deal> deals = tradingSurfaceService.findDeals(account.getId());
        Map<Long, String> instrumentIdentities = tradingSurfaceService.instrumentInternalIds(
                deals.stream()
                        .map(Deal::getInstrumentId)
                        .filter(instrumentId -> nonNull(instrumentId))
                        .collect(Collectors.toSet()));
        return deals.stream()
                .map(deal -> withIdentities(deal, account.getInternalId(), instrumentIdentities))
                .collect(Collectors.toList());
    }

    @Operation(summary = "Сделка со своими траншами")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сделка отдана"),
            @ApiResponse(responseCode = "400", description = "Сделка с такой идентичностью не найдена")})
    @GetMapping("/deals/{internalId}")
    public DealApiResponse getDeal(@PathVariable String internalId) {
        Deal deal = tradingSurfaceService.getDeal(internalId);
        ExchangeAccount account = tradingSurfaceService.getAccountById(deal.getExchangeAccountId());
        DealApiResponse response = withIdentities(deal, account.getInternalId(),
                tradingSurfaceService.instrumentInternalIds(
                        isNull(deal.getInstrumentId()) ? List.of() : List.of(deal.getInstrumentId())));
        response.setTranches(deal.getTranches().stream()
                .map(mapper::domainToApi)
                .collect(Collectors.toList()));
        return response;
    }

    @Operation(summary = "Торговое состояние биржевого счёта и его пар")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние отдано"),
            @ApiResponse(responseCode = "400", description = "Счёт с такой идентичностью не найден")})
    @GetMapping("/safety/states/{exchangeAccountInternalId}")
    public SafetyStateApiResponse getSafetyState(@PathVariable String exchangeAccountInternalId) {
        ExchangeAccount account = tradingSurfaceService.getAccount(exchangeAccountInternalId);
        SafetyStateApiResponse response = mapper.domainToApi(account);
        response.setStandingInstrumentRungs(
                mapper.domainToApi(tradingSurfaceService.standingInstrumentRungs(account.getId())));
        return response;
    }

    /**
     * Числа риск-аппетита, принятые ядром при старте. Единственный
     * вызывающий — владелец определений стратегий: операнды неравенств
     * создания и активации (docs/architecture/contracts.md §«Синхронные
     * вызовы»). Конфигурацию окружения он не читает сам: принимающее звено
     * одно (docs/rules/risk-policy.md, правило о числах риск-аппетита).
     */
    @Operation(summary = "Принятые ядром числа риск-аппетита окружения")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Числа отданы: все шесть заданы всегда")})
    @GetMapping("/risk-appetite")
    public RiskAppetiteApiResponse getRiskAppetite() {
        return mapper.domainToApi(tradingSurfaceService.getRiskAppetite());
    }

    /**
     * Назначение плеча счёта на инструменте — ручная статичная настройка
     * держателя (docs/rules/trading-constraints.md). Пока плеча нет,
     * risk-creating действие по паре отвергается. Форма назначения —
     * {@code PUT} снимком намерения целиком: непереданное поле стирает
     * прежнее значение. Плечо выше предела конфигурации отвергается.
     */
    @Operation(summary = "Назначить торговые настройки счёта на инструменте")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Настройки назначены"),
            @ApiResponse(responseCode = "400", description = "Негодное тело запроса, плечо выше предела плеча"
                    + " конфигурации либо счёт или инструмент с такой идентичностью не найдены")})
    @PutMapping("/pair-settings/{exchangeAccountInternalId}/{instrumentInternalId}")
    public AccountInstrumentStateApiResponse applyPairSettings(
            @PathVariable String exchangeAccountInternalId, @PathVariable String instrumentInternalId,
            @Valid @RequestBody AccountInstrumentStateApiRequest request) {
        ExchangeAccount account = tradingSurfaceService.getAccount(exchangeAccountInternalId);
        AccountInstrumentStateApiResponse response = mapper.domainToApi(tradingSurfaceService.applyPairSettings(
                account.getId(), instrumentInternalId, mapper.apiToDomain(request)));
        response.setExchangeAccountInternalId(account.getInternalId());
        response.setInstrumentInternalId(instrumentInternalId);
        return response;
    }

    /**
     * Единственный вызывающий — владелец определений стратегий: он
     * проверяет, разрешаются ли ссылки определения, на создании и на
     * активации (docs/architecture/contracts.md §«Синхронные вызовы»).
     */
    @Operation(summary = "Разрешаются ли счёт и инструмент в контексте тенанта")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Признаки отданы"),
            @ApiResponse(responseCode = "400", description = "Негодные параметры запроса")})
    @GetMapping("/pair-checks")
    public PairCheckApiResponse checkPair(@Valid @ParameterObject PairCheckApiRequest request) {
        PairCheck check = tradingSurfaceService.checkPair(request.getTenantInternalId(),
                request.getExchangeAccountInternalId(), request.getInstrumentInternalId());
        PairCheckApiResponse response = new PairCheckApiResponse();
        response.setAccountFound(check.accountFound());
        response.setAccountBelongsToTenant(check.accountBelongsToTenant());
        response.setInstrumentFound(check.instrumentFound());
        return response;
    }

    /** Ответ по сделке с резолвенными идентичностями связанных сущностей. */
    private DealApiResponse withIdentities(Deal deal, String accountInternalId,
                                           Map<Long, String> instrumentIdentities) {
        DealApiResponse response = mapper.domainToApi(deal);
        response.setExchangeAccountInternalId(accountInternalId);
        response.setInstrumentInternalId(instrumentIdentities.get(deal.getInstrumentId()));
        return response;
    }
}
