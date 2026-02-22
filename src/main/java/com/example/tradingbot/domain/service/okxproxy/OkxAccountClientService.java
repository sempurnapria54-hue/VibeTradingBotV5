package com.example.tradingbot.domain.service.okxproxy;

import com.example.tradingbot.client.model.okx.BalanceRequest;
import com.example.tradingbot.client.model.okx.PositionsRequest;
import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.domain.model.exchange.ExchangeBalance;
import com.example.tradingbot.domain.model.exchange.ExchangePosition;
import com.example.tradingbot.mapping.BalanceMapper;
import com.example.tradingbot.mapping.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxAccountClientService {

    private final OkxRestClient okxRestClient;
    private final BalanceMapper balanceMapper;
    private final PositionMapper positionMapper;

    public List<ExchangeBalance> getBalance(BalanceRequest request) {
        var balance = okxRestClient.getBalance(request);
        return balanceMapper.clientToDomain(balance.getData());
    }

    public List<ExchangePosition> getPositions(PositionsRequest request) {
        return okxRestClient.getPositions(request).getData().stream().map(positionMapper::clientToDomain).toList();
    }
}
