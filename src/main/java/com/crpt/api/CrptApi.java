package com.crpt.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Log
public class CrptApi {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int requestLimit;
    private final AtomicInteger requestCount;
    private final ScheduledExecutorService scheduler;
    private final Object rateLimitLock = new Object();
    private volatile boolean rateLimitExceeded = false;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestLimit = requestLimit;
        this.requestCount = new AtomicInteger(0);
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Schedule the rate limit reset task
        long period = timeUnit.toMillis(1);
        scheduler.scheduleAtFixedRate(this::resetRequestCount, period, period, TimeUnit.MILLISECONDS);
    }

    private void resetRequestCount() {
        synchronized (rateLimitLock) {
            log.info("Resetting request count");
            requestCount.set(0);
            rateLimitExceeded = false;
            rateLimitLock.notifyAll();
        }
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        synchronized (rateLimitLock) {
            while (requestCount.get() >= requestLimit) {
                log.info("Rate limit exceeded, waiting until the next interval");
                rateLimitExceeded = true;
                rateLimitLock.wait();
            }
            requestCount.incrementAndGet();
        }

        log.info("Attempting to create document");

        HttpRequest request = buildRequest(document, signature);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMessage = "Failed to create document: " + response.body();
            log.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        log.info("Document created successfully");
    }

    private HttpRequest buildRequest(Document document, String signature) throws IOException {
        String requestBody = objectMapper.writeValueAsString(document);
        return HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    @AllArgsConstructor
    public static class Description {
        private String participantInn;
    }

    @Data
    @AllArgsConstructor
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }
}
