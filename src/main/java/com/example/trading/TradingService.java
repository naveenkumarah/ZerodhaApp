package com.example.trading;

import com.example.auth.UserAccount;
import com.example.auth.UserRepository;
import com.example.common.BusinessRuleException;
import com.example.common.TokenCipher;
import com.example.kite.KiteGateway;
import com.example.margin.MarginCalculator;
import com.example.risk.ChecklistAnswers;
import com.example.risk.RiskEvaluationService;
import com.example.risk.RiskProfile;
import com.example.risk.RiskProfileService;
import com.example.risk.RiskSnapshot;
import com.example.risk.RiskStateService;
import com.example.risk.TradeProposal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class TradingService {

    private static final List<String> ORDER_TYPES = List.of("MARKET", "LIMIT", "SL", "SL-M");
    private static final String VARIETY = "regular";

    public record PlaceOrderRequest(
            @NotBlank String tradingsymbol,
            @NotBlank String exchange,
            @NotNull Trade.Side transactionType,
            @NotNull @Positive Integer quantity,
            @NotBlank String orderType,
            @NotBlank String product,
            @PositiveOrZero BigDecimal price,
            @PositiveOrZero BigDecimal triggerPrice,
            @NotNull @Positive BigDecimal entryPrice,
            BigDecimal stopLoss,
            BigDecimal target,
            @NotBlank String strategy,
            BigDecimal estimatedMargin,
            @NotNull @Valid ChecklistAnswers checklist) {}

    public record ExitRequest(
            @NotBlank String tradingsymbol,
            @NotBlank String exchange,
            @NotBlank String product,
            @NotNull @Positive Integer quantity,
            @NotNull Trade.Side positionSide,
            @NotNull @Positive BigDecimal exitPrice) {}

    private final UserRepository users;
    private final TokenCipher cipher;
    private final KiteGateway kite;
    private final TradeRepository trades;
    private final RiskProfileService profiles;
    private final RiskStateService riskState;
    private final RiskEvaluationService risk;
    private final MarginCalculator margins;

    public TradingService(UserRepository users, TokenCipher cipher, KiteGateway kite, TradeRepository trades,
                          RiskProfileService profiles, RiskStateService riskState,
                          RiskEvaluationService risk, MarginCalculator margins) {
        this.users = users;
        this.cipher = cipher;
        this.kite = kite;
        this.trades = trades;
        this.profiles = profiles;
        this.riskState = riskState;
        this.risk = risk;
        this.margins = margins;
    }

    public Map<String, Object> margins(Long userId) {
        JsonNode data = kite.margins(accessToken(userId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("availableMargin", margins.availableFromKite(data));
        out.put("usedMargin", margins.usedFromKite(data));
        out.put("raw", data);
        return out;
    }

    public JsonNode positions(Long userId) {
        return kite.positions(accessToken(userId));
    }

    public JsonNode orders(Long userId) {
        return kite.orders(accessToken(userId));
    }

    public JsonNode ltp(Long userId, List<String> instruments) {
        return kite.ltp(accessToken(userId), instruments);
    }

    /**
     * Places an order only after the discipline gate passes: risk state, checklist, risk per trade,
     * reward:risk, lot escalation and margin. Blocked orders never reach Zerodha.
     */
    @Transactional
    public Map<String, Object> placeOrder(Long userId, PlaceOrderRequest req) {
        if (!ORDER_TYPES.contains(req.orderType())) {
            throw new IllegalArgumentException("orderType must be one of " + ORDER_TYPES);
        }
        String token = accessToken(userId);
        RiskProfile profile = profiles.getOrCreate(userId);
        RiskSnapshot snapshot = riskState.snapshot(userId, Instant.now());

        BigDecimal available = margins.availableFromKite(kite.margins(token));
        TradeProposal proposal = new TradeProposal(req.tradingsymbol(), req.quantity(), req.entryPrice(),
                req.stopLoss(), req.target());
        RiskEvaluationService.TradeDecision decision = risk.evaluateTrade(
                profile, snapshot, proposal, req.checklist(), available, req.estimatedMargin());
        if (!decision.allowed()) {
            throw new BusinessRuleException(decision.blockers());
        }

        String orderId = kite.placeOrder(token, VARIETY, orderParams(req));

        Trade trade = new Trade();
        trade.setUserId(userId);
        trade.setSymbol(req.tradingsymbol());
        trade.setEntryPrice(req.entryPrice());
        trade.setQty(req.quantity());
        trade.setStopLoss(req.stopLoss());
        trade.setTarget(req.target());
        trade.setTradeType(req.transactionType());
        trade.setStrategy(req.strategy());
        trade.setStatus(Trade.Status.OPEN);
        trade.setCreatedAt(Instant.now());
        trades.save(trade);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", orderId);
        out.put("tradeId", trade.getId());
        out.put("riskStatus", decision.status().name());
        out.put("warnings", decision.warnings());
        return out;
    }

    public Map<String, Object> exitPosition(Long userId, ExitRequest req) {
        String token = accessToken(userId);
        Trade.Side exitSide = req.positionSide() == Trade.Side.BUY ? Trade.Side.SELL : Trade.Side.BUY;

        Map<String, String> params = new LinkedHashMap<>();
        params.put("tradingsymbol", req.tradingsymbol());
        params.put("exchange", req.exchange());
        params.put("transaction_type", exitSide.name());
        params.put("quantity", String.valueOf(req.quantity()));
        params.put("order_type", "MARKET");
        params.put("product", req.product());
        params.put("validity", "DAY");
        String orderId = kite.placeOrder(token, VARIETY, params);

        Instant now = Instant.now();
        List<Trade> open = trades.findByUserIdAndSymbolAndStatus(userId, req.tradingsymbol(), Trade.Status.OPEN)
                .stream().filter(t -> t.getTradeType() == req.positionSide()).toList();
        BigDecimal realised = BigDecimal.ZERO;
        int direction = req.positionSide() == Trade.Side.BUY ? 1 : -1;
        for (Trade t : open) {
            BigDecimal pnl = req.exitPrice().subtract(t.getEntryPrice())
                    .multiply(BigDecimal.valueOf((long) direction * t.getQty()))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            t.setStatus(Trade.Status.CLOSED);
            t.setPnl(pnl);
            t.setClosedAt(now);
            realised = realised.add(pnl);
        }
        trades.saveAll(open);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", orderId);
        out.put("closedTrades", open.size());
        out.put("realisedPnl", realised);
        return out;
    }

    public void modifyOrder(Long userId, String variety, String orderId, Map<String, String> params) {
        kite.modifyOrder(accessToken(userId), variety, orderId, params);
    }

    public void cancelOrder(Long userId, String variety, String orderId) {
        kite.cancelOrder(accessToken(userId), variety, orderId);
    }

    /** One F&O contract from the NFO instrument master. */
    public record Contract(String tradingsymbol, String name, LocalDate expiry, BigDecimal strike,
                           String type, int lotSize) {}

    private static final Map<String, String> SPOT_SYMBOL = Map.of(
            "NIFTY", "NIFTY 50", "BANKNIFTY", "NIFTY BANK",
            "FINNIFTY", "NIFTY FIN SERVICE", "MIDCPNIFTY", "NIFTY MID SELECT");
    private static final int STRIKES_EACH_SIDE = 10;

    // The NFO instrument CSV is large, so it is parsed once and reused for an hour.
    private volatile List<Contract> nfoCache = List.of();
    private volatile Instant nfoCachedAt = Instant.EPOCH;

    /**
     * Option chain for an index or stock: the nearest (or requested) expiry, and the strikes within
     * {@value #STRIKES_EACH_SIDE} of the ATM strike, each with CE and PE LTP, OI and volume.
     */
    public Map<String, Object> optionChain(Long userId, String underlying, String expiryParam) {
        String token = accessToken(userId);
        String name = underlying.trim().toUpperCase(Locale.ROOT);
        List<Contract> contracts = nfoContracts(token).stream().filter(c -> c.name().equals(name)).toList();
        if (contracts.isEmpty()) {
            throw new BusinessRuleException("No F&O contracts found for " + name);
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<LocalDate> expiries = contracts.stream().map(Contract::expiry)
                .filter(d -> !d.isBefore(today)).distinct().sorted().toList();
        if (expiries.isEmpty()) {
            throw new BusinessRuleException("No unexpired contracts for " + name);
        }
        LocalDate expiry = (expiryParam == null || expiryParam.isBlank())
                ? expiries.get(0) : LocalDate.parse(expiryParam);
        if (!expiries.contains(expiry)) {
            throw new BusinessRuleException("Expiry " + expiry + " is not available for " + name);
        }
        List<Contract> chain = contracts.stream().filter(c -> c.expiry().equals(expiry)).toList();

        String spotKey = "NSE:" + SPOT_SYMBOL.getOrDefault(name, name);
        double spot = kite.quote(token, List.of(spotKey)).path(spotKey).path("last_price").asDouble(0);

        List<BigDecimal> strikes = chain.stream().map(Contract::strike).distinct().sorted().toList();
        int atm = 0;
        for (int i = 1; i < strikes.size(); i++) {
            if (Math.abs(strikes.get(i).doubleValue() - spot) < Math.abs(strikes.get(atm).doubleValue() - spot)) {
                atm = i;
            }
        }
        int from = Math.max(0, atm - STRIKES_EACH_SIDE);
        int to = Math.min(strikes.size(), atm + STRIKES_EACH_SIDE + 1);
        List<BigDecimal> window = strikes.subList(from, to);

        Map<BigDecimal, Map<String, Contract>> byStrike = new HashMap<>();
        for (Contract c : chain) {
            if (window.contains(c.strike())) {
                byStrike.computeIfAbsent(c.strike(), k -> new HashMap<>()).put(c.type(), c);
            }
        }
        List<String> keys = byStrike.values().stream().flatMap(m -> m.values().stream())
                .map(c -> "NFO:" + c.tradingsymbol()).toList();
        JsonNode quotes = kite.quote(token, keys);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal atmStrike = strikes.get(atm);
        for (BigDecimal strike : window) {
            Map<String, Contract> legs = byStrike.getOrDefault(strike, Map.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("strike", strike);
            row.put("atm", strike.compareTo(atmStrike) == 0);
            row.put("ce", leg(legs.get("CE"), quotes));
            row.put("pe", leg(legs.get("PE"), quotes));
            rows.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("underlying", name);
        out.put("spot", spot);
        out.put("expiry", expiry.toString());
        out.put("expiries", expiries.stream().map(LocalDate::toString).toList());
        out.put("lotSize", chain.get(0).lotSize());
        out.put("atmStrike", atmStrike);
        out.put("rows", rows);
        return out;
    }

    private Map<String, Object> leg(Contract c, JsonNode quotes) {
        if (c == null) {
            return null;
        }
        JsonNode q = quotes.path("NFO:" + c.tradingsymbol());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tradingsymbol", c.tradingsymbol());
        out.put("ltp", q.path("last_price").asDouble(0));
        out.put("oi", q.path("oi").asLong(0));
        out.put("volume", q.path("volume").asLong(0));
        return out;
    }

    private List<Contract> nfoContracts(String token) {
        if (!nfoCache.isEmpty() && Instant.now().isBefore(nfoCachedAt.plus(Duration.ofHours(1)))) {
            return nfoCache;
        }
        List<Contract> parsed = parseContracts(kite.instruments(token, "NFO"));
        nfoCache = parsed;
        nfoCachedAt = Instant.now();
        return parsed;
    }

    static List<Contract> parseContracts(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        String[] lines = csv.split("\\r?\\n");
        Map<String, Integer> col = new HashMap<>();
        String[] header = lines[0].split(",");
        for (int i = 0; i < header.length; i++) {
            col.put(header[i].trim(), i);
        }
        int iSymbol = col.get("tradingsymbol");
        int iName = col.get("name");
        int iExpiry = col.get("expiry");
        int iStrike = col.get("strike");
        int iLot = col.get("lot_size");
        int iType = col.get("instrument_type");

        List<Contract> out = new ArrayList<>();
        for (int r = 1; r < lines.length; r++) {
            String[] f = lines[r].split(",", -1);
            if (f.length <= iType) {
                continue;
            }
            String type = f[iType].trim();
            if ((!"CE".equals(type) && !"PE".equals(type)) || f[iExpiry].isBlank()) {
                continue;
            }
            out.add(new Contract(
                    f[iSymbol].trim(),
                    f[iName].replace("\"", "").trim(),
                    LocalDate.parse(f[iExpiry].trim()),
                    new BigDecimal(f[iStrike].trim()).stripTrailingZeros(),
                    type,
                    Integer.parseInt(f[iLot].trim())));
        }
        return out;
    }

    private Map<String, String> orderParams(PlaceOrderRequest req) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("tradingsymbol", req.tradingsymbol());
        p.put("exchange", req.exchange());
        p.put("transaction_type", req.transactionType().name());
        p.put("quantity", String.valueOf(req.quantity()));
        p.put("order_type", req.orderType());
        p.put("product", req.product());
        p.put("validity", "DAY");
        if (req.price() != null) {
            p.put("price", req.price().toPlainString());
        }
        if (req.triggerPrice() != null) {
            p.put("trigger_price", req.triggerPrice().toPlainString());
        }
        return p;
    }

    private String accessToken(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (user.getAccessToken() == null) {
            throw new BusinessRuleException("Zerodha session not connected. Complete Kite login first.");
        }
        return cipher.decrypt(user.getAccessToken());
    }
}

