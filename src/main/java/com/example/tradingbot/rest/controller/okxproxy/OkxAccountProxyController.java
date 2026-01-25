package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxBalanceAccountDto;
import com.example.tradingbot.client.okx.dto.OkxPositionDto;
import com.example.tradingbot.mapping.BalanceAccountMapper;
import com.example.tradingbot.mapping.PositionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5/account")
public class OkxAccountProxyController {
    private final OkxRestClient okxRestClient;
    private final OkxProxyResponseHandler responseHandler;
    private final BalanceAccountMapper balanceAccountMapper;
    private final PositionMapper positionMapper;

    public OkxAccountProxyController(
            OkxRestClient okxRestClient,
            OkxProxyResponseHandler responseHandler,
            BalanceAccountMapper balanceAccountMapper,
            PositionMapper positionMapper
    ) {
        this.okxRestClient = okxRestClient;
        this.responseHandler = responseHandler;
        this.balanceAccountMapper = balanceAccountMapper;
        this.positionMapper = positionMapper;
    }

    @GetMapping("/balance")
    public ResponseEntity<?> getBalance(@RequestParam(required = false) String ccy) {
        try {
            OkxApiResponse<OkxBalanceAccountDto> response = okxRestClient.getBalance(ccy);
            return responseHandler.handleResponse(
                    response,
                    balanceAccountMapper::clientToDomain,
                    balanceAccountMapper::domainToRest
            );
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/positions")
    public ResponseEntity<?> getPositions(
            @RequestParam(required = false) String instType,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) String posId
    ) {
        try {
            OkxApiResponse<OkxPositionDto> response = okxRestClient.getPositions(instType, instId, posId);
            return responseHandler.handleResponse(
                    response,
                    positionMapper::clientToDomain,
                    positionMapper::domainToRest
            );
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }
}
