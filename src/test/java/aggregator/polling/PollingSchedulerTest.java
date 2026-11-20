package aggregator.polling;

import aggregator.DataAggregator;
import aggregator.config.PollingProperties;
import aggregator.model.ApiRecord;
import aggregator.storage.DataStorage;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollingSchedulerTest {
    @Test
    void startsConfiguredTasksAndStopsWithoutLosingRunningWork() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);
        CountDownLatch saved = new CountDownLatch(2);
        when(aggregator.collect(anyList(), anyMap())).thenReturn(List.of(record("weather")));
        doAnswer(invocation -> {
            saved.countDown();
            return null;
        }).when(storage).save(anyList(), any(Path.class), anyBoolean());
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);

        try {
            scheduler.start(new PollingStartRequest(
                    2,
                    60L,
                    "json",
                    "build/test-results.json",
                    false,
                    Map.of("weather", List.of(Map.of("latitude", "1"), Map.of("latitude", "2")))
            ));

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
    void rejectsSecondStartUntilFirstRunHasStopped() {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);
        PollingStartRequest request = new PollingStartRequest(null, null, null, null, null, null);

        try {
            scheduler.start(request);

            assertThrows(IllegalStateException.class, () -> scheduler.start(request));

            scheduler.stop();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void validatesEveryPollingSettingAndConfiguredSource() {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);
        List<PollingStartRequest> invalidRequests = List.of(
                new PollingStartRequest(0, 1L, "json", "x.json", true, null),
                new PollingStartRequest(1, 0L, "json", "x.json", true, null),
                new PollingStartRequest(1, 1L, "xml", "x.xml", true, null),
                new PollingStartRequest(1, 1L, "json", " ", true, null),
                new PollingStartRequest(1, 1L, "json", "bad" + Character.MIN_VALUE + "path.json", true, null),
                new PollingStartRequest(1, 1L, "json", "x.json", true,
                        Map.of("missing", List.of(Map.of("q", "v")))),
                new PollingStartRequest(1, 1L, "json", "x.json", true,
                        Map.of("weather", List.of())),
                new PollingStartRequest(1, 1L, "json", "x.json", true,
                        Map.of("weather", Collections.singletonList(null))),
                new PollingStartRequest(1, 1L, "json", "x.json", true,
                        Map.of("weather", List.of(Map.of("", "v")))),
                new PollingStartRequest(1, 1L, "json", "x.json", true,
                        Map.of("weather", List.of(Map.of("city", " ")))),
                new PollingStartRequest(1, 1L, "json", "x.json", true,
                        Map.of("weather", List.of(nullValueParameter())))
        );

        try {
            for (PollingStartRequest request : invalidRequests) {
                assertThrows(IllegalArgumentException.class, () -> scheduler.start(request));
                assertFalse(scheduler.status().running());
            }
        } finally {
            executor.shutdownNow();
        }
    }


    @Test
    void rejectsAnEmptySourceConfiguration() {
        PollingProperties properties = properties();
        properties.setSources(Map.of());
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        PollingScheduler scheduler = scheduler(mockAggregator(), mock(DataStorage.class), properties, executor);

        try {
            assertThrows(IllegalArgumentException.class, () -> scheduler.start(
                    new PollingStartRequest(null, null, null, null, null, null)
            ));
            assertFalse(scheduler.status().running());
        } finally {
            executor.shutdownNow();
        }
    }


    @Test
    void replacesOnlySourcesExplicitlyOverriddenByTheStartRequest() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(3);
        CountDownLatch collected = new CountDownLatch(3);
        List<String> collectedSources = new CopyOnWriteArrayList<>();
        List<Map<String, Map<String, String>>> collectedParameters = new CopyOnWriteArrayList<>();
        when(aggregator.collect(anyList(), anyMap())).thenAnswer(invocation -> {
            List<String> sourceNames = invocation.getArgument(0);
            collectedSources.add(sourceNames.getFirst());
            collectedParameters.add(invocation.getArgument(1));
            collected.countDown();
            return List.of(record(sourceNames.getFirst()));
        });
        PollingProperties properties = properties();
        properties.setN(3);
        properties.setSources(Map.of(
                "weather", List.of(Map.of("city", "Tirana"), Map.of("city", "Paris")),
                "users", List.of(Map.of("username", "Bret"), Map.of("username", "Antonette"))
        ));
        PollingScheduler scheduler = scheduler(aggregator, storage, properties, executor);

        try {
            scheduler.start(new PollingStartRequest(
                    3,
                    60L,
                    "json",
                    "build/overrides.json",
                    true,
                    Map.of("weather", List.of(Map.of("city", "Berlin")))
            ));

            assertTrue(collected.await(3, TimeUnit.SECONDS));
            scheduler.stop();

            assertEquals(1, Collections.frequency(collectedSources, "weather"));
            assertEquals(2, Collections.frequency(collectedSources, "users"));
            assertTrue(collectedParameters.contains(Map.of("weather", Map.of("city", "Berlin"))));
            assertTrue(collectedParameters.contains(Map.of("users", Map.of("username", "Bret"))));
            assertTrue(collectedParameters.contains(Map.of("users", Map.of("username", "Antonette"))));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void respectsConcurrencyLimitAndUsesAppendOnlyAfterFirstSuccessfulWrite() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);
        CountDownLatch firstWaveStarted = new CountDownLatch(2);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        CountDownLatch allTasksCompleted = new CountDownLatch(5);
        CountDownLatch allRecordsSaved = new CountDownLatch(5);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        List<Boolean> appendValues = Collections.synchronizedList(new ArrayList<>());
        when(aggregator.collect(anyList(), anyMap())).thenAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            firstWaveStarted.countDown();
            try {
                assertTrue(releaseTasks.await(5, TimeUnit.SECONDS));
                return List.of(record("weather"));
            } finally {
                active.decrementAndGet();
                allTasksCompleted.countDown();
            }
        });
        doAnswer(invocation -> {
            appendValues.add(invocation.getArgument(2));
            allRecordsSaved.countDown();
            return null;
        }).when(storage).save(anyList(), any(Path.class), anyBoolean());
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);

        try {
            scheduler.start(new PollingStartRequest(
                    2,
                    60L,
                    "json",
                    "build/concurrency.json",
                    false,
                    Map.of("weather", List.of(
                            Map.of("city", "A"),
                            Map.of("city", "B"),
                            Map.of("city", "C"),
                            Map.of("city", "D"),
                            Map.of("city", "E")
                    ))
            ));

            assertTrue(firstWaveStarted.await(3, TimeUnit.SECONDS));
            assertEquals(2, maximumActive.get());
            releaseTasks.countDown();
            assertTrue(allTasksCompleted.await(3, TimeUnit.SECONDS));
            assertTrue(allRecordsSaved.await(3, TimeUnit.SECONDS));
            scheduler.stop();

            assertEquals(5, appendValues.size());
            assertFalse(appendValues.getFirst());
            assertEquals(4, appendValues.stream().filter(Boolean::booleanValue).count());
            assertEquals(2, maximumActive.get());
        } finally {
            releaseTasks.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void repeatsEachTaskOnlyAfterItsConfiguredFixedDelay() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        CountDownLatch twoRuns = new CountDownLatch(2);
        List<Long> starts = Collections.synchronizedList(new ArrayList<>());
        when(aggregator.collect(anyList(), anyMap())).thenAnswer(invocation -> {
            starts.add(System.nanoTime());
            twoRuns.countDown();
            return List.of(record("weather"));
        });
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);

        try {
            scheduler.start(new PollingStartRequest(
                    1,
                    1L,
                    "json",
                    "build/fixed-delay.json",
                    true,
                    Map.of("weather", List.of(Map.of("city", "Berlin")))
            ));

            assertTrue(twoRuns.await(4, TimeUnit.SECONDS));
            scheduler.stop();

            assertEquals(2, starts.size());
            assertTrue(starts.get(1) - starts.get(0) >= TimeUnit.SECONDS.toNanos(1));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stopWaitsForAlreadyRunningTasksAndBlocksAConcurrentRestart() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        CountDownLatch stopStarted = new CountDownLatch(1);
        CountDownLatch stopFinished = new CountDownLatch(1);
        when(aggregator.collect(anyList(), anyMap())).thenAnswer(invocation -> {
            taskStarted.countDown();
            assertTrue(releaseTask.await(5, TimeUnit.SECONDS));
            return List.of(record("weather"));
        });
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);
        Thread stopThread = null;

        try {
            scheduler.start(new PollingStartRequest(
                    1,
                    60L,
                    "json",
                    "build/stop.json",
                    true,
                    Map.of("weather", List.of(Map.of("city", "Berlin")))
            ));
            assertTrue(taskStarted.await(3, TimeUnit.SECONDS));
            stopThread = new Thread(() -> {
                stopStarted.countDown();
                scheduler.stop();
                stopFinished.countDown();
            });
            stopThread.start();
            assertTrue(stopStarted.await(1, TimeUnit.SECONDS));
            assertFalse(stopFinished.await(100, TimeUnit.MILLISECONDS));
            assertThrows(IllegalStateException.class, () -> scheduler.start(
                    new PollingStartRequest(null, null, null, null, null, null)
            ));
            releaseTask.countDown();
            assertTrue(stopFinished.await(3, TimeUnit.SECONDS));
            assertFalse(scheduler.status().running());
        } finally {
            releaseTask.countDown();
            if (stopThread != null) {
                stopThread.join(3000);
            }
            executor.shutdownNow();
        }
    }

    @Test
    void continuesSchedulingAfterAnApiFailure() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch successfulSave = new CountDownLatch(1);
        when(aggregator.collect(anyList(), anyMap())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("temporary failure");
            }
            return List.of(record("weather"));
        });
        doAnswer(invocation -> {
            successfulSave.countDown();
            return null;
        }).when(storage).save(anyList(), any(Path.class), anyBoolean());
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);

        try {
            scheduler.start(new PollingStartRequest(
                    1,
                    1L,
                    "json",
                    "build/retry.json",
                    true,
                    Map.of("weather", List.of(Map.of("city", "Berlin")))
            ));

            assertTrue(successfulSave.await(4, TimeUnit.SECONDS));
            scheduler.stop();
            assertTrue(attempts.get() >= 2);
            verify(storage).save(anyList(), any(Path.class), anyBoolean());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retriesFileWritesWithoutSwitchingToAppendBeforeTheFirstSuccessfulWrite() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch successfulSave = new CountDownLatch(1);
        List<Boolean> appendValues = new CopyOnWriteArrayList<>();
        when(aggregator.collect(anyList(), anyMap())).thenReturn(List.of(record("weather")));
        doAnswer(invocation -> {
            appendValues.add(invocation.getArgument(2));
            if (attempts.getAndIncrement() == 0) {
                throw new IOException("disk unavailable");
            }
            successfulSave.countDown();
            return null;
        }).when(storage).save(anyList(), any(Path.class), anyBoolean());
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);

        try {
            scheduler.start(new PollingStartRequest(
                    1,
                    1L,
                    "json",
                    "build/write-retry.json",
                    false,
                    Map.of("weather", List.of(Map.of("city", "Berlin")))
            ));

            assertTrue(successfulSave.await(4, TimeUnit.SECONDS));
            scheduler.stop();

            assertEquals(List.of(false, false), appendValues);
            verify(storage, times(2)).save(anyList(), any(Path.class), anyBoolean());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resetsStateWhenExecutorRejectsScheduling() {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.shutdown();
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);

        assertThrows(RejectedExecutionException.class, () -> scheduler.start(
                new PollingStartRequest(null, null, null, null, null, null)
        ));
        assertFalse(scheduler.status().running());
        assertTrue(scheduler.status().sources().isEmpty());
        assertEquals(0, scheduler.status().tasks());
    }

    @Test
    void readsAllRecordsOrFiltersBySource() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        List<ApiRecord> records = List.of(record("weather"), record("users"));
        when(storage.load(Path.of("build/default.json"))).thenReturn(records);
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);

        try {
            assertEquals(records, scheduler.readData(null));
            assertEquals(List.of(records.getFirst()), scheduler.readData("WEATHER"));
            assertEquals(records, scheduler.readData(" "));
            verify(storage, times(3)).load(Path.of("build/default.json"));
        } finally {
            executor.shutdownNow();
        }
    }


    @Test
    void shutsDownRunningPollingAndIsIdempotentWhenCalledAgain() throws Exception {
        DataAggregator aggregator = mockAggregator();
        DataStorage storage = mock(DataStorage.class);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        CountDownLatch saved = new CountDownLatch(1);
        when(aggregator.collect(anyList(), anyMap())).thenReturn(List.of(record("weather")));
        doAnswer(invocation -> {
            saved.countDown();
            return null;
        }).when(storage).save(anyList(), any(Path.class), anyBoolean());
        PollingScheduler scheduler = scheduler(aggregator, storage, properties(), executor);

        try {
            scheduler.start(new PollingStartRequest(
                    1,
                    60L,
                    "json",
                    "build/shutdown.json",
                    true,
                    Map.of("weather", List.of(Map.of("city", "Berlin")))
            ));
            assertTrue(saved.await(3, TimeUnit.SECONDS));

            scheduler.shutdown();
            scheduler.shutdown();

            assertFalse(scheduler.status().running());
            assertTrue(scheduler.status().sources().isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsStopWhenPollingIsNotRunning() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        PollingScheduler scheduler = scheduler(mockAggregator(), mock(DataStorage.class), properties(), executor);

        try {
            assertThrows(IllegalStateException.class, scheduler::stop);
        } finally {
            executor.shutdownNow();
        }
    }

    private DataAggregator mockAggregator() {
        DataAggregator aggregator = mock(DataAggregator.class);
        when(aggregator.sourceNames()).thenReturn(List.of("weather", "users", "comments"));
        return aggregator;
    }

    private PollingScheduler scheduler(
            DataAggregator aggregator,
            DataStorage storage,
            PollingProperties properties,
            ScheduledThreadPoolExecutor executor
    ) {
        return new PollingScheduler(aggregator, Map.of("json", storage, "csv", mock(DataStorage.class)), properties, executor);
    }

    private Map<String, String> nullValueParameter() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("city", null);
        return parameters;
    }

    private ApiRecord record(String source) {
        return new ApiRecord(source, Instant.now(), Map.of(), TextNode.valueOf("ok"));
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
