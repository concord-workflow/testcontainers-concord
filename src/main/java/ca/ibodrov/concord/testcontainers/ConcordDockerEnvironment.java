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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;

public class ConcordDockerEnvironment implements ConcordEnvironment {

    private static final Logger log = LoggerFactory.getLogger(ConcordDockerEnvironment.class);

    private final Network network;
    private final GenericContainer<?> db;
    private final GenericContainer<?> server;
    private final GenericContainer<?> agent;

    private final boolean startAgent;

    private String apiToken;

    public ConcordDockerEnvironment(Concord opts) {
        ImagePullPolicy pullPolicy = pullPolicy(opts);

        this.network = Network.newNetwork();

        this.db = new GenericContainer<>("library/postgres:10")
                .withEnv("POSTGRES_PASSWORD", "q1")
                .withNetworkAliases("db")
                .withNetwork(network);

        String version = opts.version();
        this.server = new GenericContainer<>("walmartlabs/concord-server:" + version)
                .dependsOn(db)
                .withImagePullPolicy(pullPolicy)
                .withEnv("DB_URL", "jdbc:postgresql://db:5432/postgres")
                .withNetworkAliases("server")
                .withNetwork(network)
                .withExposedPorts(8001)
                .waitingFor(Wait.forHttp("/api/v1/server/ping"));

        String serverExtDirectory = opts.serverExtDirectory();
        if (serverExtDirectory != null) {
            server.withFileSystemBind(serverExtDirectory, "/opt/concord/server/ext", BindMode.READ_ONLY);
        }

        String serverClassesDirectory = opts.serverClassesDirectory();
        if (serverClassesDirectory != null) {
            String src = serverClassesDirectory;
            if (!src.startsWith("/")) {
                src = System.getProperty("user.dir") + "/" + src;
            }
            server.withFileSystemBind(src, "/opt/concord/server/classes/", BindMode.READ_ONLY);
        }

        if (opts.streamServerLogs()) {
            Slf4jLogConsumer serverLogConsumer = new Slf4jLogConsumer(log);
            server.followOutput(serverLogConsumer);
        }

        this.agent = new GenericContainer<>("walmartlabs/concord-agent:" + opts.version())
                .dependsOn(server)
                .withImagePullPolicy(pullPolicy)
                .withNetwork(network)
                .withEnv("SERVER_API_BASE_URL", "http://server:8001")
                .withEnv("SERVER_WEBSOCKET_URL", "ws://server:8001/websocket");

        if (opts.streamAgentLogs()) {
            Slf4jLogConsumer serverLogConsumer = new Slf4jLogConsumer(log);
            agent.followOutput(serverLogConsumer);
        }

        this.startAgent = opts.startAgent();
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
        this.db.start();
        this.server.start();

        if (startAgent) {
            this.agent.start();
        }
    }

    @Override
    public void stop() {
        this.agent.stop();
        this.server.stop();
        this.db.stop();
    }

    private static ImagePullPolicy pullPolicy(Concord opts) {
        if ("latest".equals(opts.version())) {
            return PullPolicy.alwaysPull();
        }

        return PullPolicy.defaultPolicy();
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
}
