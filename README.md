# Akka / Pekko Loses OTel Context

This is a sample project to show how Opentelemetry Java instrumentation loses context when using the [after](https://pekko.apache.org/docs/pekko/current/futures.html#after).

## Running

```
sbt stage
cd target/universal/stage
./bin/akka-after-loses-thread-context
```
