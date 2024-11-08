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

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import com.walmartlabs.concord.agent.Agent;
import com.walmartlabs.concord.server.ConcordServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startable;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class LocalConcordEnvironment implements ConcordEnvironment {

    private static final Logger log = LoggerFactory.getLogger(LocalConcordEnvironment.class);

    private final GenericContainer<?> db;
    private final String apiToken;
    private final String agentToken;
    private final String pathToRunnerV1;
    private final String pathToRunnerV2;
    private final boolean startAgent;
    private final Supplier<String> extraConfigurationSupplier;

    private int apiPort;

    private ConcordServer server;
    private Agent agent;

    public LocalConcordEnvironment(Concord<?> opts) {
        validate(opts);

        this.db = new GenericContainer<>("library/postgres:10")
                .withEnv("POSTGRES_PASSWORD", "q1")
                .withNetworkAliases("db")
                .withExposedPorts(5432);

        String dbInitScriptPath = opts.dbInitScriptPath();
        if (dbInitScriptPath != null) {
            DatabaseInit.addInitScriptFromClassPath(db, dbInitScriptPath, "init.sql");
        }

        if (opts.streamDbLogs()) {
            Slf4jLogConsumer dbLogConsumer = new Slf4jLogConsumer(log);
            db.withLogConsumer(dbLogConsumer);
        }

        // in the LOCAL mode there's only one container - the DB
        // so it's the only thing that can "depend on" anything
        List<Startable> dependsOn = opts.dependsOn();
        if (dependsOn != null && !dependsOn.isEmpty()) {
            db.dependsOn(dependsOn);
        }

        this.apiToken = Utils.randomToken();
        this.agentToken = Utils.randomToken();

        this.pathToRunnerV1 = opts.pathToRunnerV1();
        this.pathToRunnerV2 = opts.pathToRunnerV2();

        this.startAgent = opts.startAgent();

        this.extraConfigurationSupplier = Optional.ofNullable(opts.extraConfigurationSupplier()).orElse(() -> "");
    }

    @Override
    public int apiPort() {
        return apiPort;
    }

    @Override
    public String apiToken() {
        return apiToken;
    }

    public ConcordServer server() {
        return server;
    }

    @Override
    public void start() {
        apiPort = Utils.reservePort(8001);

        assertRunnerJar(startAgent, pathToRunnerV1, pathToRunnerV2);

        this.db.start();

        try {
            Path conf = prepareConfigurationFile();
            System.setProperty("ollie.conf", conf.toAbsolutePath().toString());

            this.server = ConcordServer.withAutoWiring().start();

            waitForHttp("http://localhost:" + apiPort() + "/api/v1/server/ping", 60000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (startAgent) {
            try {
                Injector injector = server.getInjector();
                this.agent = injector.getInstance(Agent.class);

                agent.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void stop() {
        if (this.agent != null) {
            try {
                this.agent.stop();
            } catch (Exception e) {
                log.warn("Error while stopping the Agent: {}", e.getMessage(), e);
            }
        }

        if (this.server != null) {
            try {
                this.server.stop();
            } catch (Exception e) {
                log.warn("Error while stopping the Server: {}", e.getMessage(), e);
            }
        }

        if (this.db != null) {
            this.db.stop();
        }
    }

    private Path prepareConfigurationFile() throws IOException {
        Path dst = Files.createTempFile("server", ".dst");

        String s = Resources.toString(LocalConcordEnvironment.class.getResource("local/concord.conf"), Charsets.UTF_8);
        s = s.replaceAll("%%extra%%", extraConfigurationSupplier.get());
        s = s.replaceAll("SERVER_PORT", String.valueOf(apiPort));
        s = s.replaceAll("DB_URL", "jdbc:postgresql://localhost:" + db.getFirstMappedPort() + "/postgres");
        s = s.replaceAll("API_TOKEN", apiToken);
        s = s.replaceAll("AGENT_TOKEN", agentToken);
        s = s.replaceAll("JAVA_CMD", getJavaCmd());
        if (pathToRunnerV1 != null) {
            s = s.replaceAll("RUNNER_V1_PATH", pathToRunnerV1);
        }
        if (pathToRunnerV2 != null) {
            s = s.replaceAll("RUNNER_V2_PATH", pathToRunnerV2);
        }
        Files.write(dst, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);

        return dst;
    }

    private static void validate(Concord opts) {
        if (opts.apiToken() != null) {
            log.warn("Can't specify 'apiToken' value when using Mode.DOCKER");
        }
    }

    private static void waitForHttp(String urlStr, long timeout) throws IOException {
        long t0 = System.currentTimeMillis();

        URL url = new URL(urlStr);

        while (true) {
            long t1 = System.currentTimeMillis();
            if (t1 - t0 >= timeout) {
                throw new IllegalStateException("Timeout waiting for " + urlStr);
            }

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            try {
                int status = con.getResponseCode();
                if (status == 200) {
                    break;
                }
            } catch (ConnectException e) {
                // ignore
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void assertRunnerJar(boolean startAgent, String pathToRunnerV1, String pathToRunnerV2) {
        if (!startAgent) {
            return;
        }

        boolean v1Exists = pathToRunnerV1 != null && Files.exists(Paths.get(pathToRunnerV1));
        boolean v2Exists = pathToRunnerV2 != null && Files.exists(Paths.get(pathToRunnerV2));

        if (!v1Exists && !v2Exists) {
            throw new IllegalStateException("The agent running in the LOCAL mode requires either" +
                    "a path to runner-v1 or runner-v2 (or both) specified. The specified paths must exist. " +
                    "If you don't care about running actual Concord processes, disable the agent with Concord#startAgent(false).");
        }
    }

    private static String getJavaCmd() {
        return Paths.get(System.getProperties().getProperty("java.home"), "bin", "java")
                .toAbsolutePath()
                .toString();
    }
}
