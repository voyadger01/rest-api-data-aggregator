package aggregator.storage;

import aggregator.model.ApiRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonStorageTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void savesLoadsAndAppendsRecords() throws Exception {
        JsonStorage storage = new JsonStorage(mapper);
        Path file = tempDir.resolve("records.json");
        ApiRecord first = record("weather", "{\"current\":{\"temperature\":20}}");
        ApiRecord second = record("comments", """
                {
                  "comments": [{"id": 1, "body": "Text", "postId": 1, "likes": 3,
                    "user": {"id": 2, "username": "user", "fullName": "Test User"}}],
                  "total": 1,
                  "skip": 0,
                  "limit": 1
                }
                """);

        storage.save(List.of(first), file, false);
        storage.save(List.of(second), file, true);

        List<ApiRecord> loaded = storage.load(file);
        assertEquals(2, loaded.size());
        assertEquals("weather", loaded.getFirst().source());
        assertEquals("comments", loaded.get(1).source());
        assertEquals("user", loaded.get(1).node().path("comments").get(0).path("user").path("username").asText());
    }

    @Test
    void overwritesExistingRecordsWhenAppendIsDisabled() throws Exception {
        JsonStorage storage = new JsonStorage(mapper);
        Path file = tempDir.resolve("overwrite.json");
        storage.save(List.of(record("weather", "{}")), file, false);

        storage.save(List.of(record("users", "[]")), file, false);

        List<ApiRecord> loaded = storage.load(file);
        assertEquals(1, loaded.size());
        assertEquals("users", loaded.getFirst().source());
        assertEquals(true, loaded.getFirst().node().isArray());
    }

    @Test
    void createsParentDirectoriesAndLoadsEmptyFiles() throws Exception {
        JsonStorage storage = new JsonStorage(mapper);
        Path file = tempDir.resolve("nested/path/empty.json");

        storage.save(List.of(), file, false);

        assertEquals(List.of(), storage.load(file));
        Files.writeString(file, "");
        assertEquals(List.of(), storage.load(file));
    }

    @Test
    void throwsWhenFileDoesNotExistOrContainsInvalidJson() throws Exception {
        JsonStorage storage = new JsonStorage(mapper);
        Path missing = tempDir.resolve("missing.json");
        Path invalid = tempDir.resolve("invalid.json");
        Files.writeString(invalid, "not-json");

        assertThrows(IOException.class, () -> storage.load(missing));
        assertThrows(IOException.class, () -> storage.load(invalid));
    }

    @Test
    void serializesConcurrentAppendsWithoutLosingRecords() throws Exception {
        JsonStorage storage = new JsonStorage(mapper);
        Path file = tempDir.resolve("concurrent.json");
        storage.save(List.of(record("initial", "{}")), file, false);
        int writers = 8;
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(writers);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(writers);

        try {
            for (int i = 0; i < writers; i++) {
                int index = i;
                executor.execute(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(3, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for writers");
                        }
                        storage.save(List.of(record("source-" + index, "{\"index\":" + index + "}")), file, true);
                    } catch (Throwable exception) {
                        failure.compareAndSet(null, exception);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            assertEquals(true, ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(true, finished.await(5, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        List<ApiRecord> loaded = storage.load(file);
        assertEquals(writers + 1, loaded.size());
        assertEquals(writers + 1, loaded.stream().map(ApiRecord::source).distinct().count());
    }

    private ApiRecord record(String source, String json) throws IOException {
        return new ApiRecord(
                source,
                Instant.parse("2026-01-01T10:00:00Z"),
                Map.of("source", source),
                mapper.readTree(json)
        );
    }
}
