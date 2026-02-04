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

import com.walmartlabs.concord.client2.ProcessEntry;

import com.walmartlabs.concord.client2.ProcessListFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static ca.ibodrov.concord.testcontainers.Utils.randomString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DockerTest {

    private static final Logger log = LoggerFactory.getLogger(DockerTest.class);
    private static Concord<?> concord;

    @BeforeAll
    static void setUp() {
        Concord<?> c = new Concord<>()
                .mode(Concord.Mode.DOCKER);

        c.start();

        concord = c;

        log.info("Concord IT server login: {}/#/login?useApiKey=true", concord.apiBaseUrl());
        log.info("Concord IT admin token: {}", concord.environment().apiToken());
    }

    @AfterAll
    static void tearDown() {
        if (concord != null) {
            concord.close();
            concord = null;
        }
    }

    @Test
    void testApiToken() {
        assertNotNull(concord.environment().apiToken());
    }

    @Test
    void testSimpleFlow() throws Exception {
        executeSimpleFlow(payload -> {}); // no project
    }

    @Test
    void testSimpleFlowWithProject() throws Exception {
        String orgName = "org_" + randomString();
        concord.organizations().create(orgName);

        String projectName = "project_" + randomString();
        // validate backwards compatibility. default project creation would allow payloads
        concord.projects().create(orgName, projectName);

        executeSimpleFlow(payload -> payload.org(orgName).project(projectName));
    }

    private void executeSimpleFlow(Consumer<Payload> payloadConsumer) throws Exception {
        String nameValue = "name_" + System.currentTimeMillis();

        String yml = """
                flows:
                  default:
                    - log: Hello, ${name}!
                """;

        var payload = new Payload()
                .concordYml(yml)
                .arg("name", nameValue);

        payloadConsumer.accept(payload);

        ConcordProcess p = concord.processes()
                .start(payload);

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*Hello, " + nameValue + ".*");
    }

    @Test
    void testTags() throws Exception {
        String tag = "tag_" + System.currentTimeMillis();

        String yml = """
                flows:
                  default:
                    - log: Hello, Concord!
                """;

        ConcordProcess p1 = concord.processes()
                .start(new Payload()
                        .concordYml(yml)
                        .tag(tag));

        p1.waitForStatus(ProcessEntry.StatusEnum.FINISHED);

        ConcordProcess p2 = concord.processes().get(p1.instanceId());
        p2.assertLog(".*Hello, Concord!.*");

        List<ProcessEntry> l = concord.processes().list(ProcessListFilter.builder()
                .addTags(tag)
                .build());
        assertEquals(1, l.size());
        assertEquals(p2.instanceId(), l.get(0).getInstanceId());
    }

    @Test
    void testSecrets() throws Exception {
        String yml = """
                flows:
                  default:
                  - log: ${crypto.exportAsString('Default', 'testSecret', null)}
                """;

        String mySecretValue = "Hello, I'm a secret value!";
        concord.secrets().createSecret(NewSecretQuery.builder()
                .org("Default")
                .name("testSecret")
                .build(), mySecretValue.getBytes());

        ConcordProcess p = concord.processes().start(new Payload().concordYml(yml));

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*" + mySecretValue + ".*");
    }

    @Test
    void testProcessLogStreaming() throws Exception {
        String yml = """
                flows:
                  default:
                    - log: Hello, Concord!
                """;

        ConcordProcess p = concord.processes()
                .create()
                .streamLogs(true)
                .payload(new Payload().concordYml(yml))
                .start();

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*Hello, Concord!.*");
    }

}
