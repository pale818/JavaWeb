package hr.algebra.shop.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class PayPalService {

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    @Value("${paypal.base-url}")
    private String baseUrl;

    @Value("${paypal.return-url}")
    private String returnUrl;

    @Value("${paypal.cancel-url}")
    private String cancelUrl;

    private final RestClient restClient = RestClient.create();

    private String getAccessToken() {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        TokenResponse response = restClient.post()
                .uri(baseUrl + "/v1/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(TokenResponse.class);

        return response != null ? response.accessToken() : "";
    }

    /** Creates a PayPal order and returns the URL the user must visit to approve it. */
    public String createOrder(BigDecimal amount) {
        String token = getAccessToken();
        String value = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();

        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(
                        Map.of("amount", Map.of("currency_code", "EUR", "value", value))
                ),
                "application_context", Map.of(
                        "return_url", returnUrl,
                        "cancel_url", cancelUrl
                )
        );

        OrderResponse response = restClient.post()
                .uri(baseUrl + "/v2/checkout/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OrderResponse.class);

        if (response == null || response.links() == null) {
            throw new IllegalStateException("Invalid PayPal create-order response");
        }

        return response.links().stream()
                .filter(l -> "approve".equals(l.rel()))
                .map(LinkDescription::href)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No PayPal approval URL in response"));
    }

    public void captureOrder(String paypalOrderId) {
        String token = getAccessToken();

        restClient.post()
                .uri(baseUrl + "/v2/checkout/orders/{id}/capture", paypalOrderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderResponse(String id, List<LinkDescription> links) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LinkDescription(String href, String rel) {}
}
