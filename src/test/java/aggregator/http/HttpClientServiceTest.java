package aggregator.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientServiceTest {
    private HttpClient client;
    private HttpResponse<String> response;
    private HttpClientService service;

    @BeforeEach
    void setUp() {
        client = mock(HttpClient.class);
        response = mock(HttpResponse.class);
        service = new HttpClientService(client);
    }

    @Test
    void sendsGetRequestWithHeadersAndReturnsResponseBody() throws Exception {
        URI uri = URI.create("https://example.test/data?city=Tirana");
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"temperature\":20}");

        String result = service.get(uri);

        assertEquals("{\"temperature\":20}", result);
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpRequest request = requestCaptor.getValue();
        assertEquals(uri, request.uri());
        assertEquals("GET", request.method());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("RestApiDataAggregator/1.0", request.headers().firstValue("User-Agent").orElseThrow());
        assertEquals(20, request.timeout().orElseThrow().toSeconds());
    }

    @Test
    void throwsIOExceptionWhenServerReturnsNonSuccessfulStatus() throws Exception {
        URI uri = URI.create("https://example.test/missing");
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn("not found");

        IOException exception = assertThrows(IOException.class, () -> service.get(uri));

        assertTrue(exception.getMessage().contains("HTTP 404"));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void propagatesIOExceptionWhenHttpClientFails() throws Exception {
        URI uri = URI.create("https://example.test/unavailable");
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new IOException("connection failed"));

        IOException exception = assertThrows(IOException.class, () -> service.get(uri));

        assertEquals("connection failed", exception.getMessage());
    }

    @Test
    void propagatesInterruptedExceptionWhenRequestIsInterrupted() throws Exception {
        URI uri = URI.create("https://example.test/interrupted");
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new InterruptedException("request interrupted"));

        InterruptedException exception = assertThrows(InterruptedException.class, () -> service.get(uri));

        assertEquals("request interrupted", exception.getMessage());
    }
}
