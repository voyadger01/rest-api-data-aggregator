package aggregator.polling;

import aggregator.DataAggregator;
import aggregator.config.PollingProperties;
import aggregator.model.ApiRecord;
import aggregator.storage.DataStorage;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PollingSchedulerTest {
    @Test
    void startsTasksAndStopsWithoutLosingRunningWork() throws Exception {
        DataAggregator aggregator = mock(DataAggregator.class);
        when(aggregator.sourceNames()).thenReturn(List.of("weather"));
        DataStorage storage = mock(DataStorage.class);
        PollingProperties properties = properties();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);
        CountDownLatch saved = new CountDownLatch(2);
        when(aggregator.collect(anyList(), anyMap())).thenAnswer(invocation -> List.of(
                new ApiRecord("weather", Instant.now(), Map.of(), TextNode.valueOf("ok"))));
        doAnswer(invocation -> { saved.countDown(); return null; }).when(storage).save(anyList(), any(Path.class), anyBoolean());
        PollingScheduler scheduler = new PollingScheduler(aggregator, Map.of("json", storage), properties, executor);
        try {
            scheduler.start(new PollingStartRequest(2, 60L, "json", "build/test-results.json", false,
                    Map.of("weather", List.of(Map.of("latitude", "1"), Map.of("latitude", "2")))));
            assertTrue(saved.await(3, TimeUnit.SECONDS));
            assertTrue(scheduler.status().running());
            scheduler.stop();
            assertFalse(scheduler.status().running());
            verify(storage, times(2)).save(anyList(), any(Path.class), anyBoolean());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsSecondStart() {
        DataAggregator aggregator = mock(DataAggregator.class);
        when(aggregator.sourceNames()).thenReturn(List.of("weather"));
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
        PollingScheduler scheduler = new PollingScheduler(aggregator, Map.of("json", storage), properties(), executor);
        PollingStartRequest request = new PollingStartRequest(null, null, null, null, null, null);
        try {
            scheduler.start(request);
            assertThrows(IllegalStateException.class, () -> scheduler.start(request));
            scheduler.stop();
        } finally { executor.shutdownNow(); }
    }

    @Test
    void validatesInvalidSettings() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
        PollingScheduler scheduler = new PollingScheduler(mock(DataAggregator.class), Map.of("json", mock(DataStorage.class)), properties(), executor);
        try {
            assertThrows(IllegalArgumentException.class, () -> scheduler.start(
                    new PollingStartRequest(0, 1L, "json", "x.json", true, null)));
        } finally { executor.shutdownNow(); }
    }

    private PollingProperties properties() {
        PollingProperties properties = new PollingProperties();
        properties.setN(2);
        properties.setT(60);
        properties.setFormat("json");
        properties.setFile("build/default.json");
        properties.setAppend(false);
        properties.setSources(Map.of("weather", List.of(Map.of("latitude", "1"), Map.of("latitude", "2"))));
        return properties;
    }
}
