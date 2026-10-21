# Testcontainers Integration for Concord

Provides a JUnit test rules to run Concord containers using
[Testcontainers](https://www.testcontainers.org/).

## Usage

```xml
<dependency>
    <groupId>ca.ibodrov.concord</groupId>
    <artifactId>testcontainers-concord-junit4</artifactId>
    <version>...</version>
</dependency>
```

or

```xml
<dependency>
    <groupId>ca.ibodrov.concord</groupId>
    <artifactId>testcontainers-concord-junit5</artifactId>
    <version>...</version>
</dependency>
```

```java
public class MyTest {

    @Rule
    public ConcordRule concord = new ConcordRule();

    @Test
    public void test() {
        // ...
    }  
}
```

By default, the DB, the Server and the Agent are started using Docker containers.
See below for other options.

Server and Agent container image versions can be customized programmatically, or
overridden using environment variables (handy for testing against alternative
versions or custom images.

```
export TESTCONTAINERS_CONCORD_DB_IMAGE=myregistry/library/postgres:10
export TESTCONTAINERS_CONCORD_SERVER_IMAGE=myregistry/altowner/concord-server:custom-tag
export TESTCONTAINERS_CONCORD_AGENT_IMAGE=myregistry/myowner/concord-agent:custom-tag
$ ./mvnw clean install
```

CI pins the Server and Agent images to `2.25.1`, matching `concord.version` in
`pom.xml`. Update these workflow overrides when upgrading that dependency.

See [test cases](./src/test/java/ca/ibodrov/concord/testcontainers/RuleTest.java) for
details.

### Waiting for process status

`waitForStatus`, `expectStatus`, and `waitForChildStatus` use a five-minute
timeout by default. Pass a `Duration` to choose a different limit:

```java
process.expectStatus(Duration.ofMinutes(10), ProcessEntry.StatusEnum.FINISHED);
```

The limit covers HTTP requests, response-body reads, and polling delays.
Expiration cancels outstanding work and throws `IllegalStateException` without
changing the shared API client's settings. API errors can still fail earlier
under the existing retry limit.

### Database initialization and logs

In Docker and local modes, use `dbInitScriptPath` to execute a classpath SQL
resource before the Concord Server starts, and `streamDbLogs` to stream database
logs through SLF4J:

```java
Concord<?> concord = new Concord<>()
        .dbInitScriptPath("/db/init.sql")
        .streamDbLogs(true);
```

Place the script at `src/test/resources/db/init.sql`. It runs as part of
PostgreSQL's initialization of a fresh database, before Concord's schema
migrations. Database log streaming is disabled by default.

## Remote Mode

In this mode `testcontainers-concord` connect to a remove Concord instance.
You need to provide the API's base URL and the token:

```java
public class MyTest {

    @Rule
    public ConcordRule concord = new ConcordRule()
            .mode(Concord.Mode.REMOTE)
            .apiBaseUrl("http://localhost:8001")
            .apiToken("...");

    @Test
    public void test() {
        // ...
    }  
}
```

## Local Mode

Local mode starts Concord Server and Concord Agent in the current JVM:

```java
public class MyTest {

    @Rule
    public ConcordRule concord = new ConcordRule()
            .mode(Concord.Mode.LOCAL);

    @Test
    public void test() {
        // ...
    }  
}
``` 

This allows for easier debugging, i.e. it is possible to set up a breakpoint inside
the Server's, the Agent's or the plugin's code while running tests.

Requires additional Maven configuration: 

```xml
<project>
    <dependencies>
        <dependency>
            <groupId>com.walmartlabs.concord.server</groupId>
            <artifactId>concord-server-impl</artifactId>
            <version>${concord.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.walmartlabs.concord.server</groupId>
            <artifactId>concord-server</artifactId>
            <version>${concord.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.walmartlabs.concord</groupId>
            <artifactId>concord-agent</artifactId>
            <version>${concord.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- copy the runtime's JAR into the target directory -->    
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.1.2</version>
                <executions>
                    <execution>
                        <id>copy-runner-jar</id>
                        <phase>process-test-resources</phase>
                        <goals>
                            <goal>copy</goal>
                        </goals>
                        <configuration>
                            <overWriteIfNewer>true</overWriteIfNewer>
                            <artifactItems>
                                <artifactItem>
                                    <groupId>com.walmartlabs.concord.runtime.v1</groupId>
                                    <artifactId>concord-runtime-impl-v1</artifactId>
                                    <version>${concord.version}</version>
                                    <classifier>jar-with-dependencies</classifier>
                                    <destFileName>runner-v1.jar</destFileName>
                                </artifactItem>
                            </artifactItems>
                            <outputDirectory>${project.build.directory}</outputDirectory>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

Runtime V2 requires a different artifact:
```xml
<project>
    <build>
        <plugins>
            <!-- copy the runtime's JAR into the target directory -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.1.2</version>
                <executions>
                    <execution>
                        <id>copy-runner-jar</id>
                        <phase>process-test-resources</phase>
                        <goals>
                            <goal>copy</goal>
                        </goals>
                        <configuration>
                            <overWriteIfNewer>true</overWriteIfNewer>
                            <artifactItems>
                                <artifactItem>
                                    <groupId>com.walmartlabs.concord.runtime.v2</groupId>
                                    <artifactId>concord-runner-v2</artifactId>
                                    <version>${concord.version}</version>
                                    <classifier>jar-with-dependencies</classifier>
                                    <destFileName>runner-v2.jar</destFileName>
                                </artifactItem>
                            </artifactItems>
                            <outputDirectory>${project.build.directory}</outputDirectory>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```
