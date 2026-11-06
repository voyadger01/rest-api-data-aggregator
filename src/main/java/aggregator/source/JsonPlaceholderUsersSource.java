package aggregator.source;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class JsonPlaceholderUsersSource implements ApiSource {
    @Override
    public String name() {
        return "users";
    }

    @Override
    public String baseUrl() {
        return "https://jsonplaceholder.typicode.com/users";
    }

    @Override
    public Map<String, String> defaultParameters() {
        return Map.of("username", "Bret");
    }

    @Override
    public JsonNode extractData(JsonNode response) {
        return response;
    }
}
