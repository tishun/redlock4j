# Installation

=== "Maven"

    Add the following dependency to your `pom.xml`:

    ```xml
    <dependency>
        <groupId>org.codarama</groupId>
        <artifactId>redlock4j</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Choose one Redis client -->

    <!-- For Jedis: -->
    <dependency>
        <groupId>redis.clients</groupId>
        <artifactId>jedis</artifactId>
        <version>7.4.1</version>
    </dependency>

    <!-- Or for Lettuce: -->
    <dependency>
        <groupId>io.lettuce</groupId>
        <artifactId>lettuce-core</artifactId>
        <version>7.5.1.RELEASE</version>
    </dependency>
    ```

=== "Gradle"

    Add the following to your `build.gradle`:

    ```groovy
    dependencies {
        implementation 'org.codarama:redlock4j:1.0.0'

        // Choose one Redis client:
        // For Jedis:
        implementation 'redis.clients:jedis:7.4.1'

        // Or for Lettuce:
        implementation 'io.lettuce:lettuce-core:7.5.1.RELEASE'
    }
    ```

## Requirements

- **Java 8** or higher (tested against Java 8, 11, 17, and 21)
- **Redis** - the default keyspace-notification wait strategy relies on RESP3,
  so **Redis 6.0+** is recommended. Older servers are still usable via the
  polling fallback strategy. Native atomic CAS/CAD is used automatically on
  Redis 8.4+, with a Lua-script fallback on earlier versions.
- One of the supported Redis clients (RESP3 is required for the default
  keyspace strategy):
    - Jedis 5+ (RESP3)
    - Lettuce 6+ (RESP3)

## Verifying Installation

After adding the dependencies, verify the installation by connecting to a Redis
node and checking that the manager reports it as connected:

```java
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

public class RedlockTest {
    public static void main(String[] args) {
        RedlockConfiguration config = RedlockConfiguration.builder()
            .addRedisNode("localhost", 6379)
            .build();

        try (RedlockManager manager = RedlockManager.withJedis(config)) {
            System.out.println("Connected nodes: " + manager.getConnectedNodeCount());
            System.out.println("Redlock4j is ready!");
        }
    }
}
```

## Next Steps

- [Quick Start Guide](quick-start.md) - Learn the basics
