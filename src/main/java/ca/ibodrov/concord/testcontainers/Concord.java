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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;

public class Concord implements TestRule {

    private GenericContainer<?> server;
    private String adminApiToken;

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
        return new ConcordApiClient("http://localhost:" + server.getFirstMappedPort())
                .setApiKey(adminApiToken);
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
                    agent.start();

                    Concord.this.server = server;
                    Concord.this.adminApiToken = getApiToken(server.getLogs(OutputFrame.OutputType.STDOUT));

                    base.evaluate();
                }
            }
        };
    }

    private static GenericContainer<?> db(Network network) {
        return new GenericContainer<>("library/postgres:10")
                .withEnv("POSTGRES_PASSWORD", "q1")
                .withNetworkAliases("db")
                .withNetwork(network);
    }

    private static GenericContainer<?> server(Network network, Startable db) {
        return new GenericContainer<>("walmartlabs/concord-server:latest")
                .dependsOn(db)
                .withEnv("DB_URL", "jdbc:postgresql://db:5432/postgres")
                .withNetworkAliases("server")
                .withNetwork(network)
                .withExposedPorts(8001)
                .waitingFor(Wait.forHttp("/api/v1/server/ping"));
    }

    private static GenericContainer<?> agent(Network network, Startable server) {
        return new GenericContainer<>("walmartlabs/concord-agent:latest")
                .dependsOn(server)
                .withNetwork(network)
                .withEnv("SERVER_API_BASE_URL", "http://server:8001")
                .withEnv("SERVER_WEBSOCKET_URL", "ws://server:8001/websocket");
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
