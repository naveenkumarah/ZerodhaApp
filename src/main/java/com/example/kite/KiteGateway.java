package com.example.kite;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/** Port to Zerodha Kite Connect. Implementations talk to the broker; services depend only on this. */
public interface KiteGateway {

    String loginUrl();

    KiteSession exchangeRequestToken(String requestToken);

    JsonNode margins(String accessToken);

    JsonNode positions(String accessToken);

    JsonNode orders(String accessToken);

    JsonNode ltp(String accessToken, List<String> instruments);

    /** Full quote (last price, OI, volume) keyed by "EXCHANGE:TRADINGSYMBOL". */
    JsonNode quote(String accessToken, List<String> instruments);

    /** Instrument master for an exchange as CSV (GET /instruments/{exchange}). */
    String instruments(String accessToken, String exchange);


    String placeOrder(String accessToken, String variety, Map<String, String> params);

    void modifyOrder(String accessToken, String variety, String orderId, Map<String, String> params);

    void cancelOrder(String accessToken, String variety, String orderId);
}

