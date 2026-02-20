package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.BalanceRequest;
import com.example.tradingbot.client.okx.dto.BalanceResponse;
import com.example.tradingbot.client.okx.dto.PositionResponse;
import com.example.tradingbot.client.okx.dto.PositionsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxAccountProxyService {

    private final OkxRestClient okxRestClient;

    public List<BalanceResponse> getBalance(BalanceRequest request) {
        return okxRestClient.getBalance(request).getData();
    }

    public List<PositionResponse> getPositions(PositionsRequest request) {
        return okxRestClient.getPositions(request).getData();
    }
}
