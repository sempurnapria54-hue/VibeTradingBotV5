package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.model.okx.BalanceRequest;
import com.example.tradingbot.client.model.okx.BalanceResponse;
import com.example.tradingbot.client.model.okx.PositionResponse;
import com.example.tradingbot.client.model.okx.PositionsRequest;
import com.example.tradingbot.domain.service.OkxAccountProxyService;
import com.example.tradingbot.rest.model.okxproxy.RestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/okx/v5/account")
@RequiredArgsConstructor
public class OkxAccountProxyController {

    private final OkxAccountProxyService service;

    @GetMapping("/balance")
    public RestResponse<BalanceResponse> getBalance(BalanceRequest request) {
        return success(service.getBalance(request));
    }

    @GetMapping("/positions")
    public RestResponse<PositionResponse> getPositions(PositionsRequest request) {
        return success(service.getPositions(request));
    }

    private <T> RestResponse<T> success(List<T> data) {
        RestResponse<T> response = new RestResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        return response;
    }
}
