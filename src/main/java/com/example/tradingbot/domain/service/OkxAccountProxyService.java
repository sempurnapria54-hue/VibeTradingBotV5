package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.okxproxy.Balance;
import com.example.tradingbot.domain.model.okxproxy.BalanceRequest;
import com.example.tradingbot.domain.model.okxproxy.Position;
import com.example.tradingbot.domain.model.okxproxy.PositionsRequest;
import com.example.tradingbot.domain.service.okxproxy.OkxAccountClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxAccountProxyService {

    private final OkxAccountClientService okxAccountClientService;

    public List<Balance> getBalance(BalanceRequest request) {
        return okxAccountClientService.getBalance(request);
    }

    public List<Position> getPositions(PositionsRequest request) {
        return okxAccountClientService.getPositions(request);
    }
}
