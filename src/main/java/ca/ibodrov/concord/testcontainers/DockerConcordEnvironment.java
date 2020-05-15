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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

public class DockerConcordEnvironment implements ConcordEnvironment {

    private static final Logger log = LoggerFactory.getLogger(DockerConcordEnvironment.class);

    private final GenericContainer<?> db;
    private final GenericContainer<?> server;
    private final GenericContainer<?> agent;

    private final boolean startAgent;

    private final List<ContainerListener> containerListeners;

    private String apiToken;

    public DockerConcordEnvironment(Concord opts) {
        validate(opts);

        ImagePullPolicy pullPolicy = pullPolicy(opts);

        Network network = Network.newNetwork();

        this.db = new GenericContainer<>(opts.dbImage())
                .withEnv("POSTGRES_PASSWORD", "q1")
                .withNetworkAliases("db")
                .withNetwork(network);

        String version = opts.version();
        this.server = new GenericContainer<>(opts.serverImage() + ":" + version)
                .dependsOn(db)
                .withImagePullPolicy(pullPolicy)
                .withEnv("DB_URL", "jdbc:postgresql://db:5432/postgres")
                .withNetworkAliases("server")
                .withNetwork(network)
                .withExposedPorts(8001)
                .waitingFor(Wait.forHttp("/api/v1/server/ping"));

        String serverExtDirectory = opts.serverExtDirectory();
        if (serverExtDirectory != null) {
            server.withCopyFileToContainer(MountableFile.forHostPath(serverExtDirectory), "/opt/concord/server/ext");
        }

        String serverClassesDirectory = opts.serverClassesDirectory();
        if (serverClassesDirectory != null) {
            String src = serverClassesDirectory;
            if (!src.startsWith("/")) {
                src = System.getProperty("user.dir") + "/" + src;
            }
            server.withCopyFileToContainer(MountableFile.forHostPath(src), "/opt/concord/server/classes/");
        }

        if (opts.streamServerLogs()) {
            Slf4jLogConsumer serverLogConsumer = new Slf4jLogConsumer(log);
            server.withLogConsumer(serverLogConsumer);
        }

        this.agent = new GenericContainer<>(opts.agentImage() + ":" + opts.version())
                .dependsOn(server)
                .withImagePullPolicy(pullPolicy)
                .withNetwork(network)
                .withEnv("SERVER_API_BASE_URL", "http://server:8001")
                .withEnv("SERVER_WEBSOCKET_URL", "ws://server:8001/websocket");

        if (opts.streamAgentLogs()) {
            Slf4jLogConsumer serverLogConsumer = new Slf4jLogConsumer(log);
            agent.withLogConsumer(serverLogConsumer);
        }

        String mavenConfigurationPath = opts.mavenConfigurationPath();
        if (mavenConfigurationPath != null) {
            mountMavenConfigurationFile(server, mavenConfigurationPath);
            mountMavenConfigurationFile(agent, mavenConfigurationPath);
        }

        if (opts.useLocalMavenRepository()) {
            Path src = Paths.get(System.getProperty("user.home"), ".m2", "repository");
            if (!Files.exists(src) || !Files.isDirectory(src)) {
                log.warn("Can't mount local Maven repository into containers. The path doesn't exist or not a directory: {}", src.toAbsolutePath());
            } else {
                String hostPath = src.toAbsolutePath().toString();
                server.withFileSystemBind(hostPath, "/host/.m2/repository");
                agent.withFileSystemBind(hostPath, "/host/.m2/repository");
            }

            String cfg = createMavenConfigurationFile().toAbsolutePath().toString();
            mountMavenConfigurationFile(server, cfg);
            mountMavenConfigurationFile(agent, cfg);
        }

        this.startAgent = opts.startAgent();

        this.containerListeners = opts.containerListeners() != null ? new ArrayList<>(opts.containerListeners()) : Collections.emptyList();
    }

    @Override
    public int apiPort() {
        if (!server.isRunning()) {
            throw new IllegalStateException("Requires a running Server container.");
        }

        return server.getFirstMappedPort();
    }

    @Override
    public String apiToken() {
        if (!server.isRunning()) {
            throw new IllegalStateException("Requires a running Server container.");
        }

        synchronized (this) {
            if (apiToken != null) {
                return apiToken;
            }

            return apiToken = getApiToken(server.getLogs(OutputFrame.OutputType.STDOUT));
        }
    }

    @Override
    public void start() {
        fireBeforeStart(ContainerType.DB);
        this.db.start();

        fireBeforeStart(ContainerType.SERVER);
        this.server.start();

        if (startAgent) {
            fireBeforeStart(ContainerType.AGENT);
            this.agent.start();
        }
    }

    @Override
    public void stop() {
        this.agent.stop();
        this.server.stop();
        this.db.stop();
    }

    private void fireBeforeStart(ContainerType type) {
        this.containerListeners.forEach(l -> l.beforeStart(type));
    }

    private static ImagePullPolicy pullPolicy(Concord opts) {
        ImagePullPolicy p = opts.pullPolicy();

        if (p == null) {
            if ("latest".equals(opts.version())) {
                return PullPolicy.alwaysPull();
            } else {
                return PullPolicy.defaultPolicy();
            }
        }

        return p;
    }

    private static String getApiToken(String s) {
        String msg = "API token created: ";
        int start = s.indexOf(msg);
        if (start >= 0) {
            int end = s.indexOf('\n', start);
            if (end >= 0) {
                return s.substring(start + msg.length(), end);
            }
        }

        throw new IllegalArgumentException("Can't find the API token in logs");
    }

    private static void validate(Concord opts) {
        if (opts.apiToken() != null) {
            log.warn("Can't specify 'apiToken' value when using Mode.DOCKER");
        }

        if (opts.useLocalMavenRepository() && opts.mavenConfigurationPath() != null) {
            log.warn("Can't use 'useLocalMavenRepository' and a 'mavenConfigurationPath' simultaneously.");
        }
    }

    private static Path createMavenConfigurationFile() {
        Map<String, Object> repo = new HashMap<>();
        repo.put("id", "local");
        repo.put("url", "file:///host/.m2/repository");
        repo.put("snapshotPolicy", Collections.singletonMap("updatePolicy", "always"));

        Map<String, Object> m = Collections.singletonMap("repositories",
                Collections.singletonList(repo));

        try {
            Path dst = Files.createTempFile("mvn", ".json");
            Files.write(dst, new ObjectMapper().writeValueAsBytes(m), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            Files.setPosixFilePermissions(dst, PosixFilePermissions.fromString("rw-r--r--"));
            return dst;
        } catch (IOException e) {
            throw new RuntimeException("Error while creating a Maven configuration file: " + e.getMessage(), e);
        }
    }

    private static void mountMavenConfigurationFile(GenericContainer<?> container, String src) {
        container.withEnv("CONCORD_MAVEN_CFG", "/opt/concord/conf/mvn.json")
                .withCopyFileToContainer(MountableFile.forHostPath(src), "/opt/concord/conf/mvn.json");
    }
}
