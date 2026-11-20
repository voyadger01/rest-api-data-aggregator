package aggregator;

import aggregator.http.HttpClientService;
import aggregator.model.ApiRecord;
import aggregator.source.ApiSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataAggregatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void combinesDefaultAndCustomParametersAndMapsResponse() throws Exception {
        ApiSource source = source("sample", Map.of("limit", "5", "city", "Tirana"), "data");
        HttpClientService client = mock(HttpClientService.class);
        when(client.get(any(URI.class))).thenReturn("{\"data\":{\"items\":[1,2,3]}}");
        DataAggregator aggregator = new DataAggregator(Map.of("sample", source), client, mapper);

        List<ApiRecord> records = aggregator.collect(
                List.of("SAMPLE"),
                Map.of("SAMPLE", Map.of("city", "Paris"))
        );

        assertEquals(1, records.size());
        assertEquals("sample", records.getFirst().source());
        assertEquals("Paris", records.getFirst().parameters().get("city"));
        assertEquals("5", records.getFirst().parameters().get("limit"));
        assertEquals(3, records.getFirst().node().path("items").size());
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(client).get(uriCaptor.capture());
        assertTrue(uriCaptor.getValue().toString().contains("limit=5"));
        assertTrue(uriCaptor.getValue().toString().contains("city=Paris"));
    }

    @Test
    void collectsMultipleSourcesAndSortsSourceNames() throws Exception {
        ApiSource first = source("zeta", Map.of(), "first");
        ApiSource second = source("alpha", Map.of(), "second");
        HttpClientService client = mock(HttpClientService.class);
        when(client.get(any(URI.class))).thenReturn("{\"first\":1}", "{\"second\":2}");
        DataAggregator aggregator = new DataAggregator(Map.of("zeta", first, "alpha", second), client, mapper);

        List<ApiRecord> records = aggregator.collect(List.of("zeta", "alpha"), Map.of());

        assertEquals(List.of("alpha", "zeta"), aggregator.sourceNames());
        assertEquals(List.of("zeta", "alpha"), records.stream().map(ApiRecord::source).toList());
        assertEquals(1, records.getFirst().node().asInt());
        assertEquals(2, records.get(1).node().asInt());
        verify(client, org.mockito.Mockito.times(2)).get(any(URI.class));
    }

    @Test
    void rejectsUnknownSourceBeforeMakingHttpRequest() throws IOException, InterruptedException {
        HttpClientService client = mock(HttpClientService.class);
        DataAggregator aggregator = new DataAggregator(Map.of("sample", source("sample", Map.of(), "data")), client, mapper);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> aggregator.collect(List.of("missing"), Map.of())
        );

        assertTrue(exception.getMessage().contains("missing"));
        verify(client, never()).get(any(URI.class));
    }

    @Test
    void propagatesInvalidJsonFromApiResponse() throws Exception {
        HttpClientService client = mock(HttpClientService.class);
        when(client.get(any(URI.class))).thenReturn("{invalid-json");
        DataAggregator aggregator = new DataAggregator(Map.of("sample", source("sample", Map.of(), "data")), client, mapper);

        assertThrows(IOException.class, () -> aggregator.collect(List.of("sample"), Map.of()));
    }


    @Test
    void usesDefaultParametersWhenOverridesAreNull() throws Exception {
        ApiSource source = source("sample", Map.of("limit", "5"), "data");
        HttpClientService client = mock(HttpClientService.class);
        when(client.get(any(URI.class))).thenReturn("{\"data\":{\"value\":1}}");
        DataAggregator aggregator = new DataAggregator(Map.of("sample", source), client, mapper);

        List<ApiRecord> records = aggregator.collect(List.of("sample"), null);

        assertEquals(Map.of("limit", "5"), records.getFirst().parameters());
        verify(client).get(URI.create("https://example.test/sample?limit=5"));
    }

    @Test
    void rejectsNullSourceList() {
        DataAggregator aggregator = new DataAggregator(
                Map.of("sample", source("sample", Map.of(), "data")),
                mock(HttpClientService.class),
                mapper
        );

        assertThrows(NullPointerException.class, () -> aggregator.collect(null, Map.of()));
    }

    @Test
    void returnsAnEmptyCollectionWhenNoSourcesAreRequested() throws Exception {
        HttpClientService client = mock(HttpClientService.class);
        DataAggregator aggregator = new DataAggregator(Map.of("sample", source("sample", Map.of(), "data")), client, mapper);

        assertTrue(aggregator.collect(List.of(), Map.of()).isEmpty());
        verify(client, never()).get(any(URI.class));
    }

    private ApiSource source(String name, Map<String, String> defaults, String responseField) {
        return new ApiSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String baseUrl() {
                return "https://example.test/" + name;
            }

            @Override
            public Map<String, String> defaultParameters() {
                return new LinkedHashMap<>(defaults);
            }

            @Override
            public com.fasterxml.jackson.databind.JsonNode extractData(com.fasterxml.jackson.databind.JsonNode response) {
                return response.path(responseField);
            }
        };
    }
}
