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
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.walmartlabs.concord.common.Posix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.function.Supplier;

public class DockerConcordEnvironment implements ConcordEnvironment {

    private static final Logger log = LoggerFactory.getLogger(DockerConcordEnvironment.class);

    private static final String CONCORD_CFG_FILE = "/opt/concord/concord.conf";

    private final GenericContainer<?> db;
    private final GenericContainer<?> server;
    private final GenericContainer<?> agent;

    private final boolean startAgent;

    private final List<ContainerListener> containerListeners;

    private String apiToken;

    public DockerConcordEnvironment(Concord<?> opts) {
        validate(opts);

        Path persistentWorkDir = opts.persistentWorkDir();
        Path configFile = prepareConfigurationFile(persistentWorkDir, opts.extraConfigurationSupplier());
        log.info("Using CONCORD_CFG_FILE={}", configFile);

        ImagePullPolicy pullPolicy = pullPolicy(opts);

        Network network = Network.newNetwork();

        this.db = new GenericContainer<>(opts.dbImage())
                .withEnv("POSTGRES_PASSWORD", "q1")
                .withNetworkAliases("db")
                .withNetwork(network);

        this.server = new GenericContainer<>(opts.serverImage())
                .dependsOn(db)
                .withImagePullPolicy(pullPolicy)
                .withEnv("DB_URL", "jdbc:postgresql://db:5432/postgres")
                .withEnv("CONCORD_CFG_FILE", CONCORD_CFG_FILE)
                .withCopyFileToContainer(MountableFile.forHostPath(configFile, 0644), CONCORD_CFG_FILE)
                .withNetworkAliases("server")
                .withNetwork(network)
                .withExposedPorts(8001)
                .waitingFor(Wait.forHttp("/api/v1/server/ping"));

        String serverExtDirectory = opts.serverExtDirectory();
        if (serverExtDirectory != null) {
            server.withCopyFileToContainer(MountableFile.forHostPath(serverExtDirectory), "/opt/concord/server/ext");
        }

        if (opts.sharedContainerDir() != null) {
            server.withFileSystemBind(opts.sharedContainerDir().toString(), opts.sharedContainerDir().toString());
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

        this.agent = new GenericContainer<>(opts.agentImage())
                .dependsOn(server)
                .withImagePullPolicy(pullPolicy)
                .withNetwork(network)
                .withEnv("SERVER_API_BASE_URL", "http://server:8001")
                .withEnv("SERVER_WEBSOCKET_URL", "ws://server:8001/websocket")
                .withEnv("CONCORD_CFG_FILE", CONCORD_CFG_FILE)
                .withCopyFileToContainer(MountableFile.forHostPath(configFile, 0644), CONCORD_CFG_FILE);

        if (opts.streamAgentLogs()) {
            Slf4jLogConsumer serverLogConsumer = new Slf4jLogConsumer(log);
            agent.withLogConsumer(serverLogConsumer);
        }

        if (opts.sharedContainerDir() != null) {
            agent.withFileSystemBind(opts.sharedContainerDir().toString(), opts.sharedContainerDir().toString());
        }

        String mavenConfigurationPath = opts.mavenConfigurationPath();
        if (mavenConfigurationPath != null) {
            mountMavenConfigurationFile(server, mavenConfigurationPath);
            mountMavenConfigurationFile(agent, mavenConfigurationPath);
        } else {
            if (opts.useLocalMavenRepository()) {
                Path src = Paths.get(System.getProperty("user.home"), ".m2", "repository");
                if (!Files.exists(src) || !Files.isDirectory(src)) {
                    log.warn("Can't mount local Maven repository into containers. The path doesn't exist or not a directory: {}", src.toAbsolutePath());
                } else {
                    String hostPath = src.toAbsolutePath().toString();
                    server.withFileSystemBind(hostPath, "/host/.m2/repository");
                    agent.withFileSystemBind(hostPath, "/host/.m2/repository");
                }
            }

            String cfg = createMavenConfigurationFile(opts).toAbsolutePath().toString();
            mountMavenConfigurationFile(server, cfg);
            mountMavenConfigurationFile(agent, cfg);
        }

        List<Startable> dependsOn = opts.dependsOn();
        if (dependsOn != null && !dependsOn.isEmpty()) {
            db.dependsOn(dependsOn);
            server.dependsOn(dependsOn);
            agent.dependsOn(dependsOn);
        }

        this.startAgent = opts.startAgent();

        if (persistentWorkDir != null) {
            try {
                Files.setPosixFilePermissions(persistentWorkDir, Posix.posix(0777));
            } catch (IOException e) {
                throw new RuntimeException("Can't set persistentWorkDir permissions: " + persistentWorkDir, e);
            }
            String path = persistentWorkDir.toAbsolutePath().toString();
            this.agent.addFileSystemBind(path, path, BindMode.READ_WRITE);
        }

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
        startContainer(ContainerType.DB, this.db);
        startContainer(ContainerType.SERVER, this.server);

        if (startAgent) {
            startContainer(ContainerType.AGENT, this.agent);
        }
    }

    @Override
    public void stop() {
        this.agent.stop();
        this.server.stop();
        this.db.stop();
    }

    private static Path prepareConfigurationFile(Path persistentWorkDir, Supplier<String> extraConfigurationSupplier) {
        try {
            Path dst = Files.createTempFile("server", ".dst");
            String s = Resources.toString(DockerConcordEnvironment.class.getResource("docker/concord.conf"), Charsets.UTF_8);
            s = s.replace("%%agentToken%%", Utils.randomToken());
            s = s.replace("%%persistentWorkDir%%", persistentWorkDir != null ? persistentWorkDir.toString() : "");
            s = s.replace("%%extra%%", extraConfigurationSupplier != null ? extraConfigurationSupplier.get() : "");
            Files.write(dst, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            return dst.toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startContainer(ContainerType t, GenericContainer<?> c) {
        fireBeforeStart(t);
        c.start();
        fireAfterStart(t, c);
    }

    private void fireBeforeStart(ContainerType type) {
        this.containerListeners.forEach(l -> l.beforeStart(type));
    }

    private void fireAfterStart(ContainerType type, Container<?> container) {
        this.containerListeners.forEach(l -> l.afterStart(type, container));
    }

    private static ImagePullPolicy pullPolicy(Concord opts) {
        ImagePullPolicy p = opts.pullPolicy();

        if (p == null) {
            if (requiresAlwaysPull(opts.serverImage()) || requiresAlwaysPull(opts.agentImage())) {
                return PullPolicy.alwaysPull();
            } else {
                return PullPolicy.defaultPolicy();
            }
        }

        return p;
    }

    private static boolean requiresAlwaysPull(String image) {
        return image.contains(":latest") || image.indexOf(":") < 0;
    }

    private static String getApiToken(String s) {
        String msg = "API token created for user 'admin': ";
        int start = s.indexOf(msg);
        if (start >= 0) {
            int end = s.indexOf('\n', start);
            if (end >= 0) {
                return s.substring(start + msg.length(), end);
            }
        }

        throw new IllegalArgumentException("Can't find the API token in logs");
    }

    private static void validate(Concord<?> opts) {
        if (opts.apiToken() != null) {
            log.warn("Can't specify 'apiToken' value when using Mode.DOCKER");
        }

        if ((opts.useMavenCentral() || opts.useLocalMavenRepository() || opts.extraMavenRepositories() != null) && opts.mavenConfigurationPath() != null) {
            log.warn("The 'mavenConfigurationPath' option is mutually exclusive with 'useLocalMavenRepository', 'useMavenCentral' or 'extraMavenRepositories'.");
        }

        Path persistentWorkDir = opts.persistentWorkDir();
        if (persistentWorkDir != null && !Files.exists(persistentWorkDir)) {
            throw new IllegalStateException("The specified 'persistentWorkDir' doesn't exist: " + persistentWorkDir);
        }
    }

    private static Path createMavenConfigurationFile(Concord<?> opts) {
        List<Map<String, Object>> repositories = new ArrayList<>();

        if (opts.useLocalMavenRepository()) {
            Map<String, Object> local = new HashMap<>();
            local.put("id", "local");
            local.put("url", "file:///host/.m2/repository");
            local.put("snapshotPolicy", Collections.singletonMap("updatePolicy", "always"));
            repositories.add(local);
        }

        if (opts.useMavenCentral()) {
            Map<String, Object> central = new HashMap<>();
            central.put("id", "central");
            central.put("url", "https://repo.maven.apache.org/maven2/");
            repositories.add(central);
        }

        if (opts.extraMavenRepositories() != null) {
            repositories.addAll(opts.extraMavenRepositories());
        }

        Map<String, Object> m = Collections.singletonMap("repositories", repositories);

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
