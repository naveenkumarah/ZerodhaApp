package com.example.kite;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Kite Connect v3 REST client. See https://kite.trade/docs/connect/v3/ */
@Component
public class KiteConnectGateway implements KiteGateway {

    private final KiteProperties props;
    private final RestClient client;

    public KiteConnectGateway(KiteProperties props, RestClient.Builder builder) {
        this.props = props;
        this.client = builder.baseUrl(props.baseUrl()).defaultHeader("X-Kite-Version", "3").build();
    }

    @Override
    public String loginUrl() {
        return "https://kite.zerodha.com/connect/login?v=3&api_key=" + props.apiKey();
    }

    @Override
    public KiteSession exchangeRequestToken(String requestToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("api_key", props.apiKey());
        form.add("request_token", requestToken);
        form.add("checksum", sha256Hex(props.apiKey() + requestToken + props.apiSecret()));

        JsonNode data = data(client.post()
                .uri("/session/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
        return new KiteSession(
                data.path("user_id").asText(),
                data.path("access_token").asText(),
                data.hasNonNull("refresh_token") ? data.get("refresh_token").asText() : null);
    }

    @Override
    public JsonNode margins(String accessToken) {
        return get(accessToken, "/user/margins");
    }

    @Override
    public JsonNode positions(String accessToken) {
        return get(accessToken, "/portfolio/positions");
    }

    @Override
    public JsonNode orders(String accessToken) {
        return get(accessToken, "/orders");
    }

    @Override
    public JsonNode ltp(String accessToken, List<String> instruments) {
        return data(client.get()
                .uri(uri -> uri.path("/quote/ltp").queryParam("i", instruments).build())
                .header("Authorization", auth(accessToken))
                .retrieve()
                .body(JsonNode.class));
    }

    @Override
    public String placeOrder(String accessToken, String variety, Map<String, String> params) {
        JsonNode data = data(client.post()
                .uri("/orders/{variety}", variety)
                .header("Authorization", auth(accessToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(toForm(params))
                .retrieve()
                .body(JsonNode.class));
        return data.path("order_id").asText();
    }

    @Override
    public void modifyOrder(String accessToken, String variety, String orderId, Map<String, String> params) {
        data(client.put()
                .uri("/orders/{variety}/{orderId}", variety, orderId)
                .header("Authorization", auth(accessToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(toForm(params))
                .retrieve()
                .body(JsonNode.class));
    }

    @Override
    public void cancelOrder(String accessToken, String variety, String orderId) {
        data(client.delete()
                .uri("/orders/{variety}/{orderId}", variety, orderId)
                .header("Authorization", auth(accessToken))
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode get(String accessToken, String path) {
        return data(client.get().uri(path).header("Authorization", auth(accessToken))
                .retrieve().body(JsonNode.class));
    }

    private String auth(String accessToken) {
        return "token " + props.apiKey() + ":" + accessToken;
    }

    private static MultiValueMap<String, String> toForm(Map<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        return form;
    }

    private static JsonNode data(JsonNode body) {
        if (body == null || !"success".equals(body.path("status").asText())) {
            throw new IllegalStateException("Unexpected Kite response: " + body);
        }
        return body.path("data");
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

