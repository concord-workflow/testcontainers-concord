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
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.walmartlabs.concord.agent.Agent;
import com.walmartlabs.concord.server.ConcordServer;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

public class ConcordLocalEnvironment implements ConcordEnvironment {

    private static final Logger log = LoggerFactory.getLogger(ConcordLocalEnvironment.class);

    private final GenericContainer<?> db;
    private final String apiToken;

    private ConcordServer server;
    private Agent agent;

    public ConcordLocalEnvironment() {
        this.db = new GenericContainer<>("library/postgres:10")
                .withEnv("POSTGRES_PASSWORD", "q1")
                .withNetworkAliases("db")
                .withExposedPorts(5432);

        this.apiToken = randomToken();
    }

    @Override
    public int apiPort() {
        return 8001;
    }

    @Override
    public String apiToken() {
        return apiToken;
    }

    @Override
    public void start() {
        this.db.start();

        try {
            Path conf = Files.createTempFile("server", ".conf");

            String s = Resources.toString(ConcordLocalEnvironment.class.getResource("local/concord.conf"), Charsets.UTF_8);
            s = s.replaceAll("DB_URL", "jdbc:postgresql://localhost:" + db.getFirstMappedPort() + "/postgres");
            s = s.replaceAll("API_TOKEN", apiToken);
            Files.write(conf, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);

            System.setProperty("ollie.conf", conf.toAbsolutePath().toString());

            this.server = ConcordServer.start();

            waitForHttp("/api/v1/server/ping", 60000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Injector injector = server.getInjector();
            this.agent = injector.getInstance(Agent.class);

            agent.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        try {
            this.agent.stop();
        } catch (Exception e) {
            log.warn("Error while stopping the Agent: {}", e.getMessage(), e);
        }

        try {
            this.server.stop();
        } catch (Exception e) {
            log.warn("Error while stopping the Server: {}", e.getMessage(), e);
        }

        this.db.stop();
    }

    private static String randomToken() {
        byte[] ab = new byte[16];
        ThreadLocalRandom.current().nextBytes(ab);

        Base64.Encoder e = Base64.getEncoder().withoutPadding();
        return e.encodeToString(ab);
    }

    private void waitForHttp(String url, long timeout) throws IOException {
        long t0 = System.currentTimeMillis();

        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url("http://localhost:" + apiPort() + url)
                .build();

        while (true) {
            long t1 = System.currentTimeMillis();
            if (t1 - t0 >= timeout) {
                throw new IllegalStateException("Timeout waiting for " + url);
            }

            Call call = client.newCall(req);
            Response resp = call.execute();
            try {
                if (resp.code() == 200) {
                    break;
                }
            } finally {
                resp.body().close();
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
