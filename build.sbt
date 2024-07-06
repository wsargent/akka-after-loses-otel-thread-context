import java.time.Instant

val otelVersion = "1.39.0"
val pekkoVersion = "1.0.3"

// Must run this with "sbt stage; cd target/universal/stage; ./bin/"
lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, JavaAgent)
  .settings(
    name := """akka-after-loses-thread-context""",
    organization := "com.example",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.13.14",
    javaAgents += "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % "2.4.0" % "dist;test",
    libraryDependencies ++= Seq(
      "io.opentelemetry" % "opentelemetry-api" % otelVersion,
      "io.opentelemetry" % "opentelemetry-sdk" % otelVersion,
      "io.opentelemetry" % "opentelemetry-exporter-logging" % otelVersion,
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.25.0-alpha",
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % otelVersion,
      "org.apache.pekko" %% "pekko-actor" % pekkoVersion,
      "ch.qos.logback" % "logback-classic" % "1.5.6"
    ),
    bashScriptExtraDefines += javaAgentOptions + loggingOptions + resourceOptions(name.value, version.value),
    scalacOptions ++= Seq(
      "-feature"
    )
  )

def javaAgentOptions: String =
  """
    |addJava "-Dotel.javaagent.debug=true"
    |addJava "-Dotel.javaagent.logging=application"
    |addJava "-Dotel.javaagent.experimental.thread-propagation-debugger.enabled=true"
    |addJava "-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=true"
    |""".stripMargin

def loggingOptions: String =
  """
    |# run jaeger-all-in-one to see traces, or set this to none
    |addJava "-Dotel.traces.exporter=logging"
    |addJava "-Dotel.metrics.exporter=none"
    |addJava "-Dotel.logs.exporter=none"
    |""".stripMargin

def resourceOptions(serviceName: String, serviceVersion: String): String =
  s"""
     |addJava "-Dotel.resource.attributes=service.name=${serviceName},service.version=${serviceVersion},artifact.build_time=${Instant.now.toString}"
     |# process.command_line is very large and potentially a security risk, do not include it
     |addJava "-Dotel.java.disabled.resource.providers=io.opentelemetry.instrumentation.resources.ProcessResourceProvider",
     |""".stripMargin
