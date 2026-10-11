package source;

import com.fasterxml.jackson.databind.JsonNode;

public interface ApiSource {
    String name();
    JsonNode extractData(JsonNode response);
}
