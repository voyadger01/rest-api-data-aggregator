package aggregator.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpClientService {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientService.class);
    private final HttpClient client;

    public HttpClientService(HttpClient client) {
        this.client = client;
    }

    public HttpClientService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public String get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "RestApiDataAggregator/1.0")
                .GET()
                .build();

        long startedAt = System.nanoTime();
        logger.info("[START] HTTP GET {}", uri);

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException exception) {
            logger.error(
                    "[END] HTTP GET {} result=ERROR durationMs={} message={}",
                    uri,
                    elapsedMillis(startedAt),
                    exception.getMessage()
            );
            throw exception;
        }

        long duration = elapsedMillis(startedAt);
        int statusCode = response.statusCode();

        if (statusCode < 200 || statusCode >= 300) {
            logger.warn(
                    "[END] HTTP GET {} status={} result=HTTP_ERROR durationMs={}",
                    uri,
                    statusCode,
                    duration
            );
            throw new IOException(
                    "HTTP " + statusCode + " при запросе " + uri + ": " + response.body()
            );
        }

        logger.info(
                "[END] HTTP GET {} status={} result=OK durationMs={}",
                uri,
                statusCode,
                duration
        );
        return response.body();
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
