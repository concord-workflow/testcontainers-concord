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
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;

import java.util.Map;

public class Concord implements TestRule {

    private String version = "latest";
    private String serverImage = "walmartlabs/concord-server";
    private String agentImage = "walmartlabs/concord-agent";
    private String serverExtDirectory;
    private String serverClassesDirectory;
    private String mavenConfigurationPath;
    private boolean useLocalMavenRepository;
    private boolean streamServerLogs;
    private boolean streamAgentLogs;
    private boolean localMode;
    private String pathToRunnerV1 = "target/runner-v1.jar";
    private String pathToRunnerV2;
    private boolean startAgent = true;
    private ImagePullPolicy pullPolicy;

    private ConcordEnvironment environment;

    /**
     * Returns the server's API port, e.g. 8001.
     */
    public int apiPort() {
        return environment.apiPort();
    }

    /**
     * Returns the server API prefix, e.g. http://localhost:8001
     */
    public String apiUrlPrefix() {
        return "http://localhost:" + environment.apiPort();
    }

    /**
     * Returns the default admin API token.
     */
    public String adminApiToken() {
        return environment.apiToken();
    }

    /**
     * Creates a new API Client using the default API token.
     *
     * @see #adminApiToken()
     */
    public ApiClient apiClient() {
        return new ConcordApiClient(apiUrlPrefix())
                .setApiKey(environment.apiToken());
    }

    public String version() {
        return version;
    }

    /**
     * The version of Concord to be used for testing.
     */
    public Concord version(String version) {
        this.version = version;
        return this;
    }

    public String serverImage() {
        return serverImage;
    }

    /**
     * Server docker image name without version.
     */
    public Concord serverImage(String image) {
        this.serverImage = image;
        return this;
    }

    public String agentImage() {
        return agentImage;
    }

    /**
     * Agent docker image name without version.
     */
    public Concord agentImage(String image) {
        this.agentImage = image;
        return this;
    }

    public String serverExtDirectory() {
        return serverExtDirectory;
    }

    /**
     * Path to the directory to be mounted as the server's "ext" directory.
     * E.g. to mount 3rd-party server plugins.
     */
    public Concord serverExtDirectory(String serverExtDirectory) {
        this.serverExtDirectory = serverExtDirectory;
        return this;
    }

    public String serverClassesDirectory() {
        return serverClassesDirectory;
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

    public String mavenConfigurationPath() {
        return mavenConfigurationPath;
    }

    /**
     * Path to {@code mvn.json} to use with the server and agent containers.
     * Doesn't work with {@link #localMode}.
     */
    public Concord mavenConfigurationPath(String mavenConfigurationPath) {
        this.mavenConfigurationPath = mavenConfigurationPath;
        return this;
    }

    public boolean useLocalMavenRepository() {
        return this.useLocalMavenRepository;
    }

    /**
     * If {@code true} the the local maven repository {@code $HOME/.m2/repository}
     * will be mounted into the server and agent containers.
     * Doesn't work with {@link #localMode}.
     */
    public Concord useLocalMavenRepository(boolean useLocalMavenRepository) {
        this.useLocalMavenRepository = useLocalMavenRepository;
        return this;
    }

    public boolean streamServerLogs() {
        return streamServerLogs;
    }

    /**
     * Stream the server logs to the console.
     */
    public Concord streamServerLogs(boolean streamServerLogs) {
        this.streamServerLogs = streamServerLogs;
        return this;
    }

    public boolean streamAgentLogs() {
        return streamAgentLogs;
    }

    /**
     * Stream the agent logs to the console.
     */
    public Concord streamAgentLogs(boolean streamAgentLogs) {
        this.streamAgentLogs = streamAgentLogs;
        return this;
    }

    /**
     * Enable local mode. In the local mode the Server and the Agent are
     * started in the current JVM directly, without using Docker.
     * Only the database is started in a container.
     * <p/>
     * Useful for debugging, i.e. it is possible to set a breakpoint in
     * the server's (or a server plugin's) code while running a test.
     * <p/>
     * Default is {@code false}.
     */
    public Concord localMode(boolean localMode) {
        this.localMode = localMode;
        return this;
    }

    public String pathToRunnerV1() {
        return pathToRunnerV1;
    }

    public String pathToRunnerV2() {
        return pathToRunnerV2;
    }

    /**
     * Path to the runner v1 JAR to use when {@link #localMode(boolean)}
     * is enabled.
     * <p/>
     * Typically points to the runner JAR file copied by Maven into
     * the target directory.
     */
    public Concord pathToRunnerV1(String pathToRunnerV1) {
        this.pathToRunnerV1 = pathToRunnerV1;
        return this;
    }

    /**
     * Path to the runner v2 JAR to use when {@link #localMode(boolean)}
     * is enabled.
     * <p/>
     * Typically points to the runner JAR file copied by Maven into
     * the target directory.
     */
    public Concord pathToRunnerV2(String pathToRunnerV2) {
        this.pathToRunnerV2 = pathToRunnerV2;
        return this;
    }

    public boolean startAgent() {
        return startAgent;
    }

    /**
     * Don't start the Agent if {@code false}. Default is {@code true}.
     */
    public Concord startAgent(boolean startAgent) {
        this.startAgent = startAgent;
        return this;
    }

    /**
     * Docker image pull policy. If {@link #version()} is "latest"
     * {@link PullPolicy#alwaysPull()} is used by default.
     */
    public ImagePullPolicy pullPolicy() {
        return pullPolicy;
    }

    public Concord pullPolicy(ImagePullPolicy pullPolicy) {
        this.pullPolicy = pullPolicy;
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
                try (ConcordEnvironment env = createEnvironment()) {
                    env.start();
                    Concord.this.environment = env;
                    base.evaluate();
                }
            }
        };
    }

    private ConcordEnvironment createEnvironment() {
        if (localMode) {
            return new ConcordLocalEnvironment(this);
        }

        return new ConcordDockerEnvironment(this);
    }
}
