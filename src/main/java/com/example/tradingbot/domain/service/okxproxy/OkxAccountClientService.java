package com.example.tradingbot.domain.service.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.domain.model.okxproxy.Balance;
import com.example.tradingbot.domain.model.okxproxy.BalanceRequest;
import com.example.tradingbot.domain.model.okxproxy.Position;
import com.example.tradingbot.domain.model.okxproxy.PositionsRequest;
import com.example.tradingbot.mapping.okxproxy.BalanceMapper;
import com.example.tradingbot.mapping.okxproxy.OkxProxyRequestMapper;
import com.example.tradingbot.mapping.okxproxy.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxAccountClientService {

    private final OkxRestClient okxRestClient;
    private final OkxProxyRequestMapper requestMapper;
    private final BalanceMapper balanceMapper;
    private final PositionMapper positionMapper;

    public List<Balance> getBalance(BalanceRequest request) {
        var balance = okxRestClient.getBalance(balanceMapper.domainToClient(request));
        return balanceMapper.clientToDomain(balance.getData());
    }

    public List<Position> getPositions(PositionsRequest request) {
        return okxRestClient.getPositions(requestMapper.domainToClient(request)).getData().stream().map(positionMapper::clientToDomain).toList();
    }
}
