package com.example.trading;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading")
public class TradingController {

    private final TradingService trading;

    public TradingController(TradingService trading) {
        this.trading = trading;
    }

    @GetMapping("/margins")
    public Map<String, Object> margins(@AuthenticationPrincipal Long userId) {
        return trading.margins(userId);
    }

    @GetMapping("/positions")
    public JsonNode positions(@AuthenticationPrincipal Long userId) {
        return trading.positions(userId);
    }

    @GetMapping("/orders")
    public JsonNode orders(@AuthenticationPrincipal Long userId) {
        return trading.orders(userId);
    }

    @GetMapping("/ltp")
    public JsonNode ltp(@AuthenticationPrincipal Long userId, @RequestParam List<String> instruments) {
        return trading.ltp(userId, instruments);
    }

    @GetMapping("/option-chain")
    public Map<String, Object> optionChain(@AuthenticationPrincipal Long userId,
                                           @RequestParam String underlying,
                                           @RequestParam(required = false) String expiry) {
        return trading.optionChain(userId, underlying, expiry);
    }

    @PostMapping("/order")
    public Map<String, Object> placeOrder(@AuthenticationPrincipal Long userId,
                                          @Valid @RequestBody TradingService.PlaceOrderRequest req) {
        return trading.placeOrder(userId, req);
    }

    @PutMapping("/order/{variety}/{orderId}")
    public Map<String, String> modifyOrder(@AuthenticationPrincipal Long userId,
                                           @PathVariable String variety, @PathVariable String orderId,
                                           @RequestBody Map<String, String> params) {
        trading.modifyOrder(userId, variety, orderId, params);
        return Map.of("orderId", orderId, "status", "MODIFIED");
    }

    @DeleteMapping("/order/{variety}/{orderId}")
    public Map<String, String> cancelOrder(@AuthenticationPrincipal Long userId,
                                           @PathVariable String variety, @PathVariable String orderId) {
        trading.cancelOrder(userId, variety, orderId);
        return Map.of("orderId", orderId, "status", "CANCELLED");
    }

    @PostMapping("/exit")
    public Map<String, Object> exit(@AuthenticationPrincipal Long userId,
                                    @Valid @RequestBody TradingService.ExitRequest req) {
        return trading.exitPosition(userId, req);
    }
}


