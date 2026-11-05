package aggregator.source;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public class GitHubCommitsSource implements ApiSource {
    @Override
    public String name() {
        return "github";
    }

    @Override
    public String baseUrl() {
        return "https://api.github.com/repos/torvalds/linux/commits";
    }

    @Override
    public Map<String, String> defaultParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("per_page", "5");
        parameters.put("page", "1");
        return parameters;
    }

    @Override
    public JsonNode extractData(JsonNode response) {
        return response;
    }
}
