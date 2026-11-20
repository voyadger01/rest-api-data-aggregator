package aggregator;

import aggregator.config.PollingProperties;
import aggregator.source.ApiSource;
import aggregator.storage.DataStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextTest {
    @Autowired
    private DataAggregator aggregator;

    @Autowired
    private PollingProperties properties;

    @Autowired
    @Qualifier("apiSources")
    private Map<String, ApiSource> apiSources;

    @Autowired
    @Qualifier("dataStorages")
    private Map<String, DataStorage> dataStorages;

    @Test
    void discoversApiSourcesAndLoadsAtLeastTwoParameterSetsPerApi() {
        assertTrue(apiSources.size() >= 3);
        assertTrue(aggregator.sourceNames().size() >= 3);
        assertTrue(apiSources.keySet().containsAll(aggregator.sourceNames()));
        assertTrue(apiSources.keySet().containsAll(properties.getSources().keySet()));
        assertTrue(properties.getSources().size() >= 3);
        assertTrue(properties.getN() >= 1);
        assertTrue(properties.getT() >= 1);
        properties.getSources().values().forEach(parameterSets -> assertTrue(parameterSets.size() >= 2));
    }

    @Test
    void createsAllApplicationServicesAndStorageFormats() {
        assertNotNull(aggregator);
        assertTrue(dataStorages.containsKey("json"));
        assertTrue(dataStorages.containsKey("csv"));
    }
}
