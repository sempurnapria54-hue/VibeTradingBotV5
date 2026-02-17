package com.example.tradingbot.rest.controller.trading;

import com.example.tradingbot.domain.model.trading.AlgoOrderCommandResult;
import com.example.tradingbot.domain.model.trading.CancelAlgoOrdersCommand;
import com.example.tradingbot.domain.model.trading.CancelOrderCommand;
import com.example.tradingbot.domain.model.trading.ClosePositionCommand;
import com.example.tradingbot.domain.model.trading.ClosePositionResult;
import com.example.tradingbot.domain.model.trading.CreateAlgoOrderCommand;
import com.example.tradingbot.domain.model.trading.CreateOrderCommand;
import com.example.tradingbot.domain.model.trading.OrderCommandResult;
import com.example.tradingbot.domain.service.trading.AlgoOrderCommandService;
import com.example.tradingbot.domain.service.trading.OrderCommandService;
import com.example.tradingbot.domain.service.trading.PositionCommandService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingCommandController {

    private final OrderCommandService orderCommandService;
    private final AlgoOrderCommandService algoOrderCommandService;
    private final PositionCommandService positionCommandService;

    @PostMapping("/orders")
    public OrderCommandResult createOrder(@RequestBody CreateOrderCommand command) {
        return orderCommandService.createOrder(command);
    }

    @PostMapping("/orders/{internalId}/cancel")
    public OrderCommandResult cancelOrder(@PathVariable String internalId, @RequestBody CancelOrderCommand command) {
        command.setInternalId(internalId);
        return orderCommandService.cancelOrder(command);
    }

    @PostMapping("/algo-orders")
    public AlgoOrderCommandResult createAlgoOrder(@RequestBody CreateAlgoOrderCommand command) {
        return algoOrderCommandService.createAlgoOrder(command);
    }

    @PostMapping("/algo-orders/cancel")
    public List<AlgoOrderCommandResult> cancelAlgoOrders(@RequestBody CancelAlgoOrdersCommand command) {
        return algoOrderCommandService.cancelAlgoOrders(command);
    }

    @PostMapping("/positions/close")
    public ClosePositionResult closePosition(@RequestBody ClosePositionCommand command) {
        return positionCommandService.closePosition(command);
    }
}
