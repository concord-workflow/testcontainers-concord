# Testcontainers Integration for Concord

Provides a JUnit 4 test rule to run Concord containers using [Testcontainers](https://www.testcontainers.org/).

Currently it requires the latest Concord version from `master`. 

## Usage

```java
public class MyTest {

    @Rule
    public static Concord concord = new Concord();

    public void test() {
        // ...
    }  
}
```

See [test cases](./src/test/java/ca/ibodrov/concord/testcontainers/RuleTest.java) for details.

## Local Mode

Local mode starts Concord Server and Concord Agent in the current JVM:

```java
public class MyTest {

    @Rule
    public static Concord concord = new Concord()
            .localMode(true);

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
