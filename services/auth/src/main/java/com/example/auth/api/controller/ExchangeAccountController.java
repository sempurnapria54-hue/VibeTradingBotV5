package com.example.auth.api.controller;

import com.example.auth.api.model.ExchangeAccountApiResponse;
import com.example.auth.api.model.RegisterExchangeAccountApiRequest;
import com.example.auth.domain.service.ExchangeAccountService;
import com.example.auth.mapping.ExchangeAccountMapper;
import com.example.auth.persistence.model.ExchangeAccountEntity;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Реестр биржевых счетов тенанта.
 *
 * <p>Наружу отдаётся `internalId`, не ключ базы; path-параметр — тоже
 * `internalId` (.claude/rules/codestyle.md §«Идентичность наружу»).
 */
@RestController
@RequestMapping("/api/v1/exchange-accounts")
public class ExchangeAccountController {

    private final ExchangeAccountService accountService;
    private final ExchangeAccountMapper accountMapper;

    public ExchangeAccountController(ExchangeAccountService accountService,
                                     ExchangeAccountMapper accountMapper) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
    }

    @Operation(summary = "Зарегистрировать биржевой счёт тенанта")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Счёт зарегистрирован"),
            @ApiResponse(responseCode = "422", description = "Контур не допускается этим окружением")
    })
    @PostMapping
    public ResponseEntity<ExchangeAccountApiResponse> register(
            @Valid @RequestBody RegisterExchangeAccountApiRequest request) {
        ExchangeAccountEntity account = accountService.register(
                request.getTenantInternalId(),
                request.getExchangeCode(),
                request.getLabel(),
                ExchangeAccount.Contour.valueOf(request.getContour()),
                request.getApiKey(),
                request.getSecret(),
                request.getPassphrase());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.persistenceToApi(account));
    }

    /**
     * Реестр счетов целиком: чтение торгового ядра, которому нужно
     * знать, какие счета существуют
     * (docs/architecture/contracts.md §«Синхронные вызовы»).
     */
    @Operation(summary = "Реестр биржевых счетов")
    @GetMapping
    public List<ExchangeAccountApiResponse> list() {
        return accountService.all().stream()
                .map(accountMapper::persistenceToApi)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Счета тенанта")
    @GetMapping("/tenant/{tenantInternalId}")
    public List<ExchangeAccountApiResponse> byTenant(@PathVariable String tenantInternalId) {
        return accountService.byTenant(tenantInternalId).stream()
                .map(accountMapper::persistenceToApi)
                .collect(Collectors.toList());
    }
}
