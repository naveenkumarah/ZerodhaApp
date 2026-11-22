package com.example.risk;

import com.example.sizing.PositionSizingService;
import com.example.stoploss.StopLossEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    public record ValidateTradeRequest(
            @NotNull @Valid TradeProposal proposal,
            @NotNull ChecklistAnswers checklist,
            BigDecimal estimatedMargin,
            BigDecimal availableMargin) {}

    public record InitialStopRequest(StopLossEngine.Direction direction, BigDecimal entryPrice,
                                     StopLossEngine.Method method, BigDecimal value, BigDecimal atr) {}

    private final PositionSizingService sizing;
    private final RiskEvaluationService risk;
    private final RiskStateService riskState;
    private final RiskProfileService profiles;
    private final StopLossEngine stopLoss;

    public RiskController(PositionSizingService sizing, RiskEvaluationService risk, RiskStateService riskState,
                          RiskProfileService profiles, StopLossEngine stopLoss) {
        this.sizing = sizing;
        this.risk = risk;
        this.riskState = riskState;
        this.profiles = profiles;
        this.stopLoss = stopLoss;
    }

    @PostMapping("/calculate-position-size")
    public PositionSizingService.Result calculatePositionSize(@AuthenticationPrincipal Long userId,
                                                              @RequestBody PositionSizingService.Request req) {
        return sizing.calculate(req);
    }

    @PostMapping("/validate-trade")
    public RiskEvaluationService.TradeDecision validateTrade(@AuthenticationPrincipal Long userId,
                                                             @Valid @RequestBody ValidateTradeRequest req) {
        return risk.evaluateTrade(
                profiles.getOrCreate(userId),
                riskState.snapshot(userId, Instant.now()),
                req.proposal(),
                req.checklist(),
                req.availableMargin(),
                req.estimatedMargin());
    }

    /** Dashboard state: GREEN safe, YELLOW warning, RED trading locked. */
    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal Long userId) {
        RiskEvaluationService.Assessment a = risk.assess(profiles.getOrCreate(userId),
                riskState.snapshot(userId, Instant.now()));
        return Map.of("status", a.status(), "locks", a.locks(), "warnings", a.warnings());
    }

    @PostMapping("/stop-loss/initial")
    public Map<String, BigDecimal> initialStop(@RequestBody InitialStopRequest req) {
        return Map.of("stopLoss", stopLoss.initialStop(req.direction(), req.entryPrice(), req.method(),
                req.value(), req.atr()));
    }

    @PostMapping("/stop-loss/evaluate")
    public StopLossEngine.Adjustment evaluateStop(@RequestBody StopLossEngine.EvaluateRequest req) {
        return stopLoss.evaluate(req);
    }

    @GetMapping("/profile")
    public RiskProfile profile(@AuthenticationPrincipal Long userId) {
        return profiles.getOrCreate(userId);
    }

    @PutMapping("/profile")
    public RiskProfile updateProfile(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody RiskProfileService.Update req) {
        return profiles.update(userId, req);
    }
}


