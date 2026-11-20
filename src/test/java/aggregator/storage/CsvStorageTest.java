package aggregator.storage;

import aggregator.model.ApiRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvStorageTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void savesLoadsAndAppendsRecordsWithNestedJson() throws Exception {
        CsvStorage storage = new CsvStorage(mapper);
        Path file = tempDir.resolve("records.csv");
        ApiRecord first = record("users", Map.of("username", "Bret"), """
                [{
                  "id": 1,
                  "name": "A, \\\"B\\\"",
                  "username": "Bret",
                  "email": "a@example.test",
                  "address": {"city": "Gwenborough", "geo": {"lat": "-37.3159", "lng": "81.1496"}},
                  "phone": "1-770-736-8031",
                  "website": "example.test",
                  "company": {"name": "Example Corp"}
                }]
                """);
        ApiRecord second = record("comments", Map.of("sortBy", "likes", "order", "desc"), """
                {
                  "comments": [{
                    "id": 15,
                    "body": "A comment, with comma",
                    "postId": 6,
                    "likes": 2,
                    "user": {"id": 17, "username": "evelyns", "fullName": "Evelyn Sanchez"}
                  }],
                  "total": 1,
                  "skip": 0,
                  "limit": 1
                }
                """);

        storage.save(List.of(first), file, false);
        storage.save(List.of(second), file, true);

        List<ApiRecord> loaded = storage.load(file);
        assertEquals(2, loaded.size());
        assertEquals("A, \"B\"", loaded.getFirst().node().get(0).path("name").asText());
        assertEquals("comments", loaded.get(1).source());
        assertEquals("desc", loaded.get(1).parameters().get("order"));
        assertEquals("evelyns", loaded.get(1).node().path("comments").get(0).path("user").path("username").asText());
        String csv = Files.readString(file);
        assertTrue(csv.startsWith("id,source,timestamp,data."));
        assertTrue(csv.contains("data.0.address.geo.lat"));
        assertTrue(csv.contains("data.comments.0.user.username"));
    }

    @Test
    void preservesRootArraysScalarTypesEmptyValuesAndEscapedLineBreaks() throws Exception {
        CsvStorage storage = new CsvStorage(mapper);
        Path file = tempDir.resolve("types.csv");
        ApiRecord objectRecord = record("types", Map.of(), """
                {
                  "integer": 12,
                  "decimal": 1.25,
                  "boolean": true,
                  "nothing": null,
                  "textNumber": "12",
                  "emptyText": "",
                  "multiline": "first line\\nsecond, \\\"quoted\\\" line",
                  "emptyObject": {},
                  "emptyArray": []
                }
                """);
        ApiRecord arrayRecord = record("array", Map.of(), "[1,{\"nested\":[false,\"value\"]},null]");

        storage.save(List.of(objectRecord, arrayRecord), file, false);

        List<ApiRecord> loaded = storage.load(file);
        var object = loaded.getFirst().node();
        assertTrue(object.path("integer").isIntegralNumber());
        assertTrue(object.path("decimal").isFloatingPointNumber());
        assertTrue(object.path("boolean").isBoolean());
        assertTrue(object.path("nothing").isNull());
        assertTrue(object.path("textNumber").isTextual());
        assertEquals("", object.path("emptyText").asText());
        assertEquals("first line\nsecond, \"quoted\" line", object.path("multiline").asText());
        assertTrue(object.path("emptyObject").isObject());
        assertTrue(object.path("emptyArray").isArray());
        assertTrue(loaded.get(1).node().isArray());
        assertEquals("value", loaded.get(1).node().path(1).path("nested").path(1).asText());
        assertTrue(loaded.get(1).node().path(2).isNull());
    }

    @Test
    void handlesEmptyRecordsAndCreatesParentDirectories() throws Exception {
        CsvStorage storage = new CsvStorage(mapper);
        Path file = tempDir.resolve("nested/empty.csv");

        storage.save(List.of(), file, false);

        assertEquals(List.of(), storage.load(file));
        Files.writeString(file, "  \r\n");
        assertEquals(List.of(), storage.load(file));
    }

    @Test
    void rejectsMissingFilesInvalidHeadersAndRowsWithWrongColumnCount() throws Exception {
        CsvStorage storage = new CsvStorage(mapper);
        Path missing = tempDir.resolve("missing.csv");
        Path invalidHeader = tempDir.resolve("header.csv");
        Path invalidRow = tempDir.resolve("row.csv");
        Files.writeString(invalidHeader, "source,timestamp,data\r\nweather,2026-01-01T00:00:00Z,1\r\n");
        Files.writeString(invalidRow, "id,source,timestamp,data.value\r\n1,weather,2026-01-01T00:00:00Z\r\n");

        assertThrows(IOException.class, () -> storage.load(missing));
        assertThrows(IOException.class, () -> storage.load(invalidHeader));
        assertThrows(IOException.class, () -> storage.load(invalidRow));
    }

    @Test
    void rejectsMalformedCsvQuotesAndConflictingNestedPaths() throws Exception {
        CsvStorage storage = new CsvStorage(mapper);
        Path malformed = tempDir.resolve("malformed.csv");
        Path conflicting = tempDir.resolve("conflicting.csv");
        Path trailingCharacters = tempDir.resolve("trailing.csv");
        Files.writeString(malformed, "id,source,timestamp,data.value\r\n1,weather,2026-01-01T00:00:00Z,\"unclosed\r\n");
        Files.writeString(conflicting, "id,source,timestamp,data.0.name,data.other\r\n1,weather,2026-01-01T00:00:00Z,value,other\r\n");
        Files.writeString(trailingCharacters, "id,source,timestamp,data.value\r\n1,weather,2026-01-01T00:00:00Z,\"quoted\"tail\r\n");

        assertThrows(IOException.class, () -> storage.load(malformed));
        assertThrows(IOException.class, () -> storage.load(conflicting));
        assertThrows(IOException.class, () -> storage.load(trailingCharacters));
    }

    @Test
    void rejectsInvalidTimestamp() throws Exception {
        CsvStorage storage = new CsvStorage(mapper);
        Path file = tempDir.resolve("invalid-time.csv");
        Files.writeString(file, "id,source,timestamp,data.value\r\n1,weather,not-an-instant,value\r\n");

        assertThrows(IOException.class, () -> storage.load(file));
    }

    @Test
    void serializesConcurrentAppendsWithoutLosingRecords() throws Exception {
        CsvStorage storage = new CsvStorage(mapper);
        Path file = tempDir.resolve("concurrent.csv");
        storage.save(List.of(record("initial", Map.of(), "{}")), file, false);
        int writers = 6;
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
                        storage.save(List.of(record("source-" + index, Map.of(), "{\"index\":" + index + "}")), file, true);
                    } catch (Throwable exception) {
                        failure.compareAndSet(null, exception);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            assertTrue(ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
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

    private ApiRecord record(String source, Map<String, String> parameters, String json) throws IOException {
        return new ApiRecord(
                source,
                Instant.parse("2026-01-01T10:00:00Z"),
                parameters,
                mapper.readTree(json)
        );
    }
}
