package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.model.okx.request.BalanceRequest;
import com.example.tradingbot.client.model.okx.response.BalanceResponse;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.client.model.okx.request.PositionsRequest;
import com.example.tradingbot.client.model.okx.response.OkxApiResponse;
import com.example.tradingbot.client.service.okx.OkxRestClient;
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
