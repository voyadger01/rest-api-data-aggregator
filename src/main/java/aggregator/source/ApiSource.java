package aggregator.source;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public interface ApiSource {
    String name();

    String baseUrl();

    Map<String, String> defaultParameters();

    JsonNode extractData(JsonNode response);

    default String buildUrl(Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return baseUrl() + (query.isEmpty() ? "" : "?" + query);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
