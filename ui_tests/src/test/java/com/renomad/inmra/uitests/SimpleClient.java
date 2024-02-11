package com.renomad.inmra.uitests;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class SimpleClient {

    /**
     * Get from a URL
     * @param headers a list of key-value pairs - the key and value must alternate.
     *                Otherwise an exception gets thrown
     */
    public static HttpResponse<String> get(String url, List<String> headers) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .headers(headers.toArray(new String[0]))
                    .build();
            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Post to a URL
     * @param headers a list of key-value pairs - the key and value must alternate.
     *                Otherwise an exception gets thrown
     */
    public static HttpResponse<String> post(String url, String body, List<String> headers) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .headers(headers.toArray(new String[0]))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
