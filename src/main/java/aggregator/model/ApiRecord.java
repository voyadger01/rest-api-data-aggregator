package aggregator.model;

import java.time.Instant;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

public record ApiRecord(String source, Instant fetchedAt, Map<String, String> parameters, JsonNode node) {}
