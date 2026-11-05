package aggregator.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

public record ApiRecord(String source, Instant fetchedAt, Map<String, String> parameters, JsonNode node) {}
