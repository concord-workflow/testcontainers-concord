package ca.ibodrov.concord.testcontainers.junit5;

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

import ca.ibodrov.concord.testcontainers.ContainerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcordRuleCustomizerTest {

    private static final List<ContainerType> customized = new ArrayList<>();
    private static GenericContainer<?> database;

    @RegisterExtension
    static final ConcordRule concord = new ConcordRule()
            .mode(ca.ibodrov.concord.testcontainers.Concord.Mode.DOCKER)
            .dbImage("postgres:10")
            .serverImage("walmartlabs/concord-server:2.25.1")
            .agentImage("walmartlabs/concord-agent:2.25.1")
            .startAgent(false)
            .containerCustomizer((type, container) -> {
                customized.add(type);
                if (type == ContainerType.DB) {
                    database = container;
                    container.withCommand("postgres", "-c", "max_connections=150");
                }
            });

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void extensionInheritsCustomizerAndStartsCustomizedDatabaseWithoutAgent() throws Exception {
        assertEquals(List.of(ContainerType.DB, ContainerType.SERVER), customized);
        assertFalse(customized.contains(ContainerType.AGENT));
        var result = database.execInContainer("psql", "-U", "postgres", "-tAc", "show max_connections");
        assertEquals(0, result.getExitCode(), result.getStderr());
        assertEquals("150", result.getStdout().trim());
        assertTrue(concord.environment().apiPort() > 0);
    }
}
