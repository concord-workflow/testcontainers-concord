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
import org.jetbrains.annotations.NotNull;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.lifecycle.Startable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings({"unused", "unchecked"})
public class Concord<T extends Concord<T>> implements AutoCloseable {

    private boolean startAgent = true;
    private boolean streamAgentLogs;
    private boolean streamServerLogs;
    private boolean useLocalMavenRepository;
    private boolean useMavenCentral = true;

    private ImagePullPolicy pullPolicy;
    private Mode mode = Mode.DOCKER;

    private String dbImage = "library/postgres:10";
    private String agentImage = "walmartlabs/concord-agent";
    private String serverImage = "walmartlabs/concord-server";

    private String apiBaseUrl = "http://localhost:8001";
    private String apiToken;
    private String mavenConfigurationPath;
    private String pathToRunnerV1 = "target/runner-v1.jar";
    private String pathToRunnerV2;
    private String serverClassesDirectory;
    private String serverExtDirectory;
    private Supplier<String> extraConfigurationSupplier;
    private List<Startable> dependsOn;
    private Path sharedContainerDir;

    private List<ContainerListener> containerListeners;

    private ConcordEnvironment environment;

    /**
     * Starts a Concord instance using the current configuration.
     */
    public void start() {
        initEnvironment();
        environment.start();
    }

    /**
     * Stops the current Concord instance.
     */
    @Override
    public void close() {
        environment.stop();
        ProcessLogStreamers.stop();
    }

    public ConcordEnvironment environment() {
        return environment;
    }

    public ConcordEnvironment initEnvironment() {
        this.environment = createEnvironment();
        return this.environment;
    }

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
     * Creates a new API Client using the currently configured (or generated) API token.
     */
    public ApiClient apiClient() {
        return new ConcordApiClient(apiUrlPrefix())
                .setApiKey(environment.apiToken());
    }

    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    /**
     * Sets the base URL of a remote T API server.
     * Required for {@link Mode#REMOTE}.
     */
    public T apiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        return (T) this;
    }

    public String apiToken() {
        return apiToken;
    }

    /**
     * Sets the default API token to use with a remote T API server.
     * Required for {@link Mode#REMOTE}.
     */
    public T apiToken(String apiToken) {
        this.apiToken = apiToken;
        return (T) this;
    }

    public Mode mode() {
        return mode;
    }

    /**
     * Sets the execution mode.
     *
     * @see Mode
     */
    public T mode(Mode mode) {
        this.mode = mode;
        return (T) this;
    }

    public String dbImage() {
        return dbImage;
    }

    public T dbImage(String dbImage) {
        this.dbImage = dbImage;
        return (T) this;
    }

    public String serverImage() {
        return serverImage;
    }

    /**
     * Server docker image name without version.
     */
    public T serverImage(String image) {
        this.serverImage = image;
        return (T) this;
    }

    public String agentImage() {
        return agentImage;
    }

    /**
     * Agent docker image name without version.
     */
    public T agentImage(String image) {
        this.agentImage = image;
        return (T) this;
    }

    public String serverExtDirectory() {
        return serverExtDirectory;
    }

    /**
     * Path to the directory to be mounted as the server's "ext" directory.
     * E.g. to mount 3rd-party server plugins.
     */
    public T serverExtDirectory(String serverExtDirectory) {
        this.serverExtDirectory = serverExtDirectory;
        return (T) this;
    }

    public Supplier<String> extraConfigurationSupplier() {
        return extraConfigurationSupplier;
    }

    /**
     * Additional configuration parameters to add to the server's and
     * the agent's configuration files.
     * The provided {@link Supplier} called before the containers start.
     * Only for {@link Mode#LOCAL}.
     */
    public T extraConfigurationSupplier(Supplier<String> extraConfigurationSupplier) {
        this.extraConfigurationSupplier = extraConfigurationSupplier;
        return (T) this;
    }

    public String serverClassesDirectory() {
        return serverClassesDirectory;
    }

    /**
     * Path to the directory to be mounted as the server's additional classes directory.
     * Useful to test plugins without building any JARs, just by mounting the target/classes
     * directory directly.
     */
    public T serverClassesDirectory(String serverClassesDirectory) {
        this.serverClassesDirectory = serverClassesDirectory;
        return (T) this;
    }

    public String mavenConfigurationPath() {
        return mavenConfigurationPath;
    }

    /**
     * Path to {@code mvn.json} to use with the server and agent containers.
     * Doesn't work with {@link Mode#LOCAL} or {@link Mode#REMOTE}.
     */
    public T mavenConfigurationPath(String mavenConfigurationPath) {
        this.mavenConfigurationPath = mavenConfigurationPath;
        return (T) this;
    }

    public boolean useLocalMavenRepository() {
        return this.useLocalMavenRepository;
    }

    /**
     * If {@code true} Maven Central will be used for artifact resolution.
     */
    public T useMavenCentral(boolean useMavenCentral) {
        this.useMavenCentral = useMavenCentral;
        return (T) this;
    }

    public boolean useMavenCentral() {
        return this.useMavenCentral;
    }

    /**
     * If {@code true} the the local maven repository {@code $HOME/.m2/repository}
     * will be mounted into the server and agent containers.
     * Doesn't work with {@link Mode#LOCAL} or {@link Mode#REMOTE}.
     * Exclusive with {@link #mavenConfigurationPath}
     */
    public T useLocalMavenRepository(boolean useLocalMavenRepository) {
        this.useLocalMavenRepository = useLocalMavenRepository;
        return (T) this;
    }

    public boolean streamServerLogs() {
        return streamServerLogs;
    }

    /**
     * Stream the server logs to the console.
     */
    public T streamServerLogs(boolean streamServerLogs) {
        this.streamServerLogs = streamServerLogs;
        return (T) this;
    }

    public boolean streamAgentLogs() {
        return streamAgentLogs;
    }

    /**
     * Stream the agent logs to the console.
     */
    public T streamAgentLogs(boolean streamAgentLogs) {
        this.streamAgentLogs = streamAgentLogs;
        return (T) this;
    }

    public String pathToRunnerV1() {
        return pathToRunnerV1;
    }

    public String pathToRunnerV2() {
        return pathToRunnerV2;
    }

    /**
     * Path to the runner v1 JAR to use when {@link Mode#LOCAL}
     * is enabled.
     * <p/>
     * Typically points to the runner JAR file copied by Maven into
     * the target directory.
     */
    public T pathToRunnerV1(String pathToRunnerV1) {
        this.pathToRunnerV1 = pathToRunnerV1;
        return (T) this;
    }

    /**
     * Path to the runner v2 JAR to use when {@link Mode#LOCAL}
     * is enabled.
     * <p/>
     * Typically points to the runner JAR file copied by Maven into
     * the target directory.
     */
    public T pathToRunnerV2(String pathToRunnerV2) {
        this.pathToRunnerV2 = pathToRunnerV2;
        return (T) this;
    }

    public boolean startAgent() {
        return startAgent;
    }

    /**
     * Don't start the Agent if {@code false}. Default is {@code true}.
     */
    public T startAgent(boolean startAgent) {
        this.startAgent = startAgent;
        return (T) this;
    }

    /**
     * Docker image pull policy. If {@link #serverImage()} or {@link #agentImage()} are "latest"
     * {@link PullPolicy#alwaysPull()} is used by default.
     */
    public ImagePullPolicy pullPolicy() {
        return pullPolicy;
    }

    public T pullPolicy(ImagePullPolicy pullPolicy) {
        this.pullPolicy = pullPolicy;
        return (T) this;
    }

    public List<Startable> dependsOn() {
        return this.dependsOn;
    }

    public T dependsOn(Startable... dependsOn) {
        this.dependsOn = Arrays.asList(dependsOn);
        return (T) this;
    }

    public Path sharedContainerDir() {
        return sharedContainerDir;
    }

    /**
     * Mount the specified directory into the Server and the Agent containers.
     * Only for {@link Mode#DOCKER}.
     */
    public T sharedContainerDir(Path sharedContainerDir) {
        this.sharedContainerDir = sharedContainerDir;
        return (T) this;
    }

    /**
     * Returns the test host's address that can be used to connect
     * to the test host's services from inside a container.
     */
    public String hostAddressAccessibleByContainers() {
        switch (mode) {
            case REMOTE: {
                throw new IllegalStateException("Can't determine the test host's address when using the REMOTE mode.");
            }
            case LOCAL: {
                // when running in the LOCAL mode, the test host's resources can be accessed by using localhost
                return "localhost";
            }
            case DOCKER: {
                // when running in the DOCKER mode use the host name provided by Testcontainers
                return "host.testcontainers.internal";
            }
            default: {
                throw new IllegalStateException("Unsupported mode: " + mode);
            }
        }
    }

    public List<ContainerListener> containerListeners() {
        return this.containerListeners;
    }

    /**
     * Adds a {@link ContainerListener} that can be used to perform
     * additional actions before containers start.
     */
    public T containerListener(ContainerListener listener) {
        if (this.containerListeners == null) {
            this.containerListeners = new ArrayList<>();
        }
        this.containerListeners.add(listener);
        return (T) this;
    }

    /**
     * Utilities to work with T processes.
     */
    public Processes processes() {
        return new Processes(apiClient());
    }

    /**
     * Utilities to work with T secrets.
     */
    public Secrets secrets() {
        return new Secrets(apiClient());
    }

    /**
     * Utilities to work with T organizations.
     */
    public Organizations organizations() {
        return new Organizations(apiClient());
    }

    /**
     * Utilities to work with T organizations.
     */
    public Projects projects() {
        return new Projects(apiClient());
    }

    private ConcordEnvironment createEnvironment() {
        switch (mode) {
            case LOCAL: {
                return createLocalConcordEnvironment();
            }
            case DOCKER: {
                return createDockerConcordEnvironment();
            }
            case REMOTE: {
                return createRemoteConcordEnvironment();
            }
            default: {
                throw new IllegalArgumentException("Unsupported mode: " + mode);
            }
        }
    }

    @NotNull
    protected RemoteConcordEnvironment createRemoteConcordEnvironment() {
        return new RemoteConcordEnvironment(this);
    }

    @NotNull
    protected DockerConcordEnvironment createDockerConcordEnvironment() {
        return new DockerConcordEnvironment(this);
    }

    @NotNull
    protected LocalConcordEnvironment createLocalConcordEnvironment() {
        return new LocalConcordEnvironment(this);
    }

    public enum Mode {

        /**
         * The Server and the Agent are started directly in the current VM.
         * Requires the appropriate modules in the classpath.
         */
        LOCAL,

        /**
         * The Server and the Agent are started using Docker and pre-built images.
         */
        DOCKER,

        /**
         * Connect to a remote T instance.
         */
        REMOTE
    }
}
