package aggregator.config;

import aggregator.source.ApiSource;
import aggregator.source.DummyJsonCommentsSource;
import aggregator.source.JsonPlaceholderUsersSource;
import aggregator.source.OpenMeteoSource;
import aggregator.storage.DataStorage;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregatorConfigurationTest {
    private final AggregatorConfiguration configuration = new AggregatorConfiguration();

    @Test
    void registersAllDiscoveredApiSourcesWithoutHardCodedSourceList() {
        ApiSource weather = new OpenMeteoSource();
        ApiSource users = new JsonPlaceholderUsersSource();
        ApiSource comments = new DummyJsonCommentsSource();
        ApiSource additional = new ApiSource() {
            @Override
            public String name() {
                return " EXTRA ".trim();
            }

            @Override
            public String baseUrl() {
                return "https://example.test";
            }

            @Override
            public Map<String, String> defaultParameters() {
                return Map.of();
            }

            @Override
            public com.fasterxml.jackson.databind.JsonNode extractData(com.fasterxml.jackson.databind.JsonNode response) {
                return response;
            }
        };

        Map<String, ApiSource> sources = configuration.apiSources(List.of(weather, users, comments, additional));

        assertEquals(4, sources.size());
        assertTrue(sources.containsKey("weather"));
        assertTrue(sources.containsKey("users"));
        assertTrue(sources.containsKey("comments"));
        assertTrue(sources.containsKey("extra"));
    }

    @Test
    void rejectsDuplicateApiSourceNames() {
        assertThrows(IllegalStateException.class, () -> configuration.apiSources(List.of(
                new OpenMeteoSource(),
                new OpenMeteoSource()
        )));
    }

    @Test
    void rejectsApiSourcesWithBlankNames() {
        ApiSource blank = new ApiSource() {
            @Override
            public String name() {
                return " ";
            }

            @Override
            public String baseUrl() {
                return "https://example.test";
            }

            @Override
            public Map<String, String> defaultParameters() {
                return Map.of();
            }

            @Override
            public com.fasterxml.jackson.databind.JsonNode extractData(com.fasterxml.jackson.databind.JsonNode response) {
                return response;
            }
        };

        assertThrows(IllegalStateException.class, () -> configuration.apiSources(List.of(blank)));
    }

    @Test
    void createsJsonMapperWithIsoDateSupport() throws Exception {
        var mapper = configuration.objectMapper();

        assertFalse(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        assertTrue(mapper.readTree("{\"value\":1}").path("value").isInt());
    }

    @Test
    void createsBothStorageImplementations() {
        Map<String, DataStorage> storages = configuration.dataStorages(configuration.objectMapper());

        assertEquals(2, storages.size());
        assertTrue(storages.containsKey("json"));
        assertTrue(storages.containsKey("csv"));
    }

    @Test
    void createsExecutorWithShutdownPolicies() {
        PollingProperties properties = new PollingProperties();
        properties.setN(4);

        ScheduledThreadPoolExecutor executor = configuration.pollingExecutor(properties);

        assertEquals(4, executor.getCorePoolSize());
        assertTrue(executor.getRemoveOnCancelPolicy());
        assertFalse(executor.getExecuteExistingDelayedTasksAfterShutdownPolicy());
        assertFalse(executor.getContinueExistingPeriodicTasksAfterShutdownPolicy());
        executor.shutdownNow();
    }
}
