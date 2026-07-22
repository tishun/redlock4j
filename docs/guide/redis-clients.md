# Redis Clients

Redlock4j supports both Jedis and Lettuce Redis clients through a clean driver
abstraction. You never construct pools or clients yourself: you describe your
Redis nodes with `RedisNodeConfiguration` / `RedlockConfiguration`, and pick the
driver once when you create the manager via `RedlockManager.withJedis(config)`
or `RedlockManager.withLettuce(config)`. The chosen driver is used for every
node, and the manager owns the underlying connections.

## Jedis

Jedis is a synchronous Redis client that's simple and straightforward.

### Setup

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>7.1.0</version>
</dependency>
```

### Basic Usage

```java
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("localhost", 6379)
    .addRedisNode("localhost", 6380)
    .addRedisNode("localhost", 6381)
    .build();

RedlockManager manager = RedlockManager.withJedis(config);
```

### Node Configuration

You don't pass a `JedisPool` or pool config object. Instead, tune each node with
`RedisNodeConfiguration`, which exposes the connection settings Redlock4j needs
(host, port, optional password and database, and connection/socket timeouts):

```java
import org.codarama.redlock4j.configuration.RedisNodeConfiguration;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

RedisNodeConfiguration node = RedisNodeConfiguration.builder()
    .host("localhost")
    .port(6379)
    .password("secret")        // optional, default null
    .database(0)               // optional, default 0
    .connectionTimeoutMs(2000) // default 2000
    .socketTimeoutMs(2000)     // default 2000
    .build();

RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode(node)
    // ... add the other nodes ...
    .build();

RedlockManager manager = RedlockManager.withJedis(config);
```

### Advantages

- Simple and easy to use
- Synchronous API is straightforward
- Lower memory footprint
- Good for simple use cases

### Disadvantages

- Blocking I/O
- Less efficient for high-throughput scenarios
- No built-in async support

## Lettuce

Lettuce is an advanced Redis client with async and reactive support.

### Setup

```xml
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>7.1.0.RELEASE</version>
</dependency>
```

### Basic Usage

Selecting Lettuce is a one-line change: the node configuration is identical, you
just create the manager with `withLettuce`:

```java
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("localhost", 6379)
    .addRedisNode("localhost", 6380)
    .addRedisNode("localhost", 6381)
    .build();

RedlockManager manager = RedlockManager.withLettuce(config);
```

### Node Configuration

As with Jedis, you don't build `RedisURI`, `ClientOptions`, or
`ClientResources` yourself. The same `RedisNodeConfiguration` fields drive the
Lettuce driver, including per-node timeouts, password, and database selection:

```java
import org.codarama.redlock4j.configuration.RedisNodeConfiguration;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

RedisNodeConfiguration node = RedisNodeConfiguration.builder()
    .host("localhost")
    .port(6379)
    .database(0)
    .connectionTimeoutMs(5000)
    .socketTimeoutMs(5000)
    .build();

RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode(node)
    // ... add the other nodes ...
    .build();

RedlockManager manager = RedlockManager.withLettuce(config);
```

### Advantages

- Non-blocking I/O with Netty
- Async and reactive API support
- Better performance for high-throughput
- Advanced features (clustering, sentinel)

### Disadvantages

- More complex API
- Higher memory footprint
- Steeper learning curve

## Choosing Between Jedis and Lettuce

### Use Jedis When:

- You need a simple, straightforward API
- Your application is primarily synchronous
- You have moderate throughput requirements
- You want minimal dependencies

### Use Lettuce When:

- You need high throughput
- You want async/reactive programming
- You're using Redis Cluster or Sentinel
- You need advanced features

## Connection Management

The `RedlockManager` owns the connections to every configured node, regardless
of which driver you selected. You do not open or close pools or clients
yourself. Because `RedlockManager` implements `AutoCloseable`, closing the
manager releases all underlying connections:

```java
try (RedlockManager manager = RedlockManager.withJedis(config)) {
    // Use the manager and the locks it creates
} // all connections closed automatically
```

If you manage the manager's lifecycle manually, call `manager.close()` when your
application shuts down.

## Next Steps

- [Best Practices](best-practices.md) - Follow recommended practices
- [Configuration](../api/configuration.md) - Detailed configuration options
