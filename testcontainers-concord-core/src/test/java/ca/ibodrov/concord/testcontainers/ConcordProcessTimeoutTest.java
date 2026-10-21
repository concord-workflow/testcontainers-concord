package ca.ibodrov.concord.testcontainers;

import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.DefaultApiClientFactory;
import com.walmartlabs.concord.client2.ProcessEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.ENQUEUED;
import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.FINISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcordProcessTimeoutTest {

    @Test
    void timesOutWhileWaitingForResponseHeaders() throws Exception {
        assertRequestTimesOut(false);
    }

    @Test
    void timesOutWhileReadingChildStatusResponseBody() throws Exception {
        assertRequestTimesOut(true);
    }

    private void assertRequestTimesOut(boolean childStatus) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.process.getEntry(); // Warm the HTTP connection before measuring the deadline.
            fixture.blockResponse = true;
            fixture.sendHeadersBeforeBlocking = childStatus;
            Future<IllegalStateException> result = fixture.caller.submit(() -> assertThrows(IllegalStateException.class,
                    () -> {
                        if (childStatus) {
                            fixture.process.waitForChildStatus(Duration.ofMillis(500), FINISHED);
                        } else {
                            fixture.process.waitForStatus(Duration.ofMillis(500), FINISHED);
                        }
                    }));

            assertTrue(fixture.requestReceived.await(2, TimeUnit.SECONDS));
            // Require the timeout before releasing the withheld FINISHED response.
            result.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void doesNotSleepPastAShortDeadline() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.process.getEntry();
            fixture.status = ENQUEUED;
            Future<IllegalStateException> result = fixture.caller.submit(() -> assertThrows(IllegalStateException.class,
                    () -> fixture.process.waitForStatus(Duration.ofMillis(50), FINISHED)));

            result.get(350, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void returnsARequestedNonterminalStatusBeforeTheDeadline() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.status = ENQUEUED;
            ProcessEntry result = fixture.process.expectStatus(Duration.ofSeconds(5), FINISHED, ENQUEUED);
            assertEquals(ENQUEUED, result.getStatus());
            assertEquals(fixture.instanceId, result.getInstanceId());
        }
    }

    @Test
    void preservesCallerInterruptionWhileCancellingTheWait() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.process.getEntry();
            fixture.blockResponse = true;
            AtomicReference<Thread> callerThread = new AtomicReference<>();
            Future<IllegalStateException> result = fixture.caller.submit(() -> {
                callerThread.set(Thread.currentThread());
                IllegalStateException error = assertThrows(IllegalStateException.class,
                        () -> fixture.process.waitForStatus(Duration.ofSeconds(10), FINISHED));
                assertTrue(Thread.currentThread().isInterrupted());
                return error;
            });

            assertTrue(fixture.requestReceived.await(2, TimeUnit.SECONDS));
            callerThread.get().interrupt();
            assertTrue(result.get(2, TimeUnit.SECONDS).getCause() instanceof InterruptedException);
        }
    }

    private static class Fixture implements AutoCloseable {

        private final UUID instanceId = UUID.randomUUID();
        private final CountDownLatch requestReceived = new CountDownLatch(1);
        private final CountDownLatch releaseResponse = new CountDownLatch(1);
        private final ExecutorService caller = Executors.newSingleThreadExecutor();
        private final HttpServer server;
        private final ConcordProcess process;
        private volatile boolean blockResponse;
        private volatile boolean sendHeadersBeforeBlocking;
        private volatile ProcessEntry.StatusEnum status = FINISHED;

        private Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String entry = "{\"instanceId\":\"" + instanceId + "\",\"status\":\"" + status + "\"}";
                String body = exchange.getRequestURI().getPath().contains("subprocess") ? "[" + entry + "]" : entry;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    if (sendHeadersBeforeBlocking) {
                        exchange.sendResponseHeaders(200, bytes.length);
                    }
                    if (blockResponse || status == ENQUEUED) {
                        requestReceived.countDown();
                    }
                    if (blockResponse) {
                        releaseResponse.await();
                    }
                    if (!sendHeadersBeforeBlocking) {
                        exchange.sendResponseHeaders(200, bytes.length);
                    }
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            ApiClient client = new DefaultApiClientFactory("http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(10), true)
                    .create(ApiClientConfiguration.builder().apiKey("timeout-test").build());
            process = new ConcordProcess(client, instanceId);
        }

        @Override
        public void close() throws InterruptedException {
            releaseResponse.countDown();
            server.stop(0);
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
