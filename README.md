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
