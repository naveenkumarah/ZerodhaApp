package com.example.kite;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Instrument;
import com.zerodhatech.models.LTPQuote;
import com.zerodhatech.models.Margin;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import com.zerodhatech.models.Position;
import com.zerodhatech.models.Quote;
import com.zerodhatech.models.User;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.springframework.stereotype.Component;
/**
 * Kite Connect client backed by the com.zerodhatech.kiteconnect 4.0.1 SDK.
 * SDK models are converted back to Kite's JSON field names so the services above keep working unchanged.
 */
@Component
public class KiteConnectGateway implements KiteGateway {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final KiteProperties props;
    public KiteConnectGateway(KiteProperties props) {
        this.props = props;
    }
    @FunctionalInterface
    private interface KiteCall<T> {
        T run(KiteConnect kite) throws KiteException, JSONException, IOException;
    }
    /** Runs one SDK call with the given access token and maps SDK failures to IllegalStateException. */
    private <T> T call(String accessToken, KiteCall<T> action) {
        KiteConnect kite = new KiteConnect(props.apiKey());
        if (accessToken != null) {
            kite.setAccessToken(accessToken);
        }
        try {
            return action.run(kite);
        } catch (KiteException e) {
            throw new IllegalStateException("Kite error: " + e.message + " (code " + e.code + ")", e);
        } catch (JSONException | IOException e) {
            throw new IllegalStateException("Kite request failed: " + e.getMessage(), e);
        }
    }
    @Override
    public String loginUrl() {
        return new KiteConnect(props.apiKey()).getLoginURL();
    }
    @Override
    public KiteSession exchangeRequestToken(String requestToken) {
        User user = call(null, kite -> kite.generateSession(requestToken, props.apiSecret()));
        return new KiteSession(user.userId, user.accessToken, user.refreshToken);
    }
    @Override
    public JsonNode margins(String accessToken) {
        return call(accessToken, kite -> {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, Margin> entry : kite.getMargins().entrySet()) {
                ObjectNode segment = root.putObject(entry.getKey());
                Margin m = entry.getValue();
                if (m.available != null) {
                    ObjectNode available = segment.putObject("available");
                    available.put("cash", num(m.available.cash));
                    available.put("live_balance", num(m.available.liveBalance));
                    available.put("intraday_payin", num(m.available.intradayPayin));
                    available.put("adhoc_margin", num(m.available.adhocMargin));
                    available.put("collateral", num(m.available.collateral));
                }
                if (m.utilised != null) {
                    ObjectNode utilised = segment.putObject("utilised");
                    utilised.put("debits", num(m.utilised.debits));
                    utilised.put("span", num(m.utilised.span));
                    utilised.put("exposure", num(m.utilised.exposure));
                    utilised.put("option_premium", num(m.utilised.optionPremium));
                    utilised.put("holding_sales", num(m.utilised.holdingSales));
                    utilised.put("m2m_realised", num(m.utilised.m2mRealised));
                    utilised.put("m2m_unrealised", num(m.utilised.m2mUnrealised));
                    utilised.put("turnover", num(m.utilised.turnover));
                }
                segment.put("net", num(m.net));
            }
            return root;
        });
    }
    @Override
    public JsonNode positions(String accessToken) {
        return call(accessToken, kite -> {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, List<Position>> entry : kite.getPositions().entrySet()) {
                ArrayNode rows = root.putArray(entry.getKey());
                for (Position p : entry.getValue()) {
                    rows.add(position(p));
                }
            }
            return root;
        });
    }
    @Override
    public JsonNode orders(String accessToken) {
        return call(accessToken, kite -> {
            ArrayNode rows = JsonNodeFactory.instance.arrayNode();
            for (Order o : kite.getOrders()) {
                rows.add(order(o));
            }
            return rows;
        });
    }
    @Override
    public JsonNode ltp(String accessToken, List<String> instruments) {
        return call(accessToken, kite -> {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, LTPQuote> entry : kite.getLTP(instruments.toArray(new String[0])).entrySet()) {
                root.putObject(entry.getKey())
                        .put("instrument_token", entry.getValue().instrumentToken)
                        .put("last_price", entry.getValue().lastPrice);
            }
            return root;
        });
    }
    @Override
    public JsonNode quote(String accessToken, List<String> instruments) {
        return call(accessToken, kite -> {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, Quote> entry : kite.getQuote(instruments.toArray(new String[0])).entrySet()) {
                Quote q = entry.getValue();
                root.putObject(entry.getKey())
                        .put("instrument_token", q.instrumentToken)
                        .put("last_price", q.lastPrice)
                        .put("average_price", q.averagePrice)
                        .put("change", q.change)
                        .put("oi", q.oi)
                        .put("volume", (long) q.volumeTradedToday)
                        .put("buy_quantity", q.buyQuantity)
                        .put("sell_quantity", q.sellQuantity);
            }
            return root;
        });
    }
    @Override
    public String instruments(String accessToken, String exchange) {
        return call(accessToken, kite -> {
            StringBuilder csv = new StringBuilder(
                    "tradingsymbol,name,expiry,strike,lot_size,instrument_type,segment,exchange\n");
            for (Instrument i : kite.getInstruments(exchange)) {
                csv.append(clean(i.tradingsymbol)).append(',')
                        .append(clean(i.name)).append(',')
                        .append(i.expiry == null ? "" : i.expiry.toInstant().atZone(IST).toLocalDate()).append(',')
                        .append(clean(i.strike)).append(',')
                        .append(i.lot_size).append(',')
                        .append(clean(i.instrument_type)).append(',')
                        .append(clean(i.segment)).append(',')
                        .append(clean(i.exchange)).append('\n');
            }
            return csv.toString();
        });
    }
    @Override
    public String placeOrder(String accessToken, String variety, Map<String, String> params) {
        OrderParams orderParams = orderParams(params);
        return call(accessToken, kite -> {
            OrderResponse response = kite.placeOrder(orderParams, variety);
            return response.orderId;
        });
    }
    @Override
    public void modifyOrder(String accessToken, String variety, String orderId, Map<String, String> params) {
        OrderParams orderParams = orderParams(params);
        call(accessToken, kite -> kite.modifyOrder(orderId, orderParams, variety));
    }
    @Override
    public void cancelOrder(String accessToken, String variety, String orderId) {
        call(accessToken, kite -> kite.cancelOrder(orderId, variety));
    }
    private static ObjectNode position(Position p) {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        o.put("exchange", p.exchange);
        o.put("tradingsymbol", p.tradingSymbol);
        o.put("instrument_token", p.instrumentToken);
        o.put("product", p.product);
        o.put("quantity", p.netQuantity);
        o.put("overnight_quantity", p.overnightQuantity);
        o.put("buy_quantity", p.buyQuantity);
        o.put("sell_quantity", p.sellQuantity);
        o.put("average_price", p.averagePrice);
        o.put("last_price", v(p.lastPrice));
        o.put("close_price", v(p.closePrice));
        o.put("pnl", v(p.pnl));
        o.put("m2m", v(p.m2m));
        o.put("unrealised", v(p.unrealised));
        o.put("realised", v(p.realised));
        o.put("value", p.value);
        o.put("multiplier", v(p.multiplier));
        return o;
    }
    private static ObjectNode order(Order o) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("order_id", o.orderId);
        n.put("exchange", o.exchange);
        n.put("tradingsymbol", o.tradingSymbol);
        n.put("transaction_type", o.transactionType);
        n.put("order_type", o.orderType);
        n.put("product", o.product);
        n.put("variety", o.orderVariety);
        n.put("validity", o.validity);
        n.put("quantity", num(o.quantity));
        n.put("filled_quantity", num(o.filledQuantity));
        n.put("pending_quantity", num(o.pendingQuantity));
        n.put("price", num(o.price));
        n.put("trigger_price", num(o.triggerPrice));
        n.put("average_price", num(o.averagePrice));
        n.put("status", o.status);
        n.put("status_message", o.statusMessage);
        n.put("tag", o.tag);
        return n;
    }
    /** Converts the Kite REST form fields used by TradingService into SDK order parameters. */
    private static OrderParams orderParams(Map<String, String> p) {
        OrderParams op = new OrderParams();
        op.tradingsymbol = p.get("tradingsymbol");
        op.exchange = p.get("exchange");
        op.transactionType = p.get("transaction_type");
        if (p.get("quantity") != null) {
            op.quantity = Integer.valueOf(p.get("quantity"));
        }
        op.orderType = p.get("order_type");
        op.product = p.get("product");
        op.validity = p.get("validity");
        if (p.get("price") != null) {
            op.price = Double.valueOf(p.get("price"));
        }
        if (p.get("trigger_price") != null) {
            op.triggerPrice = Double.valueOf(p.get("trigger_price"));
        }
        return op;
    }
    private static double num(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    private static double v(Double d) {
        return d == null ? 0 : d;
    }
    private static String clean(String s) {
        return s == null ? "" : s.replace(',', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}