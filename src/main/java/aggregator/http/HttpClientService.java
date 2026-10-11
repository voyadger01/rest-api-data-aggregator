package http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpClientService {
    private final HttpClient client;

    public HttpClientService(HttpClient client) {
        this.client = client;
    }
    public HttpClientService() {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        return client.send(request, handler);
    }
}
