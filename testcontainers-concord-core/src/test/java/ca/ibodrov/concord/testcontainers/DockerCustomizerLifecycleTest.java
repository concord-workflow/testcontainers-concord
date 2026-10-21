package ca.ibodrov.concord.testcontainers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2020 Ivan Bodrov
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

import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerCustomizerLifecycleTest {

    private static final String DB = "postgres:10";
    private static final String SERVER = "walmartlabs/concord-server:2.46.0";
    private static final String AGENT = "walmartlabs/concord-agent:2.46.0";

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void customizesEnabledContainersInOrderBeforeStartup() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicReference<GenericContainer<?>> database = new AtomicReference<>();
        try (GenericContainer<?> dependency = new GenericContainer<>(DockerImageName.parse("alpine:3.20"))
                .withCommand("sh", "-c", "sleep 600")) {
            Concord<?> concord = new Concord<>()
                    .dbImage(DB)
                    .serverImage(SERVER)
                    .agentImage(AGENT)
                    .startAgent(false)
                    .dependsOn(dependency)
                    .containerCustomizer((type, container) -> {
                        assertFalse(dependency.isRunning(), "Customize containers before starting dependencies");
                        events.add("first:" + type);
                        if (type == ContainerType.DB) {
                            database.set(container);
                            container.withCommand("postgres", "-c", "max_connections=125");
                        }
                    })
                    .containerCustomizer((type, container) -> {
                        events.add("second:" + type);
                        if (type == ContainerType.DB) {
                            container.withCommand("postgres", "-c", "max_connections=175");
                        }
                    })
                    .containerListener(new ContainerListener() {
                        @Override
                        public void beforeStart(ContainerType type) {
                            events.add("before:" + type);
                        }

                        @Override
                        public void afterStart(ContainerType type, org.testcontainers.containers.Container<?> container) {
                            events.add("after:" + type);
                        }
                    });
            try {
                concord.start();
                assertTrue(dependency.isRunning());
                var result = database.get().execInContainer("psql", "-U", "postgres", "-tAc", "show max_connections");
                assertEquals(0, result.getExitCode(), result.getStderr());
                assertEquals("175", result.getStdout().trim());
                assertEquals(List.of("first:DB", "second:DB", "first:SERVER", "second:SERVER",
                        "before:DB", "after:DB", "before:SERVER", "after:SERVER"), events);
            } finally {
                concord.close();
            }
            assertTrue(dependency.isRunning(), "Caller-owned dependency must survive environment shutdown");
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void callbackFailurePropagatesAndDoesNotStopPreRunningCallerResource() {
        RuntimeException original = new RuntimeException("customizer failed");
        AtomicReference<String> networkId = new AtomicReference<>();
        try (GenericContainer<?> unrelated = new GenericContainer<>(DockerImageName.parse("alpine:3.20"))
                .withCommand("sh", "-c", "sleep 600")) {
            unrelated.start();
            Concord<?> concord = new Concord<>()
                    .dbImage(DB)
                    .serverImage(SERVER)
                    .agentImage(AGENT)
                    .dependsOn(unrelated)
                    .containerCustomizer((type, container) -> {
                        networkId.compareAndSet(null, container.getNetwork().getId());
                        if (type == ContainerType.SERVER) {
                            throw original;
                        }
                    });
            RuntimeException thrown = assertThrows(RuntimeException.class, concord::start);
            assertSame(original, thrown);
            assertTrue(unrelated.isRunning());
            assertThrows(NotFoundException.class, () -> DockerClientFactory.instance().client()
                    .inspectNetworkCmd().withNetworkId(networkId.get()).exec());
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void startupFailureStopsExtraContainerStartedByThisEnvironment() {
        RuntimeException original = new RuntimeException("startup failed");
        AtomicReference<String> networkId = new AtomicReference<>();
        try (GenericContainer<?> unrelated = new GenericContainer<>(DockerImageName.parse("alpine:3.20"))
                .withCommand("sh", "-c", "sleep 600");
             GenericContainer<?> extra = new GenericContainer<>(DockerImageName.parse("alpine:3.20"))
                     .withCommand("sh", "-c", "sleep 600")) {
            unrelated.start();
            Concord<?> concord = new Concord<>()
                    .dbImage(DB)
                    .serverImage(SERVER)
                    .agentImage(AGENT)
                    .extraContainerSupplier(network -> {
                        networkId.set(network.getId());
                        return List.of(unrelated, extra.withNetwork(network));
                    })
                    .containerListener(new ContainerListener() {
                        @Override
                        public void beforeStart(ContainerType type) {
                            assertTrue(extra.isRunning());
                            throw original;
                        }
                    });
            assertSame(original, assertThrows(RuntimeException.class, concord::start));
            assertFalse(extra.isRunning());
            assertTrue(unrelated.isRunning());
            assertThrows(NotFoundException.class, () -> DockerClientFactory.instance().client()
                    .inspectNetworkCmd().withNetworkId(networkId.get()).exec());
        }
    }

}
