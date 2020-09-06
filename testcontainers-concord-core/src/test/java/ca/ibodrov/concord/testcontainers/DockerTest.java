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

import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DockerTest {

    private static Concord<?> concord;
    private static final Path testFile = Paths.get("target/testDir/testFile.txt");

    @BeforeClass
    public static void setUp() {
        Concord<?> c = new Concord<>()
                .mode(Concord.Mode.DOCKER)
                .pullPolicy(__ -> false)
                .containerListener(new ContainerListener() {
                    @Override
                    public void beforeStart(ContainerType type) {
                    }

                    @Override
                    public void afterStart(ContainerType type, Container<?> container) {
                        try {
                            // Create test dir + test file
                            Files.createDirectories(testFile.getParent());
                            Files.write(testFile, "Hello, Concord!".getBytes(), StandardOpenOption.CREATE);

                            // Copy dir to container
                            if (type == ContainerType.AGENT) {
                                container.copyFileToContainer(
                                        MountableFile.forHostPath(testFile.getParent()),
                                        Paths.get("/tmp", "testDir").toString());
                            }
                        } catch (Exception ex) {
                            throw new RuntimeException("Failed to set up file to be copied to container");
                        }
                    }
                });

        c.start();

        concord = c;
    }

    @AfterClass
    public static void tearDown() {
        if (concord != null) {
            concord.close();
            concord = null;
        }
    }

    @Test
    public void testApiToken() {
        assertNotNull(concord.environment().apiToken());
    }

    @Test
    public void testSimpleFlow() throws Exception {
        String nameValue = "name_" + System.currentTimeMillis();

        String yml = "" +
                "flows: \n" +
                "  default:\n" +
                "    - log: Hello, ${name}!";

        ConcordProcess p = concord.processes()
                .start(new Payload()
                        .concordYml(yml)
                        .arg("name", nameValue));

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*Hello, " + nameValue + ".*");
    }

    @Test
    public void testTags() throws Exception {
        String tag = "tag_" + System.currentTimeMillis();

        String yml = "" +
                "flows: \n" +
                "  default:\n" +
                "    - log: Hello, Concord!";

        ConcordProcess p1 = concord.processes()
                .start(new Payload()
                        .concordYml(yml)
                        .tag(tag));

        p1.waitForStatus(ProcessEntry.StatusEnum.FINISHED);

        ConcordProcess p2 = concord.processes().get(p1.instanceId());
        p2.assertLog(".*Hello, Concord!.*");

        List<ProcessEntry> l = concord.processes().list(ProcessListQuery.builder()
                .addTags(tag)
                .build());
        assertEquals(1, l.size());
        assertEquals(p2.instanceId(), l.get(0).getInstanceId());
    }

    @Test
    public void testSecrets() throws Exception {
        String yml = "" +
                "flows: \n" +
                "  default:\n" +
                "    - log: ${crypto.exportAsString('Default', 'testSecret', null)}";

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
    public void testProcessLogStreaming() throws Exception {
        String yml = "" +
                "flows: \n" +
                "  default:\n" +
                "    - log: Hello, Concord!";

        ConcordProcess p = concord.processes()
                .create()
                .streamLogs(true)
                .payload(new Payload().concordYml(yml))
                .start();

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*Hello, Concord!.*");
    }

    @Test
    public void testFilePush() throws Exception {
        String yml = "" +
                "flows: \n" +
                "  default:\n" +
                "    - log: \"${resource.asString('/tmp/testDir/testFile.txt')}\"";

        ConcordProcess p = concord.processes()
                .create()
                .streamLogs(true)
                .payload(new Payload().concordYml(yml))
                .start();

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*Hello, Concord!.*");
    }
}
