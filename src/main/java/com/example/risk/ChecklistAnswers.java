package com.example.risk;

import java.util.ArrayList;
import java.util.List;

/** Mandatory pre-trade checklist. Every item must be answered "yes" before an order can be placed. */
public record ChecklistAnswers(
        boolean trendConfirmed,
        boolean supportResistanceMarked,
        boolean riskRewardAboveTwo,
        boolean stopLossDefined,
        boolean positionSizeValid) {

    public List<String> missingItems() {
        List<String> missing = new ArrayList<>();
        if (!trendConfirmed) missing.add("Trend not confirmed");
        if (!supportResistanceMarked) missing.add("Support/resistance not marked");
        if (!riskRewardAboveTwo) missing.add("Risk:reward not above 1:2");
        if (!stopLossDefined) missing.add("Stop loss not defined");
        if (!positionSizeValid) missing.add("Position size not validated");
        return missing;
    }
}

