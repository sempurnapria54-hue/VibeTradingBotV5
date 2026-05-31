package com.example.tradingbot.client.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

import static com.example.tradingbot.util.Constant.ErrorCode.CLIENT_SERVICE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ClientManager {

    private final Set<ClientService> clientServices;

    public ClientService getClientService(String exchangeName) {
        return clientServices.stream()
                .filter(exchange -> Objects.equals(exchange.getName(), exchangeName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(CLIENT_SERVICE_NOT_FOUND));
    }

}
