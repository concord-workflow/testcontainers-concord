package ca.ibodrov.concord.testcontainers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2026 Ivan Bodrov
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.ProcessEventEntry;
import com.walmartlabs.concord.client2.ProcessEventsApi;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.DockerClientFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerConfigurationTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String WIREMOCK_IMAGE = "wiremock/wiremock:3.13.2";
    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void configuresDockerLimitsAndRunsHttpFlowThroughExtraContainer() throws Exception {
        Map<ContainerType, GenericContainer<?>> containers = new EnumMap<>(ContainerType.class);
        List<String> callbackOrder = new ArrayList<>();
        AtomicReference<GenericContainer<?>> mockRef = new AtomicReference<>();
        Network[] suppliedNetwork = new Network[1];
        Path marker = Files.createTempFile("concord-customizer", ".txt",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));
        Files.writeString(marker, "callback mount", StandardCharsets.UTF_8);
        Concord<?> concord = new Concord<>()
                .mode(Concord.Mode.DOCKER)
                .dbImage("postgres:10")
                .serverImage("walmartlabs/concord-server:2.25.1")
                .agentImage("walmartlabs/concord-agent:2.25.1")
                .agentEnv("CALLBACK_ORDER", "library-default")
                .extraContainerSupplier(network -> {
                    suppliedNetwork[0] = network;
                    GenericContainer<?> mock = new GenericContainer<>(DockerImageName.parse(WIREMOCK_IMAGE))
                            .withNetwork(network)
                            .withNetworkAliases("mock-http")
                            .withExposedPorts(8080)
                            .withCommand("--max-request-journal-entries", "10")
                            .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));
                    mockRef.set(mock);
                    return List.of(mock);
                });
        concord.containerCustomizer((type, container) -> {
            callbackOrder.add(type + ":first");
            container.withEnv("CALLBACK_ORDER", "first");
            container.withFileSystemBind(marker.toString(), "/tmp/concord-customizer-marker.txt", BindMode.READ_ONLY);
            if (type == ContainerType.DB) {
                container.withCommand("postgres", "-c", "max_connections=150");
            }
            if (type == ContainerType.SERVER) {
                container.withEnv("CONCORD_JAVA_OPTS", "-Xms256m -Xmx1g -XX:StartFlightRecording=name=concord,settings=profile,dumponexit=true,filename=/tmp/server.jfr");
            }
            container.withCreateContainerCmdModifier(cmd -> {
                HostConfig host = cmd.getHostConfig();
                long[] cpuQuotaAndMemory = switch (type) {
                    case DB -> new long[]{200_000, 2 * GIB};
                    case SERVER -> new long[]{200_000, 3 * GIB};
                    case AGENT -> new long[]{400_000, 4 * GIB};
                };
                host.withCpuPeriod(100_000L).withCpuQuota(cpuQuotaAndMemory[0]).withMemory(cpuQuotaAndMemory[1]);
            });
            containers.put(type, container);
        });
        concord.containerCustomizer((type, container) -> {
            callbackOrder.add(type + ":second");
            container.withEnv("CALLBACK_ORDER", "second");
        });

        try {
            concord.start();
            assertNotNull(suppliedNetwork[0]);
            assertTrue(mockRef.get().isRunning());
            assertEquals(List.of("DB:first", "DB:second", "SERVER:first", "SERVER:second", "AGENT:first", "AGENT:second"), callbackOrder);

            Map<ContainerType, long[]> expected = Map.of(
                    ContainerType.DB, new long[]{200_000, 2 * GIB},
                    ContainerType.SERVER, new long[]{200_000, 3 * GIB},
                    ContainerType.AGENT, new long[]{400_000, 4 * GIB});
            for (ContainerType type : ContainerType.values()) {
                GenericContainer<?> container = containers.get(type);
                assertNotNull(container, "missing callback container: " + type);
                InspectContainerResponse inspect = DockerClientFactory.instance().client()
                        .inspectContainerCmd(container.getContainerId()).exec();
                HostConfig actual = inspect.getHostConfig();
                assertEquals(100_000L, actual.getCpuPeriod(), type + " CPU period");
                assertEquals(expected.get(type)[0], actual.getCpuQuota(), type + " CPU quota");
                assertEquals(expected.get(type)[1], actual.getMemory(), type + " memory bytes");
                assertTrue(List.of(inspect.getConfig().getEnv()).contains("CALLBACK_ORDER=second"), type + " callback order was not effective");
                if (type == ContainerType.SERVER) {
                    var recording = container.execInContainer("jcmd", "0", "JFR.check");
                    assertEquals(0, recording.getExitCode(), recording.getStderr());
                    assertTrue(recording.getStdout().contains("name=concord")
                            && recording.getStdout().contains("running"), recording.getStdout());
                }
                var markerResult = container.execInContainer("cat", "/tmp/concord-customizer-marker.txt");
                assertEquals(0, markerResult.getExitCode(), markerResult.getStderr());
                assertEquals("callback mount", markerResult.getStdout().strip(), type + " callback file missing");
            }
            GenericContainer<?> db = containers.get(ContainerType.DB);
            assertEquals("150", db.execInContainer("sh", "-c", "PGPASSWORD=q1 psql -h 127.0.0.1 -U postgres -tAc 'SHOW max_connections'").getStdout().strip());

            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            int wiremockPort = mockRef.get().getFirstMappedPort();
            URI admin = URI.create("http://localhost:" + wiremockPort + "/__admin/");
            postJson(http, admin.resolve("mappings"), """
                    {"request":{"method":"GET","urlPath":"/first"},"response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":{"value":"from-first-response"}}}
                    """);
            postJson(http, admin.resolve("mappings"), """
                    {"request":{"method":"GET","urlPath":"/second","queryParameters":{"value":{"equalTo":"from-first-response"}}},"response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":{"result":"second-response-ok"}}}
                    """);
            HttpResponse<String> reset = http.send(HttpRequest.newBuilder(admin.resolve("requests")).DELETE().build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(reset.statusCode() == 200 || reset.statusCode() == 204, reset.body());

            String flow = """
                    configuration:
                      runtime: concord-v2
                      events:
                        recordEvents: true
                      dependencies:
                        - mvn://com.walmartlabs.concord.plugins.basic:http-tasks:2.25.1
                    flows:
                      default:
                        - task: http
                          in:
                            method: GET
                            url: http://mock-http:8080/first
                            response: json
                          out: first
                        - task: http
                          in:
                            method: GET
                            url: http://mock-http:8080/second
                            response: json
                            query:
                              value: ${first.content.value}
                          out: second
                        - if: ${second.content.result != 'second-response-ok'}
                          then:
                            - throw: "Unexpected second HTTP response: ${second.content.result}"
                        - log: "Second HTTP response: ${second.content.result}"
                    """;
            Payload payload = new Payload().concordYml(flow);
            ConcordProcess process = concord.processes().start(payload);
            ProcessEntry result = process.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
            byte[] processLog = process.getLog();
            Path evidence = Path.of("target", "container-configuration-evidence");
            Files.createDirectories(evidence);
            Files.write(evidence.resolve("process.log"), processLog);
            JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("docker-inspect.json").toFile(), inspectSummary(containers));
            assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), new String(processLog, StandardCharsets.UTF_8));
            process.assertLog(".*Second HTTP response: second-response-ok.*");

            HttpResponse<String> requests = http.send(HttpRequest.newBuilder(admin.resolve("requests")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, requests.statusCode(), requests.body());
            JsonNode requestRecords = JSON.readTree(requests.body()).path("requests");
            assertEquals(2, requestRecords.size(), requests.body());
            List<String> requestedUrls = new ArrayList<>();
            requestRecords.forEach(record -> requestedUrls.add(record.path("request").path("url").asText()));
            assertEquals(java.util.Set.of("/first", "/second?value=from-first-response"), java.util.Set.copyOf(requestedUrls));

            List<ProcessEventEntry> events = new ProcessEventsApi(concord.apiClient())
                    .listProcessEvents(process.instanceId(), null, null, null, null, null, null, null);
            JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("process-events.json").toFile(), events);
            JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("wiremock-requests.json").toFile(), requestRecords);
        } finally {
            try {
                concord.close();
            } finally {
                Files.deleteIfExists(marker);
            }
        }
    }

    private static void postJson(HttpClient client, URI uri, String json) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.strip(), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), response.body());
    }

    private static JsonNode inspectSummary(Map<ContainerType, GenericContainer<?>> containers) throws Exception {
        var summary = JSON.createObjectNode();
        for (var entry : containers.entrySet()) {
            InspectContainerResponse inspect = DockerClientFactory.instance().client().inspectContainerCmd(entry.getValue().getContainerId()).exec();
            var value = summary.putObject(entry.getKey().name());
            value.put("image", inspect.getConfig().getImage());
            value.put("cpuPeriod", inspect.getHostConfig().getCpuPeriod());
            value.put("cpuQuota", inspect.getHostConfig().getCpuQuota());
            value.put("memoryBytes", inspect.getHostConfig().getMemory());
        }
        return summary;
    }
}
