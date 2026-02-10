package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.domain.service.OkxAccountProxyService;
import com.example.tradingbot.mapping.okxproxy.BalanceMapper;
import com.example.tradingbot.mapping.okxproxy.OkxProxyRequestMapper;
import com.example.tradingbot.mapping.okxproxy.PositionMapper;
import com.example.tradingbot.rest.model.okxproxy.Balance;
import com.example.tradingbot.rest.model.okxproxy.BalanceRequest;
import com.example.tradingbot.rest.model.okxproxy.PositionsRequest;
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
    private final OkxProxyRequestMapper requestMapper;
    private final BalanceMapper balanceMapper;
    private final PositionMapper positionMapper;

    @GetMapping("/balance")
    public RestResponse<Balance> getBalance(BalanceRequest request) {
        List<Balance> data = service.getBalance(requestMapper.restToDomain(request)).stream().map(balanceMapper::domainToRest).toList();
        return success(data);
    }

    @GetMapping("/positions")
    public RestResponse<com.example.tradingbot.rest.model.okxproxy.Position> getPositions(PositionsRequest request) {
        List<com.example.tradingbot.rest.model.okxproxy.Position> data = service.getPositions(requestMapper.restToDomain(request)).stream()
                .map(positionMapper::domainToRest)
                .toList();
        return success(data);
    }

    private <T> RestResponse<T> success(List<T> data) {
        RestResponse<T> response = new RestResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        return response;
    }
}
