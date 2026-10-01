package com.example.auth.api.controller;

import com.example.auth.api.model.ExchangeAccountApiResponse;
import com.example.auth.api.model.RegisterExchangeAccountApiRequest;
import com.example.auth.api.model.RotateExchangeAccountKeysApiRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
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
@RequestMapping("/api/v1/auth/exchange-accounts")
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
     * Смена ключей счёта — второе действие владельца над тем же путём
     * хранилища (docs/architecture/tenant-and-exchange.md §Ключи).
     *
     * <p>Проверка права та же, что у соседних операций: принятый принципал,
     * пер-операционной проверки нет — при одном субъекте различать некого
     * (docs/rules/api-access-policy.md). Ключи ответ не возвращает.
     */
    @Operation(summary = "Сменить ключи биржевого счёта; контур остаётся прежним")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ключи заменены; контур взят со строки счёта"),
            @ApiResponse(responseCode = "400", description = "Ключ, секрет либо passphrase не предъявлен"),
            @ApiResponse(responseCode = "404", description = "Счёта с такой идентичностью нет"),
            @ApiResponse(responseCode = "409", description = "Счёт отключён и ключей не принимает")
    })
    @PutMapping("/{accountInternalId}/keys")
    public ExchangeAccountApiResponse rotateKeys(@PathVariable String accountInternalId,
                                                 @Valid @RequestBody RotateExchangeAccountKeysApiRequest request) {
        ExchangeAccountEntity account = accountService.rotateKeys(
                accountInternalId,
                request.getApiKey(),
                request.getSecret(),
                request.getPassphrase());
        return accountMapper.persistenceToApi(account);
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
