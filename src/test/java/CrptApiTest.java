import com.crpt.api.CrptApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CrptApiTest {

    private HttpClient httpClient;
    private CrptApi crptApi;
    private HttpResponse<String> httpResponse;
    private TestLogHandler logHandler;

    @BeforeEach
    public void setUp() {
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);
        crptApi = new CrptApi(TimeUnit.SECONDS, 5);

        try {
            java.lang.reflect.Field httpClientField = CrptApi.class.getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(crptApi, httpClient);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        Logger logger = Logger.getLogger(CrptApi.class.getName());
        logHandler = new TestLogHandler();
        logger.addHandler(logHandler);
        logger.setLevel(Level.ALL);
    }

    @Test
    public void testCreateDocumentSuccess() throws IOException, InterruptedException {

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        CrptApi.Document document = createSampleDocument();
        String signature = "test-signature";

        crptApi.createDocument(document, signature);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(1)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"), request.uri());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
        assertEquals("test-signature", request.headers().firstValue("Signature").orElse(""));
        assertEquals("POST", request.method());

        assertTrue(logHandler.getLog().contains("Attempting to create document"));
        assertTrue(logHandler.getLog().contains("Document created successfully"));
    }

    @Test
    public void testCreateDocumentRateLimitExceeded() throws IOException, InterruptedException {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        CrptApi.Document document = createSampleDocument();
        String signature = "test-signature";

        for (int i = 0; i < 5; i++) {
            crptApi.createDocument(document, signature);
        }

        crptApi.createDocument(document, signature);

        verify(httpClient, times(6)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertTrue(logHandler.getLog().contains("Rate limit exceeded, waiting until the next interval"));
    }

    @Test
    public void testCreateDocumentInvalidResponse() throws IOException, InterruptedException {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Internal Server Error");

        CrptApi.Document document = createSampleDocument();
        String signature = "test-signature";

        IOException thrown = assertThrows(IOException.class, () -> {
            crptApi.createDocument(document, signature);
        });

        assertEquals("Failed to create document: Internal Server Error", thrown.getMessage());

        assertTrue(logHandler.getLog().contains("Failed to create document: Internal Server Error"));
    }

    private CrptApi.Document createSampleDocument() {
        CrptApi.Description description = new CrptApi.Description("string");

        CrptApi.Product product = new CrptApi.Product(
                "string",
                "2020-01-23",
                "string",
                "string",
                "string",
                "2020-01-23",
                "string",
                "string",
                "string"
        );

        return new CrptApi.Document(
                description,
                "string",
                "string",
                "LP_INTRODUCE_GOODS",
                true,
                "string",
                "string",
                "string",
                "2020-01-23",
                "string",
                Collections.singletonList(product),
                "2020-01-23",
                "string"
        );
    }

    private static class TestLogHandler extends Handler {
        private final StringBuilder logBuilder = new StringBuilder();

        @Override
        public void publish(LogRecord record) {
            logBuilder.append(record.getMessage()).append("\n");
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        public String getLog() {
            return logBuilder.toString();
        }
    }
}
