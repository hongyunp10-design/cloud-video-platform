
package com.hongyun.asynctask.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonVideoClient {

    private final RestClient restClient;

    public PythonVideoClient(
            @Value("${app.video-api.base-url}") String baseUrl
    ) {
        // Prevent HTTP calls from blocking indefinitely.
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    // Submit a staged video to the Python processing pipeline.
    public void submitProcessing(String videoId) {
        restClient.post()
                .uri("/video/process/{videoId}", videoId)
                .retrieve()
                .toBodilessEntity();
    }

    // Query the current Python video processing status.
    public String getStatus(String videoId) {
        JsonNode response = restClient.get()
                .uri("/video/status/{videoId}", videoId)
                .retrieve()
                .body(JsonNode.class);

        if (response == null ||
                !response.path("status").isTextual()) {
            throw new IllegalStateException(
                    "Invalid video status response: " + videoId
            );
        }

        return response.path("status").asText();
    }
}
