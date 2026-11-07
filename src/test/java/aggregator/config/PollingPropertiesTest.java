package aggregator.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PollingPropertiesTest {
    @Test
    void storesAllConfigurationValues() {
        PollingProperties properties = new PollingProperties();
        Map<String, List<Map<String, String>>> sources = Map.of("weather", List.of(Map.of("city", "Berlin")));
        properties.setN(5);
        properties.setT(15);
        properties.setFormat("csv");
        properties.setFile("results.csv");
        properties.setAppend(false);
        properties.setSources(sources);
        assertEquals(5, properties.getN());
        assertEquals(15, properties.getT());
        assertEquals("csv", properties.getFormat());
        assertEquals("results.csv", properties.getFile());
        assertFalse(properties.isAppend());
        assertEquals(sources, properties.getSources());
    }
}
