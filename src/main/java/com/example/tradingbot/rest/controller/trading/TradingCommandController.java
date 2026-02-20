package com.example.tradingbot.rest.controller.trading;

import com.example.tradingbot.domain.model.trading.AlgoOrderCommandResult;
import com.example.tradingbot.domain.model.trading.CancelAlgoOrdersCommand;
import com.example.tradingbot.domain.model.trading.ClosePositionCommand;
import com.example.tradingbot.domain.model.trading.ClosePositionResult;
import com.example.tradingbot.domain.model.trading.CreateAlgoOrderRequest;
import com.example.tradingbot.domain.service.trading.AlgoOrderService;
import com.example.tradingbot.domain.service.trading.OrderService;
import com.example.tradingbot.domain.service.trading.PositionCommandService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingCommandController {

    private final OrderService orderService;
    private final AlgoOrderService algoOrderService;
    private final PositionCommandService positionCommandService;

    @PostMapping("/positions/close")
    public ClosePositionResult closePosition(@RequestBody ClosePositionCommand command) {
        return positionCommandService.closePosition(command);
    }
}
