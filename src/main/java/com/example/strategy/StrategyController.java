package com.example.strategy;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

    private final StrategyService strategies;

    public StrategyController(StrategyService strategies) {
        this.strategies = strategies;
    }

    @PostMapping("/iron-condor")
    public StrategyAnalysis ironCondor(@Valid @RequestBody StrategyRequest req) {
        return strategies.ironCondor(req);
    }

    @PostMapping("/bull-put")
    public StrategyAnalysis bullPut(@Valid @RequestBody StrategyRequest req) {
        return strategies.bullPut(req);
    }

    @PostMapping("/bear-call")
    public StrategyAnalysis bearCall(@Valid @RequestBody StrategyRequest req) {
        return strategies.bearCall(req);
    }

    @PostMapping("/hedged-strangle")
    public StrategyAnalysis hedgedStrangle(@Valid @RequestBody StrategyRequest req) {
        return strategies.hedgedStrangle(req);
    }

    @PostMapping("/iron-fly")
    public StrategyAnalysis ironFly(@Valid @RequestBody StrategyRequest req) {
        return strategies.ironFly(req);
    }
}

