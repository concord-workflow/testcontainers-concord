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

Requires additional Concord modules to be present in classpath: 

```xml
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
```
