package aggregator;

import aggregator.http.HttpClientService;
import aggregator.model.ApiRecord;
import aggregator.source.ApiSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DataAggregator {
    private final Map<String, ApiSource> sources;
    private final HttpClientService httpClient;
    private final ObjectMapper mapper;

    public DataAggregator(Map<String, ApiSource> sources, HttpClientService httpClient, ObjectMapper mapper) {
        this.sources = Map.copyOf(sources);
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public List<ApiRecord> collect(List<String> sourceNames, Map<String, Map<String, String>> overrides)
            throws IOException, InterruptedException {
        List<ApiRecord> records = new ArrayList<>();
        for (String sourceName : sourceNames) {
            ApiSource source = sources.get(sourceName.toLowerCase(Locale.ROOT));
            if (source == null) {
                throw new IllegalArgumentException("Неизвестный API: " + sourceName + ". Доступны: " + String.join(", ", sources.keySet()));
            }

            Map<String, String> parameters = new LinkedHashMap<>(source.defaultParameters());
            parameters.putAll(overrides.getOrDefault(source.name(), Map.of()));
            URI uri = URI.create(source.buildUrl(parameters));
            String responseBody = httpClient.get(uri);
            JsonNode response = mapper.readTree(responseBody);
            JsonNode data = source.extractData(response);
            records.add(new ApiRecord(source.name(), Instant.now(), Map.copyOf(parameters), data));
        }
        return records;
    }

    public List<String> sourceNames() {
        return sources.keySet().stream().sorted().toList();
    }
}
