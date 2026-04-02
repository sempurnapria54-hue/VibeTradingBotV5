package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.model.okx.request.get.GetBalancesRequest;
import com.example.tradingbot.client.model.okx.request.get.GetPositionsSearchParams;
import com.example.tradingbot.client.model.okx.response.OkxApiResponse;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.client.model.okx.response.balance.BalanceResponse;
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
    public OkxApiResponse<BalanceResponse> getBalance(GetBalancesRequest request) {
        return okxRestClient.getBalance(request);
    }

    @GetMapping("/positions")
    public OkxApiResponse<PositionResponse> getPositions(GetPositionsSearchParams request) {
        return okxRestClient.getPositions(request);
    }
}
