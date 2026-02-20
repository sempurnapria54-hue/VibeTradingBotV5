package com.example.tradingbot.rest.controller.trading;

import com.example.tradingbot.domain.service.trading.PositionCommandService;
import com.example.tradingbot.rest.model.request.trading.ClosePositionCommandRequest;
import com.example.tradingbot.rest.model.response.trading.ClosePositionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingCommandController {

    private final PositionCommandService positionCommandService;

    @PostMapping("/positions/close")
    public ClosePositionResponse closePosition(@RequestBody ClosePositionCommandRequest command) {
        return positionCommandService.closePosition(command);
    }
}
