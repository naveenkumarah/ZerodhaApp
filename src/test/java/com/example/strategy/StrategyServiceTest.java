package com.example.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.strategy.OptionLeg.Side;
import com.example.strategy.OptionLeg.Type;
import com.example.strategy.StrategyRequest.LegInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyServiceTest {

    private final StrategyService service = new StrategyService();

    @Test
    void ironCondorPayoffMetricsMatchHandCalculation() {
        // Spot 22000, lot 50. Credits: 80 + 70; debits: 30 + 25 -> net 95 per unit.
        // Widths 200 -> max loss per unit 105. Breakevens 21705 and 22295.
        var result = service.ironCondor(request(22000, List.of(
                leg(Type.CE, Side.SELL, "22200", "80"),
                leg(Type.CE, Side.BUY, "22400", "30"),
                leg(Type.PE, Side.SELL, "21800", "70"),
                leg(Type.PE, Side.BUY, "21600", "25"))));

        assertThat(result.quantity()).isEqualTo(50);
        assertThat(result.netPremium()).isEqualByComparingTo("4750.00");
        assertThat(result.maxProfit()).isEqualByComparingTo("4750.00");
        assertThat(result.maxLoss()).isEqualByComparingTo("-5250.00");
        assertThat(result.unlimitedProfit()).isFalse();
        assertThat(result.unlimitedLoss()).isFalse();
        assertThat(result.marginRequired()).isEqualByComparingTo("5250.00");
        assertThat(result.breakevens()).hasSize(2);
        assertThat(result.breakevens().get(0).doubleValue()).isCloseTo(21705.0, within(0.01));
        assertThat(result.breakevens().get(1).doubleValue()).isCloseTo(22295.0, within(0.01));
        assertThat(result.probabilityOfProfit().doubleValue()).isBetween(40.0, 60.0);
    }

    @Test
    void bullPutSpreadCreditAndRisk() {
        var result = service.bullPut(request(22000, List.of(
                leg(Type.PE, Side.SELL, "21800", "70"),
                leg(Type.PE, Side.BUY, "21600", "25"))));

        // Credit 45 per unit x 50 = 2250; width 200 -> max loss (200 - 45) x 50 = 7750.
        assertThat(result.maxProfit()).isEqualByComparingTo("2250.00");
        assertThat(result.maxLoss()).isEqualByComparingTo("-7750.00");
        assertThat(result.breakevens()).hasSize(1);
        assertThat(result.breakevens().get(0).doubleValue()).isCloseTo(21755.0, within(0.01));
    }

    @Test
    void ironFlyRequiresSharedAtmStrike() {
        assertThatThrownBy(() -> service.ironFly(request(22000, List.of(
                leg(Type.CE, Side.SELL, "22000", "300"),
                leg(Type.PE, Side.SELL, "21950", "280"),
                leg(Type.CE, Side.BUY, "22200", "60"),
                leg(Type.PE, Side.BUY, "21800", "55")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ATM");
    }

    @Test
    void bearCallRequiresLongCallAboveShort() {
        assertThatThrownBy(() -> service.bearCall(request(22000, List.of(
                leg(Type.CE, Side.SELL, "22200", "80"),
                leg(Type.CE, Side.BUY, "22100", "60")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StrategyRequest request(double spot, List<LegInput> legs) {
        return new StrategyRequest("NIFTY", BigDecimal.valueOf(spot), 50, 1, 7,
                new BigDecimal("15"), new BigDecimal("500000"), legs);
    }

    private static LegInput leg(Type type, Side side, String strike, String premium) {
        return new LegInput(type, side, new BigDecimal(strike), new BigDecimal(premium));
    }
}

