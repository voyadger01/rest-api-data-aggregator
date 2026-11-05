package aggregator;

import aggregator.http.HttpClientService;
import aggregator.model.ApiRecord;
import aggregator.source.ApiSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataAggregatorTest {
    @Test
    void combinesDefaultAndCustomParametersAndMapsResponse() throws Exception {
        ApiSource source = new ApiSource() {
            @Override
            public String name() {
                return "sample";
            }

            @Override
            public String baseUrl() {
                return "https://example.test/data";
            }

            @Override
            public Map<String, String> defaultParameters() {
                Map<String, String> parameters = new LinkedHashMap<>();
                parameters.put("limit", "5");
                parameters.put("city", "Tirana");
                return parameters;
            }

            @Override
            public com.fasterxml.jackson.databind.JsonNode extractData(com.fasterxml.jackson.databind.JsonNode response) {
                return response.path("data");
            }
        };
        HttpClientService client = new HttpClientService() {
            @Override
            public String get(URI uri) {
                assertTrue(uri.toString().contains("limit=5"));
                assertTrue(uri.toString().contains("city=Paris"));
                return "{\"data\":{\"items\":[1,2,3]}}";
            }
        };
        DataAggregator aggregator = new DataAggregator(Map.of("sample", source), client, new ObjectMapper());

        List<ApiRecord> records = aggregator.collect(List.of("sample"), Map.of("sample", Map.of("city", "Paris")));

        assertEquals(1, records.size());
        assertEquals("sample", records.get(0).source());
        assertEquals("Paris", records.get(0).parameters().get("city"));
        assertEquals(3, records.get(0).node().path("items").size());
    }
}
