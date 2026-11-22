package com.example.stoploss;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.stoploss.StopLossEngine.Direction;
import com.example.stoploss.StopLossEngine.EvaluateRequest;
import com.example.stoploss.StopLossEngine.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StopLossEngineTest {

    private final StopLossEngine engine = new StopLossEngine();

    @Test
    void initialStopForFixedPoints() {
        BigDecimal stop = engine.initialStop(Direction.LONG, new BigDecimal("250"), Method.FIXED_POINTS,
                new BigDecimal("10"), null);
        assertThat(stop).isEqualByComparingTo("240.00");
    }

    @Test
    void oneRMovesStopToCost() {
        var adj = engine.evaluate(new EvaluateRequest(Direction.LONG, bd("250"), bd("240"), bd("240"),
                bd("260"), null, null));

        assertThat(adj.rMultiple()).isEqualByComparingTo("1.00");
        assertThat(adj.rule()).isEqualTo("MOVE_TO_COST");
        assertThat(adj.stopLoss()).isEqualByComparingTo("250.00");
        assertThat(adj.modified()).isTrue();
        assertThat(adj.exitTriggered()).isFalse();
    }

    @Test
    void twoRLocksOneR() {
        var adj = engine.evaluate(new EvaluateRequest(Direction.LONG, bd("250"), bd("240"), bd("250"),
                bd("270"), null, null));

        assertThat(adj.rule()).isEqualTo("LOCK_1R");
        assertThat(adj.stopLoss()).isEqualByComparingTo("260.00");
    }

    @Test
    void stopNeverLoosens() {
        // Current stop already at 275 (above the 1R lock); price pulled back to 265 (still 1.5R).
        var adj = engine.evaluate(new EvaluateRequest(Direction.LONG, bd("250"), bd("240"), bd("275"),
                bd("265"), null, null));

        assertThat(adj.stopLoss()).isEqualByComparingTo("275.00");
        assertThat(adj.modified()).isFalse();
    }

    @Test
    void priceAtStopTriggersExit() {
        var adj = engine.evaluate(new EvaluateRequest(Direction.LONG, bd("250"), bd("240"), bd("260"),
                bd("255"), null, null));

        assertThat(adj.exitTriggered()).isTrue();
    }

    @Test
    void shortPositionLocksOneRBelowEntry() {
        var adj = engine.evaluate(new EvaluateRequest(Direction.SHORT, bd("250"), bd("260"), bd("260"),
                bd("235"), null, null));

        assertThat(adj.rMultiple()).isEqualByComparingTo("1.50");
        assertThat(adj.rule()).isEqualTo("MOVE_TO_COST");
        assertThat(adj.stopLoss()).isEqualByComparingTo("250.00");
        assertThat(adj.exitTriggered()).isFalse();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}

