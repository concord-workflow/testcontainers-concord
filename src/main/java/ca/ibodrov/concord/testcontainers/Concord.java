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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ConcordApiClient;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
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
import org.testcontainers.lifecycle.Startable;

import java.util.HashMap;
import java.util.Map;

public class Concord implements TestRule {

    private static Logger logger = LoggerFactory.getLogger(Concord.class);

    private Map<String, String> serverExtDirectories = new HashMap<>();
    private String version = "latest";
    private String serverClassesDirectory;

    private GenericContainer<?> server;
    private String adminApiToken;

    private boolean streamServerLogs;
    private boolean streamAgentLogs;

    /**
     * Return the server API prefix, e.g. http://localhost:8001
     */
    public String apiUrlPrefix() {
        if (!server.isRunning()) {
            throw new IllegalStateException("Requires a running Concord server.");
        }

        return "http://localhost:" + server.getFirstMappedPort();
    }

    /**
     * Returns the default admin API token.
     */
    public String adminApiToken() {
        return adminApiToken;
    }

    /**
     * Creates a new API Client using the default API token.
     *
     * @see #adminApiToken()
     */
    public ApiClient apiClient() {
        return new ConcordApiClient(apiUrlPrefix())
                .setApiKey(adminApiToken);
    }

    /**
     * The version of Concord to be used for testing.
     */
    public Concord version(String version) {
        this.version = version;
        return this;
    }

    /**
     * Path to the directory to be mounted as the server's "ext" directory.
     * E.g. to mount 3rd-party server plugins.
     */
    public Concord serverExtDirectory(String name, String source) {
        serverExtDirectories.put(name, source);
        return this;
    }

    /**
     * Path to the directory to be mounted as the server's additional classes directory.
     * Useful to test plugins without building any JARs, just by mounting the target/classes
     * directory directly.
     */
    public Concord serverClassesDirectory(String serverClassesDirectory) {
        this.serverClassesDirectory = serverClassesDirectory;
        return this;
    }

    /**
     * Stream the server logs to the console.
     */
    public Concord streamServerLogs(boolean streamServerLogs) {
        this.streamServerLogs = streamServerLogs;
        return this;
    }

    /**
     * Stream the agent logs to the console.
     */
    public Concord streamAgentLogs(boolean streamAgentLogs) {
        this.streamAgentLogs = streamAgentLogs;
        return this;
    }

    /**
     * Utilities to work with Concord processes.
     */
    public Processes processes() {
        return new Processes(apiClient());
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (Network network = Network.newNetwork();
                     GenericContainer<?> db = db(network);
                     GenericContainer<?> server = server(network, db);
                     GenericContainer<?> agent = agent(network, db)) {

                    db.start();

                    server.start();
                    if (streamServerLogs) {
                        Slf4jLogConsumer serverLogConsumer = new Slf4jLogConsumer(logger);
                        server.followOutput(serverLogConsumer);
                    }

                    agent.start();
                    if (streamAgentLogs) {
                        Slf4jLogConsumer agentLogConsumer = new Slf4jLogConsumer(logger);
                        server.followOutput(agentLogConsumer);
                    }

                    Concord.this.server = server;
                    Concord.this.adminApiToken = getApiToken(server.getLogs(OutputFrame.OutputType.STDOUT));

                    base.evaluate();
                }
            }
        };
    }

    private GenericContainer<?> db(Network network) {
        return new GenericContainer<>("library/postgres:10")
                .withEnv("POSTGRES_PASSWORD", "q1")
                .withNetworkAliases("db")
                .withNetwork(network);
    }

    private GenericContainer<?> server(Network network, Startable db) {
        GenericContainer<?> c = new GenericContainer<>("walmartlabs/concord-server:" + version)
                .dependsOn(db)
                .withImagePullPolicy(pullPolicy())
                .withEnv("DB_URL", "jdbc:postgresql://db:5432/postgres")
                .withNetworkAliases("server")
                .withNetwork(network)
                .withExposedPorts(8001)
                .waitingFor(Wait.forHttp("/api/v1/server/ping"));

        serverExtDirectories.forEach((name, src) -> c.withFileSystemBind(src, "/opt/concord/server/ext/" + name, BindMode.READ_ONLY));

        if (serverClassesDirectory != null) {
            String src = serverClassesDirectory;
            if (!src.startsWith("/")) {
                src = System.getProperty("user.dir") + "/" + src;
            }
            c.withFileSystemBind(src, "/opt/concord/server/classes/", BindMode.READ_ONLY);
        }

        return c;
    }

    private GenericContainer<?> agent(Network network, Startable server) {
        return new GenericContainer<>("walmartlabs/concord-agent:" + version)
                .dependsOn(server)
                .withImagePullPolicy(pullPolicy())
                .withNetwork(network)
                .withEnv("SERVER_API_BASE_URL", "http://server:8001")
                .withEnv("SERVER_WEBSOCKET_URL", "ws://server:8001/websocket");
    }

    private ImagePullPolicy pullPolicy() {
        if(version.equals("latest")) {
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
