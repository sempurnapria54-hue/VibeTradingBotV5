package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.model.okx.BalanceRequest;
import com.example.tradingbot.client.model.okx.BalanceResponse;
import com.example.tradingbot.client.model.okx.PositionResponse;
import com.example.tradingbot.client.model.okx.PositionsRequest;
import com.example.tradingbot.client.model.okx.OkxApiResponse;
import com.example.tradingbot.client.okx.OkxRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/okx/v5/account")
@RequiredArgsConstructor
public class OkxAccountProxyController {

    private final OkxRestClient okxRestClient;

    @GetMapping("/balance")
    public OkxApiResponse<BalanceResponse> getBalance(BalanceRequest request) {
        return okxRestClient.getBalance(request);
    }

    @GetMapping("/positions")
    public OkxApiResponse<PositionResponse> getPositions(PositionsRequest request) {
        return okxRestClient.getPositions(request);
    }
}
