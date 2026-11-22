package com.example.common;

import java.util.List;

/** Raised when a discipline/risk rule blocks an action (maps to HTTP 422). */
public class BusinessRuleException extends RuntimeException {

    private final List<String> reasons;

    public BusinessRuleException(List<String> reasons) {
        super(String.join("; ", reasons));
        this.reasons = List.copyOf(reasons);
    }

    public BusinessRuleException(String reason) {
        this(List.of(reason));
    }

    public List<String> getReasons() {
        return reasons;
    }
}

