package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.dto.BalanceRequest;
import com.example.tradingbot.client.okx.dto.BalanceResponse;
import com.example.tradingbot.client.okx.dto.PositionResponse;
import com.example.tradingbot.client.okx.dto.PositionsRequest;
import com.example.tradingbot.domain.service.OkxAccountProxyService;
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
    public List<BalanceResponse> getBalance(BalanceRequest request) {
        return service.getBalance(request);
    }

    @GetMapping("/positions")
    public List<PositionResponse> getPositions(PositionsRequest request) {
        return service.getPositions(request);
    }
}
