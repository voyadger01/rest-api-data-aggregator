package aggregator.source;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DummyJsonCommentsSource implements ApiSource {
    @Override
    public String name() {
        return "comments";
    }

    @Override
    public String baseUrl() {
        return "https://dummyjson.com/comments";
    }

    @Override
    public Map<String, String> defaultParameters() {
        return Map.of(
                "sortBy", "likes",
                "order", "desc"
        );
    }

    @Override
    public JsonNode extractData(JsonNode response) {
        return response;
    }
}
